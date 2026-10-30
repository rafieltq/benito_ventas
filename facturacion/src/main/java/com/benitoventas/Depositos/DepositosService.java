package com.benitoventas.Depositos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
import com.benitoventas.Cuentas.mongodb.panache.entity.TipoTransaccion;
import com.benitoventas.Cuentas.mongodb.panache.entity.Transaccion;
import com.benitoventas.Depositos.dto.CrearDepositoRequest;
import com.benitoventas.Depositos.mongodb.panache.entity.Deposito;
import com.benitoventas.Depositos.mongodb.panache.entity.EstadoDeposito;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@Startup
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class DepositosService {
    @Inject
    JsonWebToken jwt;

    @PostConstruct
    void createIndexes() {
        Deposito.mongoCollection().createIndex(Indexes.ascending("referencia"),
                new IndexOptions().unique(true)
                        .partialFilterExpression(Filters.eq("estado", EstadoDeposito.COMPLETADO.name())));
    }

    @RolesAllowed({"Admin", "cliente"})
    public List<Deposito> listar() {
        if (jwt.getGroups().contains("Admin")) {
            return Deposito.listAll();
        }
        Cuenta cuenta = cuentaDelCliente();
        return cuenta == null ? List.of() : Deposito.list("cuentaId", cuenta.id.toHexString());
    }

    @RolesAllowed({"Admin", "cliente"})
    public Deposito obtener(String id) {
        Deposito deposito = buscar(idDeposito(id));
        if (!jwt.getGroups().contains("Admin")) {
            Cuenta cuenta = cuentaDelCliente();
            if (cuenta == null || !cuenta.id.toHexString().equals(deposito.cuentaId)) {
                throw new WebApplicationException("No tiene permiso para acceder a este depósito.", Status.FORBIDDEN);
            }
        }
        return deposito;
    }

    @RolesAllowed("cliente")
    public Deposito crear(CrearDepositoRequest solicitud) {
        if (solicitud == null) {
            throw new WebApplicationException("Debe enviar un depósito.", Status.BAD_REQUEST);
        }
        validarDatos(solicitud.referencia(), solicitud.monto(), Status.BAD_REQUEST);
        Cuenta cuenta = cuentaDelCliente();
        if (cuenta == null) {
            throw new WebApplicationException("El cliente no tiene una cuenta.", Status.CONFLICT);
        }
        Deposito deposito = new Deposito();
        deposito.cuentaId = cuenta.id.toHexString();
        deposito.referencia = solicitud.referencia().trim();
        deposito.monto = solicitud.monto().setScale(2, RoundingMode.UNNECESSARY);
        deposito.fecha = Instant.now().toString();
        deposito.estado = EstadoDeposito.PENDIENTE;
        deposito.persist();
        return deposito;
    }

    @RolesAllowed("Admin")
    public Deposito aprobar(String id) {
        return decidir(idDeposito(id), EstadoDeposito.COMPLETADO);
    }

    @RolesAllowed("Admin")
    public Deposito rechazar(String id) {
        return decidir(idDeposito(id), EstadoDeposito.FALLIDO);
    }

    private Deposito decidir(ObjectId id, EstadoDeposito destino) {
        for (int intento = 1; ; intento++) {
            try {
                return QuarkusTransaction.requiringNew().call(() -> resolver(id, destino));
            } catch (RuntimeException e) {
                if (intento >= 3 || !esConflictoTransitorio(e)) {
                    throw e;
                }
                Log.warn("Conflicto transitorio al decidir depósito " + id.toHexString()
                        + ". Reintento " + (intento + 1) + "/3.", e);
            }
        }
    }

    private Deposito resolver(ObjectId id, EstadoDeposito destino) {
        Deposito deposito = buscar(id);
        if (deposito.estado == destino) {
            return deposito;
        }
        if (deposito.estado != EstadoDeposito.PENDIENTE) {
            throw new WebApplicationException("El depósito ya tiene una decisión distinta.", Status.CONFLICT);
        }
        if (destino == EstadoDeposito.COMPLETADO) {
            validarDatos(deposito.referencia, deposito.monto, Status.CONFLICT);
            deposito.referencia = deposito.referencia.trim();
        }
        long modificados = Deposito.update("{$set: {estado: ?1, referencia: ?2}}", destino, deposito.referencia)
                .where("_id = ?1 and estado = ?2", id, EstadoDeposito.PENDIENTE);
        if (modificados != 1) {
            throw new WebApplicationException("El depósito ya no está pendiente.", Status.CONFLICT);
        }
        deposito.estado = destino;
        if (destino == EstadoDeposito.FALLIDO) {
            return deposito;
        }
        if (deposito.cuentaId == null || !ObjectId.isValid(deposito.cuentaId)) {
            throw new WebApplicationException("El depósito no tiene una cuenta válida.", Status.CONFLICT);
        }
        Cuenta cuenta = Cuenta.findById(new ObjectId(deposito.cuentaId));
        if (cuenta == null || cuenta.getBalance() == null) {
            throw new WebApplicationException("La cuenta no existe o no tiene un balance válido.", Status.CONFLICT);
        }
        Transaccion movimiento = new Transaccion();
        movimiento.setId(new ObjectId().toHexString());
        movimiento.setTipo(TipoTransaccion.CREDITO);
        movimiento.setMonto(deposito.monto);
        movimiento.setFecha(Instant.now());
        movimiento.setDescripcion("Depósito " + deposito.referencia);
        movimiento.setReferenciaId(deposito.id);

        List<Transaccion> movimientos = cuenta.getTransacciones() == null
                ? new ArrayList<>() : new ArrayList<>(cuenta.getTransacciones());
        movimientos.add(movimiento);
        cuenta.setTransacciones(movimientos);
        cuenta.setBalance(cuenta.getBalance().add(deposito.monto));
        cuenta.update();
        return deposito;
    }

    private Cuenta cuentaDelCliente() {
        Object claim = jwt.getClaim("clienteId");
        if (!(claim instanceof String id) || !ObjectId.isValid(id)) {
            throw new WebApplicationException("La sesión no tiene un clienteId válido.", Status.FORBIDDEN);
        }
        return Cuenta.find("clienteId", new ObjectId(id)).firstResult();
    }

    private ObjectId idDeposito(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new WebApplicationException("ID de depósito inválido.", Status.BAD_REQUEST);
        }
        return new ObjectId(id);
    }

    private Deposito buscar(ObjectId id) {
        Deposito deposito = Deposito.findById(id);
        if (deposito == null) {
            throw new WebApplicationException("Depósito no encontrado.", Status.NOT_FOUND);
        }
        return deposito;
    }

    private void validarDatos(String referencia, BigDecimal monto, Status estado) {
        if (referencia == null || referencia.trim().isEmpty()) {
            throw new WebApplicationException("Debe enviar una referencia bancaria.", estado);
        }
        if (monto == null || monto.signum() <= 0) {
            throw new WebApplicationException("El monto debe ser positivo.", estado);
        }
        try {
            monto.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new WebApplicationException("El monto debe tener como máximo dos decimales.", estado);
        }
    }

    private boolean esConflictoTransitorio(Throwable error) {
        while (error != null) {
            if (error instanceof MongoException mongo
                    && (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode() == 112)) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }
}
