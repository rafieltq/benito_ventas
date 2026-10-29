package com.benitoventas.cuentas;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
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

@Path("/cuentas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CuentasResource {
    private static final Logger LOG = Logger.getLogger(CuentasResource.class);

    public record ListadoCuentasResponse(String mensaje, List<Cuenta> cuentas) {}
    public record MensajeResponse(String mensaje) {}

    @GET
    @PermitAll
    public Response Listar() {
        try {
            List<Cuenta> cuentas = Cuenta.listAll();
            String mensaje = cuentas.isEmpty()
                    ? "No se encontraron cuentas."
                    : "Listado de cuentas obtenido exitosamente.";
            return Response.ok(new ListadoCuentasResponse(mensaje, cuentas)).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Response Obtener(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de cuenta inválido.");
        }
        try {
            Cuenta cuenta = Cuenta.findById(new ObjectId(id));
            if (cuenta == null) {
                return mensaje(Response.Status.NOT_FOUND, "Cuenta no encontrada.");
            }
            return Response.ok(cuenta).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @POST
    @RolesAllowed({"Admin", "auth-service"})
    public Response Crear(Cuenta cuenta) {
        if (cuenta == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar una cuenta.");
        }
        try {
            cuenta.persist();
            return Response.created(URI.create("/cuentas/" + cuenta.id)).entity(cuenta).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Actualizar(@PathParam("id") String id, Cuenta cuenta) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de cuenta inválido.");
        }
        if (cuenta == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar una cuenta.");
        }
        try {
            ObjectId cuentaId = new ObjectId(id);
            Cuenta existente = Cuenta.findById(cuentaId);
            if (existente == null) {
                return mensaje(Response.Status.NOT_FOUND, "Cuenta no encontrada.");
            }
            cuenta.id = cuentaId;
            cuenta.update();
            return Response.ok(cuenta).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Eliminar(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de cuenta inválido.");
        }
        try {
            if (!Cuenta.deleteById(new ObjectId(id))) {
                return mensaje(Response.Status.NOT_FOUND, "Cuenta no encontrada.");
            }
            return Response.noContent().build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    private Response errorMongo(MongoException e) {
        if (e instanceof MongoWriteException writeException
                && writeException.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
            return mensaje(Response.Status.CONFLICT, "La cuenta ya existe.");
        }
        LOG.error("Error de MongoDB al operar sobre cuentas.", e);
        return mensaje(Response.Status.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
    }

    private Response mensaje(Response.Status estado, String mensaje) {
        return Response.status(estado).entity(new MensajeResponse(mensaje)).build();
    }
}
