package com.benitoventas.productos;

import com.benitoventas.productos.mongodb.panache.entity.Producto;
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

@Path("/productos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductosResource {
    @Inject
    JsonWebToken jwt;

    public record ListadoProductosResponse(
        String mensaje,
        List<Producto> productos
    ) {}

    @GET
    @PermitAll
    public Response Listar() {
      List<Producto> productos = Producto.listAll();
      String mensaje = "";
      if (productos.isEmpty()) {
        mensaje = "No se encontraron productos.";
      } else {
        mensaje = "Listado de productos obtenido exitosamente.";
      }
      return Response.ok(new ListadoProductosResponse(mensaje, productos)).build();
    }

    @POST
    @RolesAllowed("Admin")
    public Response Crear(Producto producto) {
        try {
          producto.persist();
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return Response.status(Response.Status.CONFLICT).entity("El producto ya existe.").build();
            }
            return
          Response.status(Response.Status.BAD_REQUEST).entity("Error al crear el producto.").build();
        }
        return Response.ok(producto).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Actualizar(String id, Producto producto) {
        // Catch errors for update
        try {
            Producto productoExistente = Producto.findById(new ObjectId(id));
            if (productoExistente == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            producto.update();
        } catch (MongoWriteException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Error al actualizar el producto.").build();
        } catch (MongoException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error interno del servidor.").build();
        }

        return Response.ok(producto).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("Admin")
    public Response Eliminar(String id) {
        Producto producto = Producto.findById(new ObjectId(id));
        if (producto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        producto.delete();
        return Response.noContent().build();
    }
}
