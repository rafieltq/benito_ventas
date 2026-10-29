package com.benitoventas.Ordenes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
import com.benitoventas.Cuentas.mongodb.panache.entity.Transaccion;
import com.benitoventas.Cuentas.mongodb.panache.entity.TipoTransaccion;
import com.benitoventas.Jobs.mongodb.panache.entity.EstadoJob;
import com.benitoventas.Jobs.mongodb.panache.entity.Job;
import com.benitoventas.Jobs.mongodb.panache.entity.TipoJob;
import com.benitoventas.Ordenes.client.ClientesClient;
import com.benitoventas.Ordenes.client.ProductosClient;
import com.benitoventas.Ordenes.dto.CrearOrdenRequest;
import com.benitoventas.Ordenes.mongodb.panache.entity.EstadoOrden;
import com.benitoventas.Ordenes.mongodb.panache.entity.Orden;
import com.benitoventas.Ordenes.mongodb.panache.entity.OrdenItem;
import com.mongodb.MongoException;

import java.util.List;

import io.quarkus.logging.Log;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@ApplicationScoped
public class OrdenesService {
    @Inject
    JsonWebToken jwt;

    @Inject
    @RestClient
    ClientesClient clientes;

    @Inject
    @RestClient
    ProductosClient productos;

    private record OrdenPreparada(Orden orden, ObjectId cuentaId, ObjectId clienteId) {}

    @RolesAllowed({"Admin", "cliente"})
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Orden crear(CrearOrdenRequest solicitud) {
        OrdenPreparada preparada = preparar(solicitud);
        return QuarkusTransaction.requiringNew().call(() -> guardar(preparada));
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void placeOrder(ObjectId ordenId) {
        if (ordenId == null) {
            throw new IllegalArgumentException("Debe proporcionar un ID de orden.");
        }
        for (int intento = 1; intento <= 3; intento++) {
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    Orden orden = Orden.findById(ordenId);
                    if (orden == null) {
                        throw new IllegalArgumentException("No se encontró la orden " + ordenId.toHexString() + ".");
                    }
                    EstadoOrden estadoInicial = orden.getEstado();
                    if (estadoInicial == EstadoOrden.PAGADA) {
                        Log.info("La orden " + ordenId.toHexString() + " ya ha sido pagada.");
                        return;
                    }
                    if (estadoInicial != EstadoOrden.PENDIENTE && estadoInicial != EstadoOrden.PAGO_PENDIENTE) {
                        throw new IllegalStateException("La orden no tiene un estado válido para procesar.");
                    }
                    if (!idValido(orden.getCuentaId())) {
                        throw new IllegalStateException("La orden no tiene una cuenta válida.");
                    }
                    BigDecimal total = orden.getTotal();
                    if (total == null || total.signum() < 0) {
                        throw new IllegalStateException("La orden no tiene un total válido.");
                    }
                    Cuenta cuenta = Cuenta.findById(new ObjectId(orden.getCuentaId()));
                    if (cuenta == null || cuenta.getBalance() == null) {
                        throw new IllegalStateException("La cuenta no existe o no tiene un balance válido.");
                    }

                    // El reclamo y su liberación pertenecen a la misma transacción.
                    long reclamadas = Orden.update("procesando", true)
                            .where("_id = ?1 and procesando != ?2 and estado = ?3", ordenId, true, estadoInicial);
                    if (reclamadas != 1) {
                        throw new IllegalStateException("La orden no está disponible para procesar.");
                    }

                    if (cuenta.getBalance().compareTo(total) >= 0) {
                        Transaccion transaccion = new Transaccion();
                        transaccion.setReferenciaId(orden.id);
                        transaccion.setMonto(total);
                        transaccion.setFecha(Instant.now());
                        transaccion.setTipo(TipoTransaccion.DEBITO);

                        List<Transaccion> nuevasTransacciones = cuenta.getTransacciones() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(cuenta.getTransacciones());
                        nuevasTransacciones.add(transaccion);
                        cuenta.setTransacciones(nuevasTransacciones);
                        cuenta.setBalance(cuenta.getBalance().subtract(total));
                        cuenta.update();
                        orden.setEstado(EstadoOrden.PAGADA);
                    } else {
                        orden.setEstado(EstadoOrden.PAGO_PENDIENTE);
                    }
                    orden.setProcesando(false);
                    orden.update();
                });
                return;
            } catch (RuntimeException e) {
                if (intento == 3 || !esConflictoTransitorio(e)) {
                    throw e;
                }
                Log.warn("Conflicto transitorio al procesar orden " + ordenId.toHexString()
                        + ". Reintento " + (intento + 1) + "/3.", e);
            }
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

    private OrdenPreparada preparar(CrearOrdenRequest solicitud) {
        validarSolicitud(solicitud);
        Set<String> grupos = jwt.getGroups();
        boolean administrador = grupos != null && grupos.contains("Admin");
        if (!administrador && (grupos == null || !grupos.contains("cliente"))) {
            throw new OrdenPreparacionException(Status.FORBIDDEN, "No tiene permisos para crear órdenes.");
        }

        String clienteId = determinarCliente(solicitud, administrador);
        ClientesClient.ClienteResponse cliente = consultarCliente(clienteId);
        ObjectId userId = new ObjectId(cliente.userId());
        if (!administrador && (!idValido(jwt.getSubject())
                || !userId.equals(new ObjectId(jwt.getSubject())))) {
            throw new OrdenPreparacionException(Status.FORBIDDEN, "El cliente no pertenece al usuario autenticado.");
        }

        Cuenta cuenta = Cuenta.find("clienteId", new ObjectId(clienteId)).firstResult();
        if (cuenta == null) {
            throw new OrdenPreparacionException(Status.CONFLICT, "El cliente no tiene una cuenta.");
        }

        Map<String, ProductosClient.ProductoResponse> catalogo = consultarProductos();
        ArrayList<OrdenItem> items = new ArrayList<>(solicitud.items().size());
        BigDecimal total = new BigDecimal("0.00");
        for (CrearOrdenRequest.Item solicitado : solicitud.items()) {
            ObjectId productoId = new ObjectId(solicitado.productoId());
            ProductosClient.ProductoResponse producto = catalogo.get(productoId.toHexString());
            if (producto == null) {
                throw new OrdenPreparacionException(Status.BAD_REQUEST,
                        "No se encontró el producto " + productoId.toHexString() + ".");
            }
            BigDecimal precio = precioAutorizado(producto);
            OrdenItem item = new OrdenItem();
            item.setProductoId(productoId);
            item.setCantidad(solicitado.cantidad());
            item.setNombre(producto.nombre());
            item.setDescripcion(producto.descripcion());
            item.setPrecio(precio);
            items.add(item);
            total = total.add(precio.multiply(BigDecimal.valueOf(solicitado.cantidad())));
        }

        Orden orden = new Orden();
        orden.setClienteId(clienteId);
        orden.setFecha(Instant.now());
        orden.setItems(items);
        orden.setCuentaId(cuenta.id.toHexString());
        orden.setTotal(total);
        orden.setEstado(EstadoOrden.PENDIENTE);
        return new OrdenPreparada(orden, cuenta.id, new ObjectId(clienteId));
    }

    private Orden guardar(OrdenPreparada preparada) {
        if (preparada == null || preparada.orden() == null
                || preparada.cuentaId() == null || preparada.clienteId() == null) {
            throw new IllegalArgumentException("Debe proporcionar una orden preparada con su cuenta y cliente.");
        }

        Orden orden = preparada.orden();
        if (orden.id != null) {
            throw new IllegalArgumentException("La orden preparada no debe tener un identificador asignado.");
        }

        Cuenta cuenta = Cuenta.findById(preparada.cuentaId());
        if (cuenta == null || !preparada.clienteId().equals(cuenta.getClienteId())) {
            throw new OrdenPreparacionException(Status.CONFLICT, "La cuenta del cliente ya no está disponible.");
        }

        orden.persist();

        Job job = new Job();
        job.setTipo(TipoJob.PLACE_ORDER);
        job.setContenido(orden.id.toHexString());
        job.setEstado(EstadoJob.PENDING);
        job.persist();

        return orden;
    }

    private void validarSolicitud(CrearOrdenRequest solicitud) {
        if (solicitud == null || solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new OrdenPreparacionException(Status.BAD_REQUEST, "Debe enviar una orden con al menos un ítem.");
        }
        for (CrearOrdenRequest.Item item : solicitud.items()) {
            if (item == null || !idValido(item.productoId())
                    || item.cantidad() == null || item.cantidad() <= 0) {
                throw new OrdenPreparacionException(Status.BAD_REQUEST,
                        "Cada ítem debe tener un producto válido y una cantidad entera positiva.");
            }
        }
    }

    private String determinarCliente(CrearOrdenRequest solicitud, boolean administrador) {
        if (administrador) {
            if (!idValido(solicitud.clienteId())) {
                throw new OrdenPreparacionException(Status.BAD_REQUEST, "Debe enviar un clienteId válido.");
            }
            return new ObjectId(solicitud.clienteId()).toHexString();
        }
        Object claim = jwt.getClaim("clienteId");
        if (!(claim instanceof String clienteId) || !idValido(clienteId)) {
            throw new OrdenPreparacionException(Status.FORBIDDEN, "La sesión no tiene un clienteId válido.");
        }
        ObjectId propietario = new ObjectId(clienteId);
        if (solicitud.clienteId() != null && (!idValido(solicitud.clienteId())
                || !propietario.equals(new ObjectId(solicitud.clienteId())))) {
            throw new OrdenPreparacionException(Status.FORBIDDEN,
                    "No puede crear una orden para otro cliente.");
        }
        return propietario.toHexString();
    }

    private ClientesClient.ClienteResponse consultarCliente(String clienteId) {
        String token = jwt.getRawToken();
        if (token == null || token.isBlank()) {
            throw new OrdenPreparacionException(Status.UNAUTHORIZED, "La sesión no es válida.");
        }
        try (Response respuesta = clientes.obtener(clienteId, "Bearer " + token)) {
            if (respuesta.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new OrdenPreparacionException(Status.NOT_FOUND, "No se encontró el cliente.");
            }
            if (respuesta.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                throw new OrdenPreparacionException(Status.UNAUTHORIZED, "Clientes rechazó la sesión.");
            }
            if (respuesta.getStatus() == Status.FORBIDDEN.getStatusCode()) {
                throw new OrdenPreparacionException(Status.FORBIDDEN, "No tiene permiso para acceder a este cliente.");
            }
            if (respuesta.getStatus() != Status.OK.getStatusCode()) {
                throw new OrdenPreparacionException(Status.SERVICE_UNAVAILABLE, "No se pudo consultar Clientes.");
            }
            if (!respuesta.hasEntity()) {
                throw new OrdenPreparacionException(Status.BAD_GATEWAY, "Clientes devolvió una respuesta inválida.");
            }
            ClientesClient.ClienteResponse cliente = respuesta.readEntity(ClientesClient.ClienteResponse.class);
            if (cliente == null || !idValido(cliente.id()) || !idValido(cliente.userId())
                    || !new ObjectId(clienteId).equals(new ObjectId(cliente.id()))) {
                throw new OrdenPreparacionException(Status.BAD_GATEWAY, "Clientes devolvió una referencia inválida.");
            }
            return cliente;
        } catch (ProcessingException | WebApplicationException e) {
            throw new OrdenPreparacionException(Status.SERVICE_UNAVAILABLE, "No se pudo consultar Clientes.", e);
        }
    }

    private Map<String, ProductosClient.ProductoResponse> consultarProductos() {
        try (Response respuesta = productos.listar()) {
            if (respuesta.getStatus() != Status.OK.getStatusCode()) {
                throw new OrdenPreparacionException(Status.SERVICE_UNAVAILABLE, "No se pudo consultar Productos.");
            }
            if (!respuesta.hasEntity()) {
                throw new OrdenPreparacionException(Status.BAD_GATEWAY, "Productos devolvió una respuesta inválida.");
            }
            ProductosClient.ListadoProductosResponse listado =
                    respuesta.readEntity(ProductosClient.ListadoProductosResponse.class);
            if (listado == null || listado.productos() == null) {
                throw new OrdenPreparacionException(Status.BAD_GATEWAY, "Productos devolvió un catálogo inválido.");
            }
            Map<String, ProductosClient.ProductoResponse> catalogo = new HashMap<>();
            for (ProductosClient.ProductoResponse producto : listado.productos()) {
                if (producto == null || !idValido(producto.id())
                        || catalogo.putIfAbsent(new ObjectId(producto.id()).toHexString(), producto) != null) {
                    throw new OrdenPreparacionException(Status.BAD_GATEWAY, "Productos devolvió un catálogo inválido.");
                }
            }
            return catalogo;
        } catch (ProcessingException | WebApplicationException e) {
            throw new OrdenPreparacionException(Status.SERVICE_UNAVAILABLE, "No se pudo consultar Productos.", e);
        }
    }

    private BigDecimal precioAutorizado(ProductosClient.ProductoResponse producto) {
        if (producto.precio() == null || producto.precio().signum() < 0) {
            throw new OrdenPreparacionException(Status.BAD_GATEWAY,
                    "Productos devolvió un precio inválido para " + producto.id() + ".");
        }
        try {
            return producto.precio().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new OrdenPreparacionException(Status.BAD_GATEWAY,
                    "El precio del producto " + producto.id() + " debe tener como máximo dos decimales.", e);
        }
    }

    private boolean idValido(String id) {
        return id != null && ObjectId.isValid(id);
    }
}
