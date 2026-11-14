package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.in.ChangeAdminPasswordUseCase;
import dev.julioflores.cv.application.port.in.LoginAdminUseCase;
import dev.julioflores.cv.application.port.in.LogoutAllSessionsUseCase;
import dev.julioflores.cv.application.port.in.VerifyAdminSessionUseCase;
import dev.julioflores.cv.application.service.AdminUnauthorizedException;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminModels.LoginRequest;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminModels.PasswordChangeRequest;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminModels.SessionResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminModels.TokenResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Sesión del administrador: login, sonda de sesión, cambio de contraseña y cierre de todas las sesiones. */
@Path("/api/v1/admin/auth")
@Tag(name = "Admin")
public class AdminAuthResource {

    static final String BEARER = "bearerAuth";

    private final LoginAdminUseCase loginAdmin;
    private final VerifyAdminSessionUseCase verifySession;
    private final ChangeAdminPasswordUseCase changePassword;
    private final LogoutAllSessionsUseCase logoutAll;
    private final JsonWebToken jwt;

    public AdminAuthResource(
            LoginAdminUseCase loginAdmin,
            VerifyAdminSessionUseCase verifySession,
            ChangeAdminPasswordUseCase changePassword,
            LogoutAllSessionsUseCase logoutAll,
            JsonWebToken jwt) {
        this.loginAdmin = loginAdmin;
        this.verifySession = verifySession;
        this.changePassword = changePassword;
        this.logoutAll = logoutAll;
        this.jwt = jwt;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminLogin",
            summary = "Inicia sesión como administrador",
            description = "Comprueba la contraseña y devuelve un JWT (HS256) válido por defecto 8 horas. "
                    + "Una contraseña mala y una cuenta inexistente dan exactamente el mismo `401`. Tras 5 fallos seguidos la "
                    + "cuenta se bloquea 15 minutos (`429` con `Retry-After`), incluso con la contraseña correcta. Además hay "
                    + "un límite global de 10 intentos por minuto. Es el único endpoint admin sin token.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LoginRequest.class)))
    @APIResponse(responseCode = "200", description = "Sesión abierta.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TokenResponse.class),
                    examples = @ExampleObject(name = "token", value = AdminExamples.TOKEN)))
    @APIResponse(responseCode = "400", description = "Falta el cuerpo, la `password` o el JSON está mal formado (`BAD_REQUEST`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "missingPassword", value = AdminExamples.BAD_REQUEST)))
    @APIResponse(responseCode = "401", description = "Contraseña incorrecta o sin cuenta (`ADMIN_INVALID_CREDENTIALS`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "invalidCredentials", value = AdminExamples.INVALID_CREDENTIALS)))
    @APIResponse(responseCode = "429",
            description = "Cuenta bloqueada (`ADMIN_LOGIN_LOCKED`, con `Retry-After`) o límite global agotado "
                    + "(`ADMIN_LOGIN_RATE_LIMITED`).",
            headers = @Header(name = "Retry-After", description = "Segundos hasta que acabe el bloqueo (solo con `ADMIN_LOGIN_LOCKED`).",
                    schema = @Schema(type = SchemaType.INTEGER, example = "900")),
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "locked", summary = "Cuenta bloqueada", value = AdminExamples.LOCKED),
                        @ExampleObject(name = "rateLimited", summary = "Límite global", value = AdminExamples.RATE_LIMITED)
                    }))
    public TokenResponse login(LoginRequest request) {
        if (request == null || request.password() == null) {
            throw new BadRequestException();
        }
        return token(loginAdmin.login(request.password()));
    }

    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityRequirement(name = BEARER)
    @Operation(
            operationId = "adminSession",
            summary = "Comprueba la sesión actual",
            description = "Sonda para el panel: `200` si el token vale (firma, caducidad y versión de sesión) y `401` si no.")
    @APIResponse(responseCode = "200", description = "La sesión es válida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SessionResponse.class),
                    examples = @ExampleObject(name = "session", value = AdminExamples.SESSION)))
    public SessionResponse session() {
        Instant passwordChangedAt = verifySession.verify(AdminPaths.sessionVersion(jwt))
                .orElseThrow(AdminUnauthorizedException::new);
        return new SessionResponse(true, Instant.ofEpochSecond(jwt.getExpirationTime()), passwordChangedAt);
    }

    @PUT
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityRequirement(name = BEARER)
    @Operation(
            operationId = "adminChangePassword",
            summary = "Cambia la contraseña",
            description = "Exige la contraseña actual. Invalida todos los tokens anteriores y devuelve uno nuevo, para que el "
                    + "panel no pierda su propia sesión. Una contraseña actual incorrecta da `403` (no `401`, para no "
                    + "confundirla con una sesión caducada) y cuenta para el bloqueo igual que un login fallido.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = PasswordChangeRequest.class)))
    @APIResponse(responseCode = "200", description = "Contraseña cambiada; este es el token nuevo.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TokenResponse.class),
                    examples = @ExampleObject(name = "token", value = AdminExamples.TOKEN)))
    @APIResponse(responseCode = "400", description = "Falta el cuerpo o algún campo, o el JSON está mal formado (`BAD_REQUEST`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "missingField", value = AdminExamples.BAD_REQUEST)))
    @APIResponse(responseCode = "403", description = "La contraseña actual no es correcta (`ADMIN_CURRENT_PASSWORD_WRONG`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "currentWrong", value = AdminExamples.CURRENT_WRONG)))
    @APIResponse(responseCode = "422", description = "La contraseña nueva no cumple la política (`ADMIN_WEAK_PASSWORD`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "weak", value = AdminExamples.WEAK)))
    @APIResponse(responseCode = "429", description = "La cuenta está bloqueada por intentos fallidos (`ADMIN_LOGIN_LOCKED`).",
            headers = @Header(name = "Retry-After", description = "Segundos hasta que acabe el bloqueo.",
                    schema = @Schema(type = SchemaType.INTEGER, example = "900")),
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "locked", value = AdminExamples.LOCKED)))
    public TokenResponse changePassword(PasswordChangeRequest request) {
        if (request == null || request.currentPassword() == null || request.newPassword() == null) {
            throw new BadRequestException();
        }
        return token(changePassword.change(request.currentPassword(), request.newPassword()));
    }

    @POST
    @Path("/logout-all")
    @SecurityRequirement(name = BEARER)
    @Operation(
            operationId = "adminLogoutAll",
            summary = "Cierra todas las sesiones",
            description = "Invalida todos los tokens emitidos, el que se usa aquí incluido. No hay cierre de una sola sesión.")
    @APIResponse(responseCode = "204", description = "Todas las sesiones quedan invalidadas. Sin cuerpo.")
    public Response logoutAll() {
        logoutAll.logoutAll();
        return Response.noContent().build();
    }

    private static TokenResponse token(AdminSession session) {
        return new TokenResponse(session.accessToken(), "Bearer", session.expiresAt());
    }
}
