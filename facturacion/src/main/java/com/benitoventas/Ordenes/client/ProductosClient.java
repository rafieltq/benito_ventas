package com.benitoventas.Ordenes.client;

import java.math.BigDecimal;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/productos")
@RegisterRestClient(configKey = "productos")
@Produces(MediaType.APPLICATION_JSON)
public interface ProductosClient {
    record ProductoResponse(String id, String nombre, String descripcion, BigDecimal precio) {}
    record ListadoProductosResponse(String mensaje, List<ProductoResponse> productos) {}

    @GET
    Response listar();
}
