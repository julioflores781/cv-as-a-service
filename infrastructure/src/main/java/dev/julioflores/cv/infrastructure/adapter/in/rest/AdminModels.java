package dev.julioflores.cv.infrastructure.adapter.in.rest;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Cuerpos de la zona de administración (auth). */
public final class AdminModels {

    private AdminModels() {}

    @Schema(name = "AdminLoginRequest", description = "Credenciales del único administrador: solo la contraseña.")
    public record LoginRequest(
            @Schema(description = "Contraseña del administrador.", required = true, example = "una frase larga y secreta")
                    String password) {}

    @Schema(name = "AdminPasswordChangeRequest", description = "Cambio de contraseña del administrador.")
    public record PasswordChangeRequest(
            @Schema(description = "Contraseña actual.", required = true, example = "una frase larga y secreta")
                    String currentPassword,
            @Schema(description = "Contraseña nueva: de 12 a 128 caracteres, distinta de la actual. "
                    + "No hay reglas de composición.", required = true, minLength = 12, maxLength = 128,
                    example = "otra frase larga y secreta") String newPassword) {}

    @Schema(name = "AdminToken", description = "Sesión emitida: un JWT HS256 que se envía como `Authorization: Bearer`.")
    public record TokenResponse(
            @Schema(description = "El JWT firmado.", required = true, example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.firma")
                    String accessToken,
            @Schema(description = "Tipo de token. Siempre `Bearer`.", required = true, example = "Bearer") String tokenType,
            @Schema(description = "Cuándo caduca el token (UTC).", required = true, example = "2026-11-12T20:15:00Z")
                    Instant expiresAt) {}

    @Schema(name = "AdminSessionInfo", description = "Estado de la sesión actual. Sirve de sonda para el panel.")
    public record SessionResponse(
            @Schema(description = "Siempre `true`: si la sesión no vale, la respuesta es un 401.", required = true,
                    example = "true") boolean authenticated,
            @Schema(description = "Cuándo caduca el token usado (UTC).", required = true, example = "2026-11-12T20:15:00Z")
                    Instant expiresAt,
            @Schema(description = "Cuándo se cambió por última vez la contraseña (UTC).", required = true,
                    example = "2026-11-01T09:30:00Z") Instant passwordChangedAt) {}
}
