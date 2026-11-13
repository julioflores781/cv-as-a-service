package dev.julioflores.cv.infrastructure.adapter.out.audit;

import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.audit.AuditOutcome;
import dev.julioflores.cv.application.port.out.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import org.jboss.logging.Logger;

/**
 * Escribe cada {@link AuditEvent} como una línea del logger {@code audit}: {@code action=… outcome=… clave=valor…} con los detalles
 * ordenados, INFO si es un éxito y WARN si es un fallo de seguridad. El {@code request_id} y, en rutas admin, el {@code client_ip}
 * los añade el MDC de la petición; este adaptador no los toca ni lee la IP. Cumple el contrato del puerto: no lanza nunca.
 */
@ApplicationScoped
public class LogAuditLog implements AuditLog {

    /** Nombre fijo de la categoría: el filtro operativo es {@code grep audit}. */
    public static final String CATEGORY = "audit";

    private final Logger log;

    public LogAuditLog() {
        this(Logger.getLogger(CATEGORY));
    }

    /** Para las pruebas: permite un logger que falla. CDI usa el constructor sin argumentos. */
    public LogAuditLog(Logger log) {
        this.log = log;
    }

    @Override
    public void record(AuditEvent event) {
        try {
            log.log(event.outcome() == AuditOutcome.SUCCESS ? Logger.Level.INFO : Logger.Level.WARN, line(event));
        } catch (RuntimeException e) {
            // Auditar nunca rompe la operación. El logger roto no sirve para avisar: solo la clase del fallo, por stderr.
            System.err.println("audit log failed: " + e.getClass().getName());
        }
    }

    static String line(AuditEvent event) {
        StringBuilder line = new StringBuilder("action=").append(event.action())
                .append(" outcome=").append(event.outcome().name().toLowerCase(Locale.ROOT));
        event.details().forEach((key, value) -> line.append(' ').append(key).append('=').append(value));
        return line.toString();
    }
}
