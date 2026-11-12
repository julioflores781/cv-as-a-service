package dev.julioflores.cv.application.content;

/** Códigos estables de las violaciones de contenido: forman parte del contrato del panel. */
public enum ViolationCode {
    REQUIRED,
    TOO_LONG,
    TOO_MANY,
    UNKNOWN_FIELD,
    DUPLICATE_ID,
    INVALID_ID,
    INVALID_VALUE,
    INVALID_DATE,
    INVALID_URL,
    ANONYMOUS_CLIENT_FORBIDDEN,
    CLIENT_REQUIRED,
    IMAGES_NOT_ALLOWED_FOR_PROJECT,
    IMAGE_KEY_INVALID,
    PARITY_MISMATCH
}
