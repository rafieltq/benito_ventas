package com.benitoventas.Ordenes;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
import com.benitoventas.Ordenes.dto.CrearOrdenRequest;
import com.benitoventas.Ordenes.mongodb.panache.entity.EstadoOrden;
import com.benitoventas.Ordenes.mongodb.panache.entity.Orden;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import jakarta.transaction.TransactionalException;

import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.inject.Inject;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/ordenes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrdenesResource {
    @Inject
    JsonWebToken jwt;

    @Inject
    OrdenesService ordenesService;

    private static final Logger LOG = Logger.getLogger(OrdenesResource.class);

    public record ListadoOrdenesResponse(String mensaje, List<Orden> ordenes) {}
    public record OrdenCreadaResponse(String mensaje, String ordenId, EstadoOrden estado) {}
    public record MensajeResponse(String mensaje) {}

    @GET
    @RolesAllowed({"Admin", "cliente"})
    public Response Listar() {
        boolean administrador = jwt.getGroups().contains("Admin");
        String clienteId = null;
        if (!administrador) {
            Object claim = jwt.getClaim("clienteId");
            if (!(claim instanceof String id) || !ObjectId.isValid(id)) {
                return mensaje(Response.Status.FORBIDDEN, "La sesión no tiene un clienteId válido.");
            }
            clienteId = new ObjectId(id).toHexString();
        }
        try {
            List<Orden> ordenes = administrador ? Orden.listAll() : Orden.list("clienteId", clienteId);
            String mensaje = ordenes.isEmpty()
                    ? "No se encontraron órdenes."
                    : "Listado de órdenes obtenido exitosamente.";
            return Response.ok(new ListadoOrdenesResponse(mensaje, ordenes)).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Response Obtener(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de orden inválido.");
        }
        try {
            Orden orden = Orden.findById(new ObjectId(id));
            if (orden == null) {
                return mensaje(Response.Status.NOT_FOUND, "Orden no encontrada.");
            }
            return Response.ok(orden).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @POST
    @RolesAllowed({"Admin", "cliente"})
    public Response Crear(CrearOrdenRequest solicitud) {
        try {
            Orden orden = ordenesService.crear(solicitud);
            String ordenId = orden.id.toHexString();
            return Response.created(URI.create("/ordenes/" + ordenId))
                    .entity(new OrdenCreadaResponse("Su orden ha sido colocada", ordenId, orden.getEstado()))
                    .build();
        } catch (OrdenPreparacionException e) {
            if (e.getEstadoHttp().getStatusCode() >= 500) {
                LOG.error("Error al preparar la orden.", e);
            }
            return mensaje(e.getEstadoHttp(), e.getMessage());
        } catch (MongoException e) {
            return errorMongo(e);
        } catch (QuarkusTransactionException | TransactionalException e) {
            LOG.error("No se pudo confirmar la creación de la orden.", e);
            return mensaje(Response.Status.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Actualizar(@PathParam("id") String id, Orden orden) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de orden inválido.");
        }
        if (orden == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar una orden.");
        }
        try {
            ObjectId ordenId = new ObjectId(id);
            Orden existente = Orden.findById(ordenId);
            if (existente == null) {
                return mensaje(Response.Status.NOT_FOUND, "Orden no encontrada.");
            }
            if ((orden.getClienteId() != null && !orden.getClienteId().equals(existente.getClienteId()))
                    || (orden.getCuentaId() != null && !orden.getCuentaId().equals(existente.getCuentaId()))) {
                return mensaje(Response.Status.BAD_REQUEST, "No se puede cambiar el cliente ni la cuenta de una orden.");
            }
            if (existente.getClienteId() == null || !ObjectId.isValid(existente.getClienteId())
                    || existente.getCuentaId() == null || !ObjectId.isValid(existente.getCuentaId())) {
                return mensaje(Response.Status.CONFLICT, "La orden no tiene un cliente o una cuenta válidos.");
            }
            Cuenta cuenta = Cuenta.findById(new ObjectId(existente.getCuentaId()));
            if (cuenta == null || !new ObjectId(existente.getClienteId()).equals(cuenta.getClienteId())) {
                return mensaje(Response.Status.CONFLICT, "La cuenta no pertenece al cliente de la orden.");
            }
            orden.id = ordenId;
            orden.setClienteId(existente.getClienteId());
            orden.setCuentaId(existente.getCuentaId());
            orden.update();
            return Response.ok(orden).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Eliminar(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de orden inválido.");
        }
        try {
            if (!Orden.deleteById(new ObjectId(id))) {
                return mensaje(Response.Status.NOT_FOUND, "Orden no encontrada.");
            }
            return Response.noContent().build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    private Response errorMongo(MongoException e) {
        if (e instanceof MongoWriteException writeException
                && writeException.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
            return mensaje(Response.Status.CONFLICT, "La orden ya existe.");
        }
        LOG.error("Error de MongoDB al operar sobre órdenes.", e);
        return mensaje(Response.Status.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
    }

    private Response mensaje(Response.Status estado, String mensaje) {
        return Response.status(estado).entity(new MensajeResponse(mensaje)).build();
    }
}
