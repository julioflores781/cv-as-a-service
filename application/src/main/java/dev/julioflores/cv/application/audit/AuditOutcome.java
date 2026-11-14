package dev.julioflores.cv.application.audit;

/** Resultado de una acción auditada: {@code FAILURE} es solo un fallo de seguridad (credenciales, bloqueo, límite). */
public enum AuditOutcome {
    SUCCESS,
    FAILURE
}
