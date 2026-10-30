package com.benitoventas.auth;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.benitoventas.credentials.mongodb.panache.entity.Credential;
import com.benitoventas.roles.mongodb.panache.entity.Role;
import com.benitoventas.users.mongodb.panache.entity.User;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.MediaType;

@Path("/auth")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject
    JsonWebToken jwt;

    @Inject
    @RestClient
    ClientesClient clientes;

    @Inject
    @RestClient
    CuentasClient cuentas;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    public record SignupPayload(String name, String email, String phone, String password, String confirmPassword) {}
    public record LoginPayload(String email, String password) {}
    public record authResponse(String message, String token) {}
    public record meResponse(String name, String email, String phone) {}

    @PostConstruct
    void createIndexes() {
        // Database constraints, not only pre-insert checks, prevent duplicate signups.
        User.mongoCollection().createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
        Credential.mongoCollection().createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
    }

    @GET
    @Path("/me")
    @Authenticated
    public Response me() {
        if (jwt.getSubject() == null || !ObjectId.isValid(jwt.getSubject())) {
            return message(Status.UNAUTHORIZED, "La sesión no es válida.");
        }
        User user = User.findById(new ObjectId(jwt.getSubject()));
        if (user == null || !Boolean.TRUE.equals(user.isActive)) {
            return message(Status.UNAUTHORIZED, "La cuenta no está activa.");
        }
        return Response.ok(new meResponse(user.name, user.email, user.phone)).build();
    }

    @POST
    @Path("/clientes/signup")
    @PermitAll
    public Response clientSignup(SignupPayload signupUser) {
        if (signupUser == null || blank(signupUser.name()) || blank(signupUser.email())
                || blank(signupUser.phone()) || blank(signupUser.password()) || blank(signupUser.confirmPassword())) {
            return message(Status.BAD_REQUEST, "Por favor rellena todos los campos.");
        }
        if (!signupUser.password().equals(signupUser.confirmPassword())) {
            return message(Status.BAD_REQUEST, "Las contraseñas deben de ser iguales.");
        }
        if (!validPasswordLength(signupUser.password())) {
            return message(Status.BAD_REQUEST, "La contraseña no puede superar los 72 bytes UTF-8.");
        }
        String email = normalizeEmail(signupUser.email());
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            return message(Status.BAD_REQUEST, "El correo electrónico no es válido.");
        }
        User user = User.find("email", email).firstResult();
        if (user != null && Boolean.TRUE.equals(user.isActive)) {
            return message(Status.CONFLICT, "El correo electrónico ya está registrado.");
        }
        Credential credential = Credential.find("email", email).firstResult();
        if (credential != null && !passwordMatches(signupUser.password(), credential.passwordHash)) {
            return invalidLogin();
        }
        if (user != null && (credential == null || !user.id.equals(credential.userId))) {
            // Without a stored password proof, an incomplete account must not be claimed.
            return message(Status.CONFLICT, "El registro está incompleto y requiere revisión del administrador.");
        }
        Role role = Role.find("name", "cliente").firstResult();
        if (role == null) {
            return message(Status.SERVICE_UNAVAILABLE, "No se pudo encontrar el rol de cliente.");
        }
        if (user != null && (user.roleIds == null || !user.roleIds.contains(role.id))) {
            return message(Status.CONFLICT, "La cuenta no corresponde a un registro de cliente.");
        }
        boolean newUser = user == null;
        if (newUser) {
            if (credential == null) {
                credential = new Credential();
                credential.userId = new ObjectId();
                credential.email = email;
                credential.passwordHash = BcryptUtil.bcryptHash(signupUser.password());
                try {
                    // Store password proof first. If the user write fails, a retry can
                    // verify this credential and reuse its userId without a transaction.
                    credential.persist();
                } catch (MongoWriteException exception) {
                    if (exception.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                        throw exception;
                    }
                    return message(Status.CONFLICT, "El registro ya está en proceso. Reintenta con los mismos datos.");
                }
            }
            user = new User();
            user.id = credential.userId;
            user.name = signupUser.name().trim();
            user.email = email;
            user.phone = signupUser.phone().trim();
            user.isActive = false;
            user.roleIds.add(role.id);
            try {
                user.persist();
            } catch (MongoWriteException exception) {
                if (exception.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                    throw exception;
                }
                return message(Status.CONFLICT, "El registro ya está en proceso. Reintenta con los mismos datos.");
            }
        }
        // Retries use the original stored profile, not unverified replacement data.
        try {
            String serviceToken = Jwt.issuer(issuer)
                    .subject(user.id.toHexString())
                    .groups(Set.of("auth-service"))
                    .expiresIn(60)
                    .sign();
            try (
                Response result = clientes.create(
                    new ClientesClient.ClientePayload(
                      user.id.toHexString(),
                      user.name,
                      user.email,
                      user.phone
                    ), "Bearer " + serviceToken)
                ) {
                if (result.getStatus() != Status.OK.getStatusCode() && result.getStatus() != Status.CREATED.getStatusCode()) {
                    Log.warnf("Clientes rechazó completar el registro del usuario %s: HTTP %d", user.id, result.getStatus());
                    return pendingSignup();
                }
                ClientesClient.ClienteResponse cliente =
                        result.readEntity(ClientesClient.ClienteResponse.class);
                if (cliente == null
                        || cliente.id() == null
                        || !ObjectId.isValid(cliente.id())
                        || !user.id.toHexString().equals(cliente.userId())) {
                    Log.warn("Clientes devolvió una referencia de cliente inválida.");
                    return pendingSignup();
                }
                user.clienteId = new ObjectId(cliente.id());
            }
        } catch (ProcessingException | WebApplicationException exception) {
            Log.warnf("No se pudo contactar a Clientes para completar el registro del usuario %s", user.id);
            return pendingSignup();
        }
        try {
            String serviceToken = Jwt.issuer(issuer)
                    .subject(user.id.toHexString())
                    .claim("clienteId", user.clienteId.toHexString())
                    .groups(Set.of("auth-service"))
                    .expiresIn(60)
                    .sign();
            try (Response result = cuentas.create(
                    new CuentasClient.CuentaPayload(user.clienteId.toHexString()), "Bearer " + serviceToken)) {
                if (result.getStatus() != Status.OK.getStatusCode() && result.getStatus() != Status.CREATED.getStatusCode()) {
                    Log.warnf("Facturación rechazó crear la cuenta del cliente %s: HTTP %d", user.clienteId, result.getStatus());
                    return pendingSignup();
                }
                CuentasClient.CuentaResponse cuenta = result.readEntity(CuentasClient.CuentaResponse.class);
                if (cuenta == null || cuenta.id() == null || !ObjectId.isValid(cuenta.id())
                        || !user.clienteId.toHexString().equals(cuenta.clienteId())) {
                    Log.warn("Facturación devolvió una referencia de cuenta inválida.");
                    return pendingSignup();
                }
            }
        } catch (ProcessingException | WebApplicationException exception) {
            Log.warnf("No se pudo contactar a Facturación para crear la cuenta del cliente %s", user.clienteId);
            return pendingSignup();
        }
        // Retries recover the existing client and account if signing or activation fails.
        String token = userToken(user);
        user.isActive = true;
        user.update();
        return Response.status(newUser ? Status.CREATED : Status.OK)
                .entity(new authResponse("Cliente registrado y cuenta activada correctamente.", token)).build();
    }

    @POST
    @Path("/clientes/login")
    @PermitAll
    public Response clientLogin(LoginPayload loginUser) {
        return login(loginUser, "cliente");
    }

    @POST
    @Path("/admin/signup")
    @RolesAllowed("Admin")
    public Response adminSignup(SignupPayload signupUser) {
        return message(Status.NOT_IMPLEMENTED, "El registro de administradores no está implementado.");
    }

    @POST
    @Path("/admin/login")
    @PermitAll
    public Response adminLogin(LoginPayload loginUser) {
        return login(loginUser, "Admin");
    }

    private Response login(LoginPayload loginUser, String requiredRole) {
        if (loginUser == null || blank(loginUser.email()) || blank(loginUser.password())) {
            return message(Status.BAD_REQUEST, "Por favor ingresa el correo electrónico y la contraseña.");
        }
        if (!validPasswordLength(loginUser.password())) {
            return invalidLogin();
        }
        String email = normalizeEmail(loginUser.email());
        Credential credential = Credential.find("email", email).firstResult();
        if (credential == null || !passwordMatches(loginUser.password(), credential.passwordHash)) {
            return invalidLogin();
        }
        User user = User.findById(credential.userId);
        if (user == null || !email.equals(user.email) || !Boolean.TRUE.equals(user.isActive)) {
            return invalidLogin();
        }
        Set<String> roles = roleNames(user);
        if (!roles.contains(requiredRole)) {
            return invalidLogin();
        }
        return Response.ok(new authResponse("Inicio de sesión exitoso.", userToken(user))).build();
    }

    private Set<String> roleNames(User user) {
        Set<String> names = new HashSet<>();
        if (user.roleIds != null) {
            for (ObjectId roleId : user.roleIds) {
                Role role = Role.findById(roleId);
                // This reserved group is only issued for server-to-server calls.
                if (role != null && role.name != null && !"auth-service".equals(role.name)) {
                    names.add(role.name);
                }
            }
        }
        return names;
    }

    private String userToken(User user) {
        JwtClaimsBuilder token = Jwt.issuer(issuer)
                .subject(user.id.toHexString())
                .upn(user.email)
                .groups(roleNames(user))
                .expiresIn(3600);
        if (user.clienteId != null) {
            token.claim("clienteId", user.clienteId.toHexString());
        }
        return token.sign();
    }

    private boolean passwordMatches(String password, String hash) {
        if (hash == null) {
            return false;
        }
        try {
            return BcryptUtil.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean validPasswordLength(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Response invalidLogin() {
        return message(Status.UNAUTHORIZED, "Correo electrónico o contraseña incorrectos, o cuenta no activa.");
    }

    private Response pendingSignup() {
        return message(Status.SERVICE_UNAVAILABLE,
                "El registro está pendiente. Reintenta el signup con el mismo correo electrónico y contraseña.");
    }

    private Response message(Status status, String message) {
        return Response.status(status).entity(new authResponse(message, null)).build();
    }
}
