package com.benitoventas.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import io.quarkus.elytron.security.common.BcryptUtil;

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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import com.benitoventas.users.mongodb.panache.entity.User;
import com.benitoventas.credentials.mongodb.panache.entity.Credential;
import com.benitoventas.roles.mongodb.panache.entity.Role;

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
    public Response clientSignup(@Context SecurityContext ctx, SignupPayload signupUser) {
        // Implement signup
        // 1. Validate the input (name, email, phone, password, confirmPassword)
        if (signupUser.name() == null || signupUser.name().isEmpty() || signupUser.email() == null || signupUser.email().isEmpty() ||  signupUser.phone() == null || signupUser.phone().isEmpty() || signupUser.password() == null || signupUser.password().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new authResponse("Por favor rellena todos los campos", null))
                    .build();
        }
        // 1.1 validate that password and confirmPassword are the same
        if (!signupUser.password().equals(signupUser.confirmPassword())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new authResponse("Las contraseñas deben de ser iguales", null))
                    .build();
        }
        // 2. Check if the email already exists in the database with isActive = true
        User existingUser = User.find("email", signupUser.email()).firstResult();
        if (existingUser != null && existingUser.isActive) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new authResponse("El correo electrónico ya está registrado", null))
                    .build();
        }
        // 3. Create a new user
        User user = new User();
        user.name = signupUser.name();
        user.email = signupUser.email();
        user.phone = signupUser.phone();
        // 3.1 find the role 
        Role role = Role.find("name", "cliente").firstResult();
        if (role == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new authResponse("No se pudo encontrar el rol de cliente", null))
                    .build();
        }
        user.roleIds.add(role.id);
        user.persist();
        // 4. Hash the password and store the new user in the database
        String hashedPassword = BcryptUtil.bcryptHash(signupUser.password());
        // 5. Save hased password into credentials
        Credential credential = new Credential();
        credential.userId = user.id;
        credential.email = signupUser.email();
        credential.passwordHash = hashedPassword;
        credential.persist();
        return Response.ok().build();
    }

    @POST
    @Path("/clientes/login")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response clientLogin(LoginPayload loginUser) {
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
    public Response adminSignup(@Context SecurityContext ctx, SignupPayload signupUser) {
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
    public Response adminLogin(LoginPayload loginUser) {
        // Implement login
        // 1. Validate the input (email, password)
        // 2. Check if the username exists in the database
        // 3. Verify the password against the stored hash
        // 4. Return a generated token message or an error message if something goes wrong
        return Response.ok(501).build();
    }
}
