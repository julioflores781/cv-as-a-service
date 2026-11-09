package dev.julioflores.cv.application.error;

/**
 * Identificadores estables de los errores de negocio. Forman parte del contrato público de la API
 * (el cliente decide con ellos), así que no se renombran. El texto para el usuario vive en la
 * infraestructura, donde se traduce; aquí solo hay códigos.
 */
public enum ErrorCode {
    RESUME_NOT_FOUND,
    EXPERIENCE_NOT_FOUND,
    SKILL_GROUP_NOT_FOUND,
    HL7_EMPTY_BODY,
    HL7_PAYLOAD_TOO_LARGE,
    HL7_UNSUPPORTED_FORMAT,
    HL7_INVALID_MESSAGE,
    HL7_UNSUPPORTED_MESSAGE,
    CONTACT_INVALID,
    CONTACT_RATE_LIMITED
}
