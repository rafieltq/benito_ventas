package com.benitoventas.cuentas;

import com.benitoventas.Cuentas.mongodb.panache.entity.Cuenta;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import java.util.List;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.MongoException;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Path("/cuentas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CuentasResource {
    @Inject
    JsonWebToken jwt;

    public record ListadoCuentasResponse( String mensaje, List<Cuenta> cuentas) {}

    @GET
    @PermitAll
    public Response Listar() {
      List<Cuenta> cuentas = cuenta.listAll();
      String mensaje = "";
      if (cuentas.isEmpty()) {
        mensaje = "No se encontraron cuentas.";
      } else {
        mensaje = "Listado de cuentas obtenido exitosamente.";
      }
      return Response.ok(new ListadoCuentasResponse(mensaje, cuentas)).build();
    }

    @POST
    @RolesAllowed({"Admin", "auth-service"})
    public Response Crear(Cuenta cuenta) {
        try {
          cuenta.persist();
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return Response.status(Response.Status.CONFLICT).entity("El cuenta ya existe.").build();
            }
            return
          Response.status(Response.Status.BAD_REQUEST).entity("Error al crear el cuenta.").build();
        }
        return Response.ok(cuenta).build();
    }

    // @PUT
    // @Path("/{id}")
    // @RolesAllowed("Admin")
    // public Response Actualizar(String id, Cuenta cuenta) {
    //     // Catch errors for update
    //     try {
    //         Cuenta cuentaExistente = Producto.findById(new ObjectId(id));
    //         if (cuentaExistente == null) {
    //             return Response.status(Response.Status.NOT_FOUND).build();
    //         }
    //         cuenta.update();
    //     } catch (MongoWriteException e) {
    //         return Response.status(Response.Status.BAD_REQUEST).entity("Error al actualizar el cuenta.").build();
    //     } catch (MongoException e) {
    //         return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error interno del servidor.").build();
    //     }
    //
    //     return Response.ok(cuenta).build();
    // }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Eliminar(String id) {
        Cuenta cuenta = Cuenta.findById(new ObjectId(id));
        if (cuenta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        cuenta.delete();
        return Response.noContent().build();
    }
}
