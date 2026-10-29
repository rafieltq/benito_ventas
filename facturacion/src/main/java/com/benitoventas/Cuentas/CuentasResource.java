package com.benitoventas.Cuentas;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CuentasResource {
    @Inject
    JsonWebToken jwt;

    private static final Logger LOG = Logger.getLogger(CuentasResource.class);

    public record ListadoCuentasResponse(String mensaje, List<Cuenta> cuentas) {}
    public record MensajeResponse(String mensaje) {}

    @PostConstruct
    void createIndexes() {
        Cuenta.mongoCollection().createIndex(Indexes.ascending("clienteId"), new IndexOptions().unique(true));
    }

    @GET
    @RolesAllowed({"Admin", "auth-service"})
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
    @RolesAllowed({"Admin", "auth-service", "cliente"})
    public Response Obtener(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return mensaje(Response.Status.BAD_REQUEST, "ID de cuenta inválido.");
        }
        try {
            Cuenta cuenta = Cuenta.findById(new ObjectId(id));
            if (cuenta == null) {
                return mensaje(Response.Status.NOT_FOUND, "Cuenta no encontrada.");
            }
            if (!canAccess(cuenta.getClienteId())) {
                return mensaje(Response.Status.FORBIDDEN, "No tiene permiso para acceder a esta cuenta.");
            }
            return Response.ok(cuenta).build();
        } catch (MongoException e) {
            return errorMongo(e);
        }
    }

    @POST
    @RolesAllowed({"Admin", "auth-service"})
    public Response Crear(Cuenta cuenta) {
        if (cuenta == null || cuenta.getClienteId() == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar una cuenta con un clienteId válido.");
        }
        ObjectId clienteId = cuenta.getClienteId();
        if (!canAccess(clienteId)) {
            return mensaje(Response.Status.FORBIDDEN, "No tiene permiso para crear esta cuenta.");
        }
        try {
            Cuenta existente = Cuenta.find("clienteId", clienteId).firstResult();
            if (existente != null) {
                return Response.ok(existente).build();
            }
            cuenta = new Cuenta();
            cuenta.setClienteId(clienteId);
            try {
                cuenta.persist();
            } catch (MongoWriteException e) {
                if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                    throw e;
                }
                existente = Cuenta.find("clienteId", clienteId).firstResult();
                if (existente == null) {
                    throw e;
                }
                return Response.ok(existente).build();
            }
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
            if (cuenta.getClienteId() != null && !cuenta.getClienteId().equals(existente.getClienteId())) {
                return mensaje(Response.Status.BAD_REQUEST, "No se puede cambiar el cliente de una cuenta.");
            }
            cuenta.id = cuentaId;
            cuenta.setClienteId(existente.getClienteId());
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

    private boolean canAccess(ObjectId clienteId) {
        Object claim = jwt.getClaim("clienteId");
        return jwt.getGroups().contains("Admin")
                || (clienteId != null && claim instanceof String id && ObjectId.isValid(id)
                    && clienteId.equals(new ObjectId(id)));
    }
}
