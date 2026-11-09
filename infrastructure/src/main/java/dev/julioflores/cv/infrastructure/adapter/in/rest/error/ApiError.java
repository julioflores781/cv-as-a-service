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
    // Errores de transporte, sin ErrorCode de negocio
    BAD_REQUEST(400),
    NOT_FOUND(404),
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
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? UNEXPECTED : BAD_REQUEST;
        };
    }
}
