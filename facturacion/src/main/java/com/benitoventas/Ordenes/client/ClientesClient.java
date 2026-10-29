package com.benitoventas.Ordenes.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/clientes")
@RegisterRestClient(configKey = "clientes")
@Produces(MediaType.APPLICATION_JSON)
public interface ClientesClient {
    record ClienteResponse(String id, String userId, String name, String email, String phone) {}

    @GET
    @Path("/{id}")
    Response obtener(@PathParam("id") String id, @HeaderParam("Authorization") String authorization);
}
