package com.benitoventas.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.Authenticated;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    JsonWebToken jwt; 

    public record SignupPayload (
        String name,
        String email,
        String phone,
        String password,
        String confirmPassword
    ) {}

    public record LoginPayload (
        String email,
        String password
    ) {}

    public record authResponse (
        String message,
        String token
    ) {}

    public record meResponse (
        String name,
        String email,
        String phone
    ) {}

    @GET
    @Path("/me")
    @Authenticated // allow any role as long is authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response me(@Context SecurityContext ctx) {
        // Implement me endpoint
        // 1. Get the authenticated user from the security context
        // 2. Return the user information as JSON
        // 3. Return an error message if the user is not authenticated
        return Response.ok(501).build();
    }

    @POST
    @Path("/clientes/signup")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response signup(@Context SecurityContext ctx, SignupPayload signupUser) {
        // Implement signup
        // 1. Validate the input (name, email, phone, password, confirmPassword)
        // 2. Check if the username or email already exists in the database
        // 3. Hash the password and store the new user in the database
        // 4. Return a generated token message or an error message if something goes wrong
        return Response.ok(501).build();
    }

    @POST
    @Path("/clientes/login")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginPayload loginUser) {
        // Implement login
        // 1. Validate the input (email, password)
        // 2. Check if the username exists in the database
        // 3. Verify the password against the stored hash
        // 4. Return a generated token message or an error message if something goes wrong
        return Response.ok(501).build();
    }

    @POST
    @Path("/admin/signup")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response signup(@Context SecurityContext ctx, SignupPayload signupUser) {
        // Implement signup
        // 1. Validate the input (name, email, phone, password, confirmPassword)
        // 2. Check if the username or email already exists in the database
        // 3. Hash the password and store the new user in the database
        // 4. Return a generated token message or an error message if something goes wrong
        return Response.ok(501).build();
    }

    @POST
    @Path("/admin/login")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginPayload loginUser) {
        // Implement login
        // 1. Validate the input (email, password)
        // 2. Check if the username exists in the database
        // 3. Verify the password against the stored hash
        // 4. Return a generated token message or an error message if something goes wrong
        return Response.ok(501).build();
    }
}
