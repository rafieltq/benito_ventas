package com.benitoventas.clientes;

import java.util.List;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.benitoventas.clientes.mongodb.panache.entity.Cliente;
import com.mongodb.ErrorCategory;
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
import jakarta.ws.rs.core.Response.Status;

@Path("/clientes")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClientesResource {
    @Inject
    JsonWebToken jwt;

    public record ClientePayload(String userId, String name, String email, String phone) {}
    public record ClienteResponse(String id, String userId, String name, String email, String phone) {}
    public record ListadoClientesResponse(String message, List<ClienteResponse> clientes) {}
    public record MessageResponse(String message) {}

    @PostConstruct
    void createIndexes() {
        Cliente.mongoCollection().createIndex(Indexes.ascending("userId"), new IndexOptions().unique(true));
    }

    @GET
    @RolesAllowed("Admin")
    public Response list() {
        List<Cliente> clientes = Cliente.listAll();
        String message = clientes.isEmpty()
                ? "No se encontraron clientes."
                : "Listado de clientes obtenido exitosamente.";
        return Response.ok(new ListadoClientesResponse(message,
                clientes.stream().map(this::response).toList())).build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"Admin", "cliente"})
    public Response get(@PathParam("id") String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return error(Status.BAD_REQUEST, "El identificador del cliente no es válido.");
        }
        Cliente cliente = Cliente.findById(new ObjectId(id));
        if (cliente == null) {
            return error(Status.NOT_FOUND, "No se encontró el cliente.");
        }
        if (!canAccess(cliente.userId)) {
            return error(Status.FORBIDDEN, "No tienes permiso para acceder a este cliente.");
        }
        return Response.ok(response(cliente)).build();
    }

    @GET
    @Path("/user/{userId}")
    @RolesAllowed({"Admin", "cliente", "auth-service"})
    public Response getByUser(@PathParam("userId") String userId) {
        if (!ObjectId.isValid(userId)) {
            return error(Status.BAD_REQUEST, "El identificador del usuario no es válido.");
        }
        ObjectId ownerId = new ObjectId(userId);
        if (!canAccess(ownerId)) {
            return error(Status.FORBIDDEN, "No tienes permiso para acceder a este cliente.");
        }
        Cliente cliente = Cliente.find("userId", ownerId).firstResult();
        if (cliente == null) {
            return error(Status.NOT_FOUND, "No se encontró el cliente.");
        }
        return Response.ok(response(cliente)).build();
    }

    @POST
    @RolesAllowed({"Admin", "auth-service"})
    public Response create(ClientePayload payload) {
        if (!validProfile(payload) || payload.userId() == null || !ObjectId.isValid(payload.userId())) {
            return error(Status.BAD_REQUEST, "Completa los datos del cliente y un userId válido.");
        }
        ObjectId userId = new ObjectId(payload.userId());
        if (!canAccess(userId)) {
            return error(Status.FORBIDDEN, "No tienes permiso para crear este cliente.");
        }
        Cliente existing = Cliente.find("userId", userId).firstResult();
        if (existing != null) {
            return Response.ok(response(existing)).build();
        }
        Cliente cliente = new Cliente();
        cliente.userId = userId;
        cliente.name = payload.name().trim();
        cliente.email = payload.email().trim();
        cliente.phone = payload.phone().trim();
        try {
            cliente.persist();
        } catch (MongoWriteException exception) {
            if (exception.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw exception;
            }
            // Another request may have completed the same signup first.
            existing = Cliente.find("userId", userId).firstResult();
            if (existing == null) {
                throw exception;
            }
            return Response.ok(response(existing)).build();
        }
        return Response.status(Status.CREATED).entity(response(cliente)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"Admin", "cliente"})
    public Response update(@PathParam("id") String id, ClientePayload payload) {
        if (!ObjectId.isValid(id) || !validProfile(payload)) {
            return error(Status.BAD_REQUEST, "El identificador o los datos del cliente no son válidos.");
        }
        Cliente cliente = Cliente.findById(new ObjectId(id));
        if (cliente == null) {
            return error(Status.NOT_FOUND, "No se encontró el cliente.");
        }
        if (!canAccess(cliente.userId)) {
            return error(Status.FORBIDDEN, "No tienes permiso para modificar este cliente.");
        }
        if (payload.userId() != null && !cliente.userId.toHexString().equals(payload.userId())) {
            return error(Status.BAD_REQUEST, "No se puede cambiar el usuario de un cliente.");
        }
        cliente.name = payload.name().trim();
        cliente.email = payload.email().trim();
        cliente.phone = payload.phone().trim();
        cliente.update();
        return Response.ok(response(cliente)).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response delete(@PathParam("id") String id) {
        if (!ObjectId.isValid(id)) {
            return error(Status.BAD_REQUEST, "El identificador del cliente no es válido.");
        }
        Cliente cliente = Cliente.findById(new ObjectId(id));
        if (cliente == null) {
            return error(Status.NOT_FOUND, "No se encontró el cliente.");
        }
        cliente.delete();
        return Response.noContent().build();
    }

    private boolean canAccess(ObjectId userId) {
        return jwt.getGroups().contains("Admin")
                || (userId != null && userId.toHexString().equals(jwt.getSubject()));
    }

    private boolean validProfile(ClientePayload payload) {
        return payload != null && payload.name() != null && !payload.name().isBlank()
                && payload.email() != null && !payload.email().isBlank()
                && payload.phone() != null && !payload.phone().isBlank();
    }

    private ClienteResponse response(Cliente cliente) {
        return new ClienteResponse(cliente.id.toHexString(), cliente.userId.toHexString(),
                cliente.name, cliente.email, cliente.phone);
    }

    private Response error(Status status, String message) {
        return Response.status(status).entity(new MessageResponse(message)).build();
    }
}
