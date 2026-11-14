package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.port.out.AuditLog;
import dev.julioflores.cv.infrastructure.adapter.out.audit.LogAuditLog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logging.MDC;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El adaptador de auditoría: una línea por evento en la categoría {@code audit}, y jamás una excepción hacia el caso de uso. */
@QuarkusTest
class LogAuditLogTest {

    @Inject
    AuditLog audit;

    private LogRecords logs;

    @BeforeEach
    void capture() {
        logs = LogRecords.attach();
    }

    @AfterEach
    void release() {
        MDC.remove("request_id");
        MDC.remove("client_ip");
        logs.close();
    }

    @Test
    void aSuccessIsInfoAndAFailureIsWarnWithSortedDetailsAndTheRequestMdc() { // T48
        MDC.put("request_id", "req-audit-0001");
        MDC.put("client_ip", "203.0.113.9");

        audit.record(AuditEvent.success(AuditAction.CONTENT_SAVED, "kind", "services", "es_version", 7, "en_version", 7,
                "comment_present", true, "comment_length", 23));
        audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILED, "reason", "bad_credentials"));

        List<ExtLogRecord> lines = logs.byLogger("audit");
        assertEquals(2, lines.size());
        ExtLogRecord ok = lines.get(0);
        assertEquals(java.util.logging.Level.INFO.intValue(), ok.getLevel().intValue());
        assertEquals("action=CONTENT_SAVED outcome=success comment_length=23 comment_present=true en_version=7 es_version=7 kind=services",
                ok.getFormattedMessage());
        ExtLogRecord failed = lines.get(1);
        assertEquals(java.util.logging.Level.WARNING.intValue(), failed.getLevel().intValue());
        assertEquals("action=LOGIN_FAILED outcome=failure reason=bad_credentials", failed.getFormattedMessage());
        for (ExtLogRecord line : lines) {
            assertEquals("req-audit-0001", LogRecords.mdc(line, "request_id"));
            assertEquals("203.0.113.9", LogRecords.mdc(line, "client_ip"));
        }
    }

    @Test
    void aMessageWithPercentSignsIsNotTreatedAsAFormat() { // extra fuera del plan
        audit.record(AuditEvent.success(AuditAction.IMAGE_UPLOADED, "key", "100%s%d%n"));
        assertEquals("action=IMAGE_UPLOADED outcome=success key=100%s%d%n", logs.byLogger("audit").get(0).getFormattedMessage());
    }

    @Test
    void aLogHandlerThatThrowsNeverBreaksTheCaller() { // T45
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger("audit");
        Handler exploding = new Handler() {
            @Override
            public void publish(LogRecord record) {
                throw new IllegalStateException("handler boom");
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        jul.addHandler(exploding);
        try {
            assertDoesNotThrow(() -> audit.record(AuditEvent.success(AuditAction.LOGIN_SUCCESS)));
            assertDoesNotThrow(() -> audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILED, "reason", "no_account")));
        } finally {
            jul.removeHandler(exploding);
        }
    }

    @Test
    void aLoggerThatThrowsIsSwallowedByTheAdapter() { // T45 (el catch del adaptador, sin depender de cómo el LogManager trate al handler)
        org.jboss.logging.Logger broken = new org.jboss.logging.Logger("audit") {
            @Override
            public boolean isEnabled(Level level) {
                return true;
            }

            @Override
            protected void doLog(Level level, String loggerClassName, Object message, Object[] parameters, Throwable thrown) {
                throw new IllegalStateException("logger boom");
            }

            @Override
            protected void doLogf(Level level, String loggerClassName, String format, Object[] parameters, Throwable thrown) {
                throw new IllegalStateException("logger boom");
            }
        };
        AuditLog adapter = new LogAuditLog(broken);
        assertDoesNotThrow(() -> adapter.record(AuditEvent.success(AuditAction.LOGIN_SUCCESS)));
        assertTrue(logs.byLogger("audit").isEmpty());
    }
}
