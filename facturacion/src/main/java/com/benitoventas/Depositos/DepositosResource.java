package com.benitoventas.Depositos;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import com.benitoventas.Depositos.mongodb.panache.entity.Deposito;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;

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

@Path("/depositos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DepositosResource {
    private static final Logger LOG = Logger.getLogger(DepositosResource.class);

    public record ListadoDepositosResponse(String mensaje, List<Deposito> depositos) {}
    public record MensajeResponse(String mensaje) {}

    @GET
    @PermitAll
    public Response Listar() {
        try {
            List<Deposito> depositos = Deposito.listAll();
            String mensaje = depositos.isEmpty()
                    ? "No se encontraron depósitos."
                    : "Listado de depósitos obtenido exitosamente.";
            return Response.ok(new ListadoDepositosResponse(mensaje, depositos)).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Response Obtener(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de depósito inválido.");
        }
        try {
            Deposito deposito = Deposito.findById(new ObjectId(id));
            if (deposito == null) {
                return mensaje(Response.Status.NOT_FOUND, "Depósito no encontrado.");
            }
            return Response.ok(deposito).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @POST
    @RolesAllowed("Admin")
    public Response Crear(Deposito deposito) {
        if (deposito == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar un depósito.");
        }
        try {
            deposito.persist();
            return Response.created(URI.create("/depositos/" + deposito.id)).entity(deposito).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Actualizar(@PathParam("id") String id, Deposito deposito) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de depósito inválido.");
        }
        if (deposito == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar un depósito.");
        }
        try {
            ObjectId depositoId = new ObjectId(id);
            Deposito existente = Deposito.findById(depositoId);
            if (existente == null) {
                return mensaje(Response.Status.NOT_FOUND, "Depósito no encontrado.");
            }
            deposito.id = depositoId;
            deposito.update();
            return Response.ok(deposito).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Eliminar(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de depósito inválido.");
        }
        try {
            if (!Deposito.deleteById(new ObjectId(id))) {
                return mensaje(Response.Status.NOT_FOUND, "Depósito no encontrado.");
            }
            return Response.noContent().build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    private Response errorMongo(MongoException e) {
        if (e instanceof MongoWriteException writeException
                && writeException.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
            return mensaje(Response.Status.CONFLICT, "El depósito ya existe.");
        }
        LOG.error("Error de MongoDB al operar sobre depósitos.", e);
        return mensaje(Response.Status.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
    }

    private Response mensaje(Response.Status estado, String mensaje) {
        return Response.status(estado).entity(new MensajeResponse(mensaje)).build();
    }
}
