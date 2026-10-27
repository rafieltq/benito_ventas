package com.benitoventas.productos;

import com.benitoventas.productos.mongodb.panache.entity.Producto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.bson.types.ObjectId;

@Path("/productos")
@Produces(MediaType.APPLICATION_JSON)
public class ProductosResource {

    public record ListadoProductosResponse(
        String mensaje,
        List<Producto> productos
    ) {}

    @GET
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
    public Response Crear(Producto producto) {
        producto.persist();
        return Response.ok(producto).build();
    }

    @PUT
    @Path("/{id}")
    public Response Actualizar(String id, Producto producto) {
        producto.update();
        return Response.ok(producto).build();
    }

    @DELETE
    @Path("/{id}")
    public Response Eliminar(String id) {
        Producto producto = Producto.findById(new ObjectId(id));
        if (producto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        producto.delete();
        return Response.noContent().build();
    }
}
