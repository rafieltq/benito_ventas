package com.benitoventas.auth;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/clientes")
@RegisterRestClient(configKey = "clientes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ClientesClient {
    record ClientePayload(String userId, String name, String email, String phone) {}

    @POST
    Response create(ClientePayload cliente, @HeaderParam("Authorization") String authorization);
}
