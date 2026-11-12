package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import dev.julioflores.cv.application.error.ErrorCode;

/**
 * Tabla única de errores de la API: cada código estable con su estado HTTP.
 * Incluye los {@link ErrorCode} de negocio (mismo nombre) y los errores genéricos de transporte.
 * Cada constante necesita su título y detalle en los dos idiomas (ver problems_*.properties).
 */
public enum ApiError {
    // Errores de negocio (un ErrorCode de la aplicación por cada uno)
    RESUME_NOT_FOUND(404),
    EXPERIENCE_NOT_FOUND(404),
    SKILL_GROUP_NOT_FOUND(404),
    HL7_EMPTY_BODY(400),
    HL7_PAYLOAD_TOO_LARGE(413),
    HL7_UNSUPPORTED_FORMAT(400),
    HL7_INVALID_MESSAGE(422),
    HL7_UNSUPPORTED_MESSAGE(422),
    CONTACT_INVALID(400),
    CONTACT_RATE_LIMITED(429),
    ADMIN_INVALID_CREDENTIALS(401),
    ADMIN_LOGIN_LOCKED(429),
    ADMIN_LOGIN_RATE_LIMITED(429),
    ADMIN_UNAUTHORIZED(401),
    ADMIN_CURRENT_PASSWORD_WRONG(403),
    ADMIN_WEAK_PASSWORD(422),
    CONTENT_VERSION_CONFLICT(409),
    CONTENT_VERSION_NOT_FOUND(404),
    CONTENT_INVALID(422),
    CONTENT_PARITY_BROKEN(422),
    CONTENT_TOO_LARGE(413),
    IMAGE_INVALID(400),
    IMAGE_TOO_LARGE(413),
    IMAGE_UNSUPPORTED_TYPE(415),
    IMAGE_STORAGE_UNAVAILABLE(503),
    IMAGE_NOT_FOUND(404),
    IMAGE_IN_USE(409),
    NOT_FOUND(404),
    // Errores de transporte, sin ErrorCode de negocio
    BAD_REQUEST(400),
    FORBIDDEN(403),
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    UNSUPPORTED_MEDIA_TYPE(415),
    UNEXPECTED(500);

    private final int status;

    ApiError(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    static ApiError of(ErrorCode code) {
        return valueOf(code.name());
    }

    /** Errores de JAX-RS (ruta inexistente, método no permitido…). Lo desconocido cae en 400 o 500. */
    static ApiError fromStatus(int status) {
        return switch (status) {
            case 401 -> ADMIN_UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? UNEXPECTED : BAD_REQUEST;
        };
    }
}
