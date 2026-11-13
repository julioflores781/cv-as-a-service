package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.audit.AuditEvent;

/**
 * Deja constancia de una acción de administración. El contrato: {@code record} <b>nunca lanza</b>; un fallo al auditar no puede
 * romper ni deshacer la operación que se audita. Quien la implementa decide el destino (hoy, el log de la aplicación).
 */
public interface AuditLog {

    void record(AuditEvent event);
}
