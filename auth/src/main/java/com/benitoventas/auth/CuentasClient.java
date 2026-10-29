package com.benitoventas.auth;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/cuentas")
@RegisterRestClient(configKey = "cuentas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CuentasClient {
    record CuentaPayload(String clienteId) {}
    record CuentaResponse(String id, String clienteId) {}

    @POST
    Response create(CuentaPayload cuenta, @HeaderParam("Authorization") String authorization);
}
