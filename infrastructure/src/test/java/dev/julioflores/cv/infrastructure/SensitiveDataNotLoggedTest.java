package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ningún registro (mensaje, parámetros, MDC ni traza) de ninguna categoría lleva credenciales ni datos de la base de datos. */
@QuarkusTest
@QuarkusTestResource(value = MinioTestResource.class, restrictToAnnotatedClass = true)
class SensitiveDataNotLoggedTest {

    private static final String SESSION = "/api/v1/admin/auth/session";

    @Inject
    AdminTestSupport support;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    private LogRecords logs;

    @BeforeEach
    void capture() {
        logs = LogRecords.attach();
        support.resetAccount();
        ThreadRecordingAccountRepository.failReads = false;
    }

    @AfterEach
    void release() {
        ThreadRecordingAccountRepository.failReads = false;
        logs.close();
    }

    @Test
    void neitherAnInvalidNorAValidTokenNorTheAuthorizationHeaderAreLogged() { // T20
        var invalid = given().header("Authorization", "Bearer CANARY-TOKEN-1").when().get(SESSION);
        invalid.then().statusCode(401);
        String valid = support.token(1);
        var ok = given().header("Authorization", "Bearer " + valid).when().get(SESSION);
        ok.then().statusCode(200);
        logs.awaitAccessLine("GET " + SESSION + " 401", invalid.header("X-Request-Id"));
        logs.awaitAccessLine("GET " + SESSION + " 200", ok.header("X-Request-Id"));

        String dump = logs.dump();
        assertFalse(dump.contains("CANARY-TOKEN-1"), dump);
        assertFalse(dump.contains(valid), dump);
        assertFalse(dump.contains("Bearer"), dump);
        assertFalse(dump.toLowerCase().contains("authorization"), dump);
    }

    @Test
    void aDatabaseFailureOnAnAdminRouteLeaksNoMessageAndLogsOnlyClassAndSqlState() { // T24
        String token = support.token(1);
        ThreadRecordingAccountRepository.failReads = true;
        var response = given().header("Authorization", "Bearer " + token).when().get(SESSION);
        response.then().statusCode(500).contentType("application/problem+json").body("code", equalTo("UNEXPECTED"));
        logs.awaitAccessLine("GET " + SESSION + " 500", response.header("X-Request-Id"));

        String dump = logs.dump();
        assertFalse(dump.contains("hunter2"), dump);
        assertFalse(dump.contains("secreto"), dump);
        assertFalse(dump.contains("jdbc:"), dump);
        ExtLogRecord error = logs.await(r -> r.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()).get(0);
        assertTrue(error.getFormattedMessage().contains("Unexpected database error (SQLState=08006"), error.getFormattedMessage());
        assertTrue(error.getThrown() == null, "sin traza");
        assertTrue(LogRecords.mdc(error, "request_id").equals(response.header("X-Request-Id")));
    }

    @Test
    void theFullAdminFlowLeavesNoCanaryInAnyRecordOfAnyCategory() throws SQLException { // T50
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        AdminAuditFlow.Result flow;
        try {
            flow = AdminAuditFlow.run(support, "203.0.113.9", AdminAuditFlow.Canaries.unique());
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
            TestClockConfig.reset();
            support.execute("DELETE FROM contact_message");
            MinioSupport.clear();
        }
        // Todo el recorrido ya terminó: se espera a la línea de access de la última petición y a la de cada paso.
        for (AdminAuditFlow.Step step : flow.steps()) {
            logs.await(r -> LogRecords.ACCESS.equals(r.getLoggerName()) && r.getFormattedMessage().contains(" rid=" + step.requestId() + " "));
        }
        assertFalse(logs.byLogger("audit").isEmpty(), "el recorrido sí auditó: la búsqueda negativa no puede ser vacía");

        String dump = logs.dump();
        for (String secret : flow.secrets()) {
            assertFalse(secret.isBlank());
            assertFalse(dump.contains(secret), "aparece en un registro: " + secret);
        }
        assertFalse(dump.contains("Bearer"), dump);
        assertFalse(dump.contains("eyJ"), "ningún JWT");
    }
}
