package com.benitoventas.Depositos;

import java.net.URI;
import java.util.List;

import org.jboss.logging.Logger;

import com.benitoventas.Depositos.dto.CrearDepositoRequest;
import com.benitoventas.Depositos.mongodb.panache.entity.Deposito;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/depositos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DepositosResource {
    private static final Logger LOG = Logger.getLogger(DepositosResource.class);

    @Inject
    DepositosService depositosService;

    public record ListadoDepositosResponse(String mensaje, List<Deposito> depositos) {}
    public record MensajeResponse(String mensaje) {}

    @GET
    @RolesAllowed({"Admin", "cliente"})
    public Response Listar() {
        try {
            List<Deposito> depositos = depositosService.listar();
            String mensaje = depositos.isEmpty()
                    ? "No se encontraron depósitos."
                    : "Listado de depósitos obtenido exitosamente.";
            return Response.ok(new ListadoDepositosResponse(mensaje, depositos)).build();
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"Admin", "cliente"})
    public Response Obtener(@PathParam("id") String id) {
        try {
            return Response.ok(depositosService.obtener(id)).build();
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @POST
    @RolesAllowed("cliente")
    public Response Crear(CrearDepositoRequest solicitud) {
        try {
            Deposito deposito = depositosService.crear(solicitud);
            return Response.created(URI.create("/depositos/" + deposito.id)).entity(deposito).build();
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @POST
    @Path("/{id}/aprobar")
    @RolesAllowed("Admin")
    public Response Aprobar(@PathParam("id") String id) {
        try {
            return Response.ok(depositosService.aprobar(id)).build();
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @POST
    @Path("/{id}/rechazar")
    @RolesAllowed("Admin")
    public Response Rechazar(@PathParam("id") String id) {
        try {
            return Response.ok(depositosService.rechazar(id)).build();
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private Response error(RuntimeException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof WebApplicationException web) {
                return Response.status(web.getResponse().getStatus())
                        .entity(new MensajeResponse(web.getMessage())).build();
            }
            if (causa instanceof MongoException mongo && (mongo.getCode() == 11000
                    || (mongo instanceof MongoWriteException escritura
                        && escritura.getError().getCategory() == ErrorCategory.DUPLICATE_KEY))) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new MensajeResponse("La referencia bancaria ya respalda un depósito aprobado."))
                        .build();
            }
        }
        LOG.error("Error al operar sobre depósitos.", error);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new MensajeResponse("Error interno del servidor.")).build();
    }
}
