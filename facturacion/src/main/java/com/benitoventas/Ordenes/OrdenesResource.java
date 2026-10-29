package com.benitoventas.Ordenes;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import com.benitoventas.Ordenes.mongodb.panache.entity.Orden;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import jakarta.transaction.Transactional;

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

    private static final Logger LOG = Logger.getLogger(OrdenesResource.class);

    public record ListadoOrdenesResponse(String mensaje, List<Orden> ordenes) {}
    public record MensajeResponse(String mensaje) {}

    @GET
    @RolesAllowed({"Admin", "Clientes"})
    public Response Listar() {
        // validate the group of the token
        if (!jwt.getGroups().contains("Admin") && !jwt.getGroups().contains("Clientes")) {
            return mensaje(Response.Status.FORBIDDEN, "No tiene permisos para listar órdenes.");
        }

        // if the JWT is admin fetch all the orders 
        if (jwt.getGroups().contains("Admin")) {
            try {
                List<Orden> ordenes = Orden.listAll();
                String mensaje = ordenes.isEmpty()
                        ? "No se encontraron órdenes."
                        : "Listado de órdenes obtenido exitosamente.";
                return Response.ok(new ListadoOrdenesResponse(mensaje, ordenes)).build();
            } catch (MongoException e) {
                return errorMongo(e);
            }
        }

        if (jwt.getGroups().contains("Clientes")) {
            String userId = jwt.getClaim("sub");
            try {
                List<Orden> ordenes = Orden.list("userId", userId);
                String mensaje = ordenes.isEmpty()
                        ? "No se encontraron órdenes para el usuario."
                        : "Listado de órdenes del usuario obtenido exitosamente.";
                return Response.ok(new ListadoOrdenesResponse(mensaje, ordenes)).build();
            } catch (MongoException e) {
                return errorMongo(e);
            }
        }
        try {
            List<Orden> ordenes = Orden.listAll();
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
    @RolesAllowed({"Admin", "Clientes"})
    @Transactional
    public Response Crear(Orden orden) {
        // validate all the data required is in the payload
        // save the order and the order items in the database and save the Task for this order to be processed by kafka later
        if (orden == null) {
            return mensaje(Response.Status.BAD_REQUEST, "Debe enviar una orden.");
        }

        try {
            orden.persist();
            return Response.created(URI.create("/ordenes/" + orden.id)).entity(orden).build();
        } catch (MongoException e) {
            return errorMongo(e);
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
            orden.id = ordenId;
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
