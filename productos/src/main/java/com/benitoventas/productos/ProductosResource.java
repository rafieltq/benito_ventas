package com.benitoventas.productos;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/productos")
@Produces(MediaType.APPLICATION_JSON)
public class ProductosResource {

    public record Producto(String nombre, double precio) {}
    public record ListadoProductosResponse(String mensaje, Producto[] productos) {}

    @GET
    public ListadoProductosResponse Listar() {
      return new ListadoProductosResponse(
          "Listado de productos obtenido exitosamente",
          new Producto[] {
            new Producto("Producto 1", 10.0),
            new Producto("Producto 2", 20.0),
            new Producto("Producto 3", 30.0)
          }
      );
    }
}
