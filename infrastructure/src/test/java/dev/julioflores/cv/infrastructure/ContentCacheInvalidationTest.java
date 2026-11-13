package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.domain.Language;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresSiteContentRepository;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.jboss.logmanager.ExtLogRecord;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Efecto de la invalidación sobre los GET públicos, con el puerto a mano (T111 parcial) y con guardado y rollback reales (T111, T112). */
@QuarkusTest
class ContentCacheInvalidationTest {

    @Inject
    ContentCacheInvalidator invalidator;

    @Inject
    DataSource dataSource;

    @Inject
    AdminTestSupport support;

    @Inject
    ObjectMapper mapper;

    private static final String SITE_LOGGER = PostgresSiteContentRepository.class.getName();
    private static final String INVALIDATED = "Content cache invalidated ";

    private static String requestId() {
        return "cc-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static List<ExtLogRecord> invalidations(LogRecords logs, String rid) {
        return logs.byLogger(SITE_LOGGER).stream()
                .filter(r -> r.getFormattedMessage().startsWith(INVALIDATED) && rid.equals(LogRecords.mdc(r, "request_id")))
                .toList();
    }

    private static void save(String token, String rid, String es, String en) {
        given().header("Authorization", "Bearer " + token).header("X-Request-Id", rid).contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of(
                        "es", servicesDocument(token, "es", es), "en", servicesDocument(token, "en", en))))
                .when().put("/api/v1/admin/content/services").then().statusCode(200);
    }

    // T111 (parte de este slug: puerto + GET público; el guardado real queda para api-admin-content)
    @Test
    void aNewActiveVersionShowsUpOnTheNextPublicReadOnlyAfterInvalidating() throws SQLException {
        given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio de ejemplo"));
        ContentVersions.publishServices(dataSource, "es", "Servicio nuevo");
        try {
            given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio de ejemplo"));

            invalidator.invalidate("services", Language.ES);

            given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio nuevo"));
        } finally {
            ContentVersions.revertServices(dataSource, "es");
            invalidator.invalidate("services", Language.ES);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicesDocument(String token, String lang, String title) {
        Map<String, Object> document = given().header("Authorization", "Bearer " + token).when()
                .get("/api/v1/admin/content/services").then().statusCode(200).extract().path("languages." + lang + ".content");
        ((List<Map<String, Object>>) document.get("services")).get(0).put("title", title);
        return document;
    }

    private static void publicTitle(String lang, String expected) {
        given().queryParam("lang", lang).when().get("/api/v1/services").then().statusCode(200).body("[0].title", equalTo(expected));
    }

    // T111: un guardado real; el siguiente GET público (sin esperar el TTL de 5 min) ya trae lo nuevo
    @Test
    void aRealSaveShowsUpOnTheVeryNextPublicRead() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            publicTitle("es", "Servicio de ejemplo"); // llena la caché con lo viejo
            publicTitle("en", "Sample service");

            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of(
                            "es", servicesDocument(token, "es", "Guardado ya"), "en", servicesDocument(token, "en", "Saved now"))))
                    .when().put("/api/v1/admin/content/services").then().statusCode(200);

            publicTitle("es", "Guardado ya");
            publicTitle("en", "Saved now");
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    // T112: un rollback real; el siguiente GET público ya trae lo restaurado
    @Test
    void aRealRollbackShowsUpOnTheVeryNextPublicRead() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of(
                            "es", servicesDocument(token, "es", "Guardado"), "en", servicesDocument(token, "en", "Saved"))))
                    .when().put("/api/v1/admin/content/services").then().statusCode(200);
            publicTitle("es", "Guardado"); // la caché ya tiene lo guardado

            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersion", 2, "targetVersion", 1))
                    .when().post("/api/v1/admin/content/services/es/rollback").then().statusCode(200);

            publicTitle("es", "Servicio de ejemplo");
            publicTitle("en", "Saved"); // solo se hizo rollback del español
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    // T61: un guardado deja un INFO por idioma (dos), con el request_id de la petición
    @Test
    void aSaveLeavesOneInvalidationLinePerLanguageWithTheRequestId() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            save(token, rid, "Guardado log", "Saved log");
            logs.awaitAccessLine("PUT /api/v1/admin/content/services 200", rid);
            List<ExtLogRecord> lines = invalidations(logs, rid);
            assertEquals(List.of(INVALIDATED + "kind=services lang=es", INVALIDATED + "kind=services lang=en"),
                    LogRecords.messages(lines), logs.dump());
            lines.forEach(r -> assertEquals(Level.INFO.intValue(), r.getLevel().intValue()));
            assertFalse(logs.dump().contains("Guardado log"), "el contenido no se loguea");
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    // T61: un rollback deja un solo INFO (solo el idioma revertido)
    @Test
    void aRollbackLeavesExactlyOneInvalidationLine() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            save(token, requestId(), "Guardado", "Saved");
            String rid = requestId();
            try (LogRecords logs = LogRecords.attach()) {
                given().header("Authorization", "Bearer " + token).header("X-Request-Id", rid).contentType(ContentType.JSON)
                        .body(Map.of("baseVersion", 2, "targetVersion", 1))
                        .when().post("/api/v1/admin/content/services/es/rollback").then().statusCode(200);
                logs.awaitAccessLine("POST /api/v1/admin/content/services/es/rollback 200", rid);
                assertEquals(List.of(INVALIDATED + "kind=services lang=es"), LogRecords.messages(invalidations(logs, rid)), logs.dump());
            }
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    // Extra fuera del plan: una lectura pública y un guardado fallido (conflicto de versión) no invalidan nada ni dejan la línea
    @Test
    void aRejectedSaveLeavesNoInvalidationLine() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            given().header("Authorization", "Bearer " + token).header("X-Request-Id", rid).contentType(ContentType.JSON)
                    .body(Map.of("baseVersions", Map.of("es", 99, "en", 99), "documents", Map.of(
                            "es", servicesDocument(token, "es", "x"), "en", servicesDocument(token, "en", "y"))))
                    .when().put("/api/v1/admin/content/services").then().statusCode(409);
            logs.awaitAccessLine("PUT /api/v1/admin/content/services 409", rid);
            assertTrue(invalidations(logs, rid).isEmpty(), logs.dump());
        }
    }

    /** Invalidador que falla a voluntad: la caché real sigue siendo la del padre. */
    private final class FlakyInvalidator extends PostgresSiteContentRepository {
        final AtomicBoolean failing = new AtomicBoolean(true);

        FlakyInvalidator() {
            super(dataSource, mapper, Duration.ofMinutes(5));
        }

        @Override
        public void invalidate(String kind, Language language) {
            if (failing.get()) {
                throw new IllegalStateException("cannot invalidate " + kind);
            }
            super.invalidate(kind, language);
        }
    }

    // T61 + hipótesis 7: si el invalidador falla se mantiene el WARN existente (System.Logger de application) y lleva el request_id
    @Test
    void whenTheInvalidatorFailsTheExistingWarnsStayAndCarryTheRequestId() throws SQLException {
        FlakyInvalidator flaky = new FlakyInvalidator();
        QuarkusMock.installMockForType(flaky, PostgresSiteContentRepository.class);
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        String saveRid = requestId();
        String rollbackRid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            save(token, saveRid, "Guardado warn", "Saved warn");
            given().header("Authorization", "Bearer " + token).header("X-Request-Id", rollbackRid).contentType(ContentType.JSON)
                    .body(Map.of("baseVersion", 2, "targetVersion", 1))
                    .when().post("/api/v1/admin/content/services/es/rollback").then().statusCode(200);
            List<ExtLogRecord> saveWarns = warns(logs, saveRid);
            List<ExtLogRecord> rollbackWarns = warns(logs, rollbackRid);
            assertEquals(2, saveWarns.size(), logs.dump());
            assertTrue(saveWarns.get(0).getFormattedMessage().startsWith("Saved services/es but could not invalidate its cache"));
            assertTrue(saveWarns.get(1).getFormattedMessage().startsWith("Saved services/en but could not invalidate its cache"));
            assertEquals(1, rollbackWarns.size(), logs.dump());
            assertTrue(rollbackWarns.get(0).getFormattedMessage().startsWith("Rolled back services/es but could not invalidate"));
            assertNotNull(rollbackWarns.get(0).getLoggerName());
            assertTrue(invalidations(logs, saveRid).isEmpty() && invalidations(logs, rollbackRid).isEmpty(), "sin INFO si falló");
        } finally {
            flaky.failing.set(false);
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    private static List<ExtLogRecord> warns(LogRecords logs, String rid) {
        return logs.await(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                && r.getFormattedMessage().contains("could not invalidate its cache") && rid.equals(LogRecords.mdc(r, "request_id")))
                .stream().toList();
    }
}
