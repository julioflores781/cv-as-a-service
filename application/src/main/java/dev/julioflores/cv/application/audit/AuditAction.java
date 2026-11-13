package dev.julioflores.cv.application.audit;

/** Las acciones de administración que dejan rastro. Enum cerrado: una acción nueva se añade aquí y en la tabla de ARCHITECTURE.md. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGIN_LOCKED,
    LOGIN_RATE_LIMITED,
    PASSWORD_CHANGED,
    PASSWORD_CHANGE_FAILED,
    SESSIONS_REVOKED,
    CONTENT_SAVED,
    CONTENT_ROLLED_BACK,
    IMAGE_UPLOADED,
    IMAGE_DELETED,
    IMAGES_CLEANUP,
    MESSAGES_READ
}
