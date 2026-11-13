package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditOutcome;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El recorrido admin completo por HTTP deja exactamente una línea {@code audit} por acción, con su request id y la IP enviada. */
@QuarkusTest
@QuarkusTestResource(value = MinioTestResource.class, restrictToAnnotatedClass = true)
class AdminAuditLogTest {

    private static final String IP = "203.0.113.9";
    private static final String AUDIT = "audit";

    @Inject
    AdminTestSupport support;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    private List<Object[]> backup;
    private LogRecords logs;

    @BeforeEach
    void setUp() throws SQLException {
        support.resetAccount();
        backup = ContentVersions.snapshot(dataSource);
        TestClockConfig.reset();
        logs = LogRecords.attach();
    }

    @AfterEach
    void restore() throws SQLException {
        logs.close();
        ContentVersions.restore(dataSource, backup, cache);
        TestClockConfig.reset();
        support.execute("DELETE FROM contact_message");
        MinioSupport.clear();
    }

    private static String expectedLine(AuditAction action, AuditOutcome outcome, Map<String, String> details) {
        StringBuilder line = new StringBuilder("action=" + action + " outcome=" + outcome.name().toLowerCase());
        new TreeMap<>(details).forEach((k, v) -> line.append(' ').append(k).append('=').append(v));
        return line.toString();
    }

    private List<ExtLogRecord> auditFor(String requestId) {
        return logs.all().stream()
                .filter(r -> AUDIT.equals(r.getLoggerName()) && requestId.equals(LogRecords.mdc(r, "request_id")))
                .toList();
    }

    @Test
    void theFullAdminFlowLeavesExactlyOneAuditLinePerActionWithItsRequestIdAndTheSentIp() { // T49
        AdminAuditFlow.Result flow = AdminAuditFlow.run(support, IP, AdminAuditFlow.Canaries.unique());

        assertEquals(12, flow.steps().size());
        for (AdminAuditFlow.Step step : flow.steps()) {
            String rid = step.requestId();
            assertNotNull(rid, step.label());
            List<ExtLogRecord> lines = auditFor(rid);
            assertEquals(1, lines.size(), step.label() + ": " + logs.dump());
            ExtLogRecord line = lines.get(0);
            String message = line.getFormattedMessage();
            if (step.details() != null) {
                assertEquals(expectedLine(step.action(), step.outcome(), step.details()), message, step.label());
            } else { // la subida: la clave y el tipo salen de los bytes, no se conocen de antemano
                assertTrue(message.matches("action=IMAGE_UPLOADED outcome=success bytes=\\d+ content_type=image/(png|jpeg) height=\\d+ "
                        + "key=site/projects/[0-9a-f-]{36}\\.(png|jpg) width=\\d+"), message);
            }
            assertEquals(step.outcome() == AuditOutcome.SUCCESS ? java.util.logging.Level.INFO.intValue()
                    : java.util.logging.Level.WARNING.intValue(), line.getLevel().intValue(), step.label());
            assertEquals(IP, LogRecords.mdc(line, "client_ip"), step.label());
            // El mismo id que el cliente recibió está en la línea de access de esa petición.
            logs.await(r -> LogRecords.ACCESS.equals(r.getLoggerName()) && r.getFormattedMessage().contains(" rid=" + rid + " "));
        }
        long total = logs.byLogger(AUDIT).size();
        assertEquals(flow.steps().size(), total, "no hay líneas audit de más: " + logs.dump());
    }

    @Test
    void readsOfContentVersionsSessionAndImagesLeaveNoAuditLine() { // T52
        AdminAuditFlow.Result flow = AdminAuditFlow.run(support, IP, AdminAuditFlow.Canaries.unique());

        assertEquals(5, flow.reads().size());
        for (var read : flow.reads()) {
            String rid = read.header("X-Request-Id");
            logs.await(r -> LogRecords.ACCESS.equals(r.getLoggerName()) && r.getFormattedMessage().contains(" rid=" + rid + " "));
            assertEquals(0, auditFor(rid).size(), "una lectura no se audita: " + rid);
        }
    }
}
