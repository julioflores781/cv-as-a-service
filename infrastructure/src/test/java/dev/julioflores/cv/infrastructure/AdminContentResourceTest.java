package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El contrato de contenido del panel (T96-T110) contra la app real, la BD real y el seed de ejemplo (Ada Ejemplo). */
@QuarkusTest
class AdminContentResourceTest {

    private static final String BASE = "/api/v1/admin/content/";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Inject
    AdminTestSupport support;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    private List<Object[]> backup;
    private String token;

    @BeforeEach
    void setUp() throws SQLException {
        support.resetAccount();
        token = support.token(1);
        backup = ContentVersions.snapshot(dataSource);
    }

    @AfterEach
    void restore() throws SQLException {
        ContentVersions.restore(dataSource, backup, cache);
    }

    private RequestSpecification auth() {
        return given().header("Authorization", "Bearer " + token);
    }

    private ValidatableResponse get(String kind) {
        return auth().when().get(BASE + kind).then();
    }

    /** El documento activo de un idioma, como lo devuelve el panel (mutable). */
    private Map<String, Object> document(String kind, String lang) {
        return get(kind).statusCode(200).extract().path("languages." + lang + ".content");
    }

    private Response put(String kind, int baseEs, int baseEn, String comment, Object es, Object en) {
        return auth().contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", baseEs, "en", baseEn), "comment", comment,
                        "documents", Map.of("es", es, "en", en)))
                .when().put(BASE + kind);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> document, String list, int index) {
        return ((List<Map<String, Object>>) document.get(list)).get(index);
    }

    /** Guarda un cambio de título del primer servicio en ambos idiomas, desde la versión activa que se pasa. */
    private Response saveServices(int baseEs, int baseEn, String comment, String titleEs, String titleEn) {
        Map<String, Object> es = document("services", "es");
        Map<String, Object> en = document("services", "en");
        item(es, "services", 0).put("title", titleEs);
        item(en, "services", 0).put("title", titleEn);
        return put("services", baseEs, baseEn, comment, es, en);
    }

    private int rowCount(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void sql(String statement) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(statement);
        }
    }

    // ── Lectura ──────────────────────────────────────────────────────────────────

    @Test
    void theSeedIsReadAsVersionOneInBothLanguagesWithItsSourceAndParity() { // T96
        get("services").statusCode(200)
                .header("Cache-Control", "no-store")
                .body("kind", equalTo("services"))
                .body("languages.es.version", equalTo(1))
                .body("languages.en.version", equalTo(1))
                .body("languages.es.source", equalTo("seed"))
                .body("languages.es.comment", nullValue())
                .body("languages.es.publishedAt", notNullValue())
                .body("languages.es.content.services[0].id", equalTo("servicio-ejemplo"))
                .body("languages.es.content.services[0].title", equalTo("Servicio de ejemplo"))
                .body("languages.en.content.services[0].title", equalTo("Sample service"))
                .body("parity.ok", equalTo(true))
                .body("parity.differences", hasSize(0));
        for (String kind : List.of("projects", "architecture")) {
            get(kind).statusCode(200).body("languages.es.version", equalTo(1)).body("parity.ok", equalTo(true));
        }
    }

    @Test
    void theStoredFormKeepsTheClientOfPublicProjectsAndNeverHasOneForAnonymous() {
        get("projects").statusCode(200)
                .body("languages.es.content.projects[0].client", equalTo("Ejemplo S.A."))
                .body("languages.es.content.projects[1].client", nullValue())
                .body("languages.es.content.projects[1].clientLabel", equalTo("prepaga de ejemplo"));
    }

    @Test
    void aKindWithNothingPublishedIsVersionZeroWithNullContentNotAnError() throws SQLException { // T97
        sql("DELETE FROM content_version WHERE kind = 'projects'");

        get("projects").statusCode(200)
                .body("languages.es.version", equalTo(0))
                .body("languages.es.content", nullValue())
                .body("languages.en.version", equalTo(0))
                .body("languages.en.content", nullValue())
                .body("parity.ok", equalTo(true));
    }

    @Test
    void aFirstSaveOfAnEmptyKindWithBaseZeroCreatesVersionOne() throws SQLException {
        Map<String, Object> es = document("projects", "es");
        Map<String, Object> en = document("projects", "en");
        sql("DELETE FROM content_version WHERE kind = 'projects'");

        put("projects", 0, 0, "Primer guardado", es, en).then().statusCode(200)
                .body("languages.es.version", equalTo(1))
                .body("languages.en.version", equalTo(1));
        put("projects", 0, 0, "Otra vez", es, en).then().statusCode(409);
    }

    // ── Guardado ─────────────────────────────────────────────────────────────────

    @Test
    void aValidSaveCreatesNewVersionsInBothLanguagesKeepsOneActiveAndLeavesThePastIntact() throws SQLException { // T98
        String before = rowCount("SELECT count(*) FROM content_version WHERE kind = 'services'") + "";

        saveServices(1, 1, "Cambio de precio", "Servicio editado", "Edited service").then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("kind", equalTo("services"))
                .body("languages.es.version", equalTo(2))
                .body("languages.en.version", equalTo(2))
                .body("languages.es.source", equalTo("admin"))
                .body("languages.es.comment", equalTo("Cambio de precio"))
                .body("languages.es.content.services[0].title", equalTo("Servicio editado"))
                .body("languages.en.content.services[0].title", equalTo("Edited service"))
                .body("parity.ok", equalTo(true));

        assertEquals(Integer.parseInt(before) + 2, rowCount("SELECT count(*) FROM content_version WHERE kind = 'services'"));
        for (String lang : List.of("es", "en")) {
            assertEquals(1, rowCount("SELECT count(*) FROM content_version WHERE kind = 'services' AND lang = '" + lang
                    + "' AND active"));
            assertEquals(2, rowCount("SELECT version FROM content_version WHERE kind = 'services' AND lang = '" + lang
                    + "' AND active"));
        }
        // La versión 1 sigue exactamente como estaba.
        auth().when().get(BASE + "services/es/versions/1").then().statusCode(200)
                .body("active", equalTo(false))
                .body("source", equalTo("seed"))
                .body("content.services[0].title", equalTo("Servicio de ejemplo"));
        get("services").body("languages.es.content.services[0].title", equalTo("Servicio editado"));
    }

    @Test
    void theStoredDocumentIsTheCanonicalFormWithoutNullsOrBlankOptionals() throws SQLException {
        Map<String, Object> es = document("services", "es");
        Map<String, Object> en = document("services", "en");
        item(es, "services", 1).put("price", null);
        item(en, "services", 1).put("price", null);

        put("services", 1, 1, "x", es, en).then().statusCode(200);

        get("services").body("languages.es.content.services[1].price", nullValue());
        assertEquals(0, rowCount("SELECT count(*) FROM content_version WHERE kind = 'services' AND version = 2 "
                + "AND content::text LIKE '%null%'"));
    }

    @Test
    void staleBaseVersionsAreAConflictAndTheDatabaseDoesNotChange() throws SQLException { // T99
        saveServices(1, 1, "primero", "A", "A").then().statusCode(200);
        int rows = rowCount("SELECT count(*) FROM content_version");

        saveServices(1, 1, "segundo", "B", "B").then().statusCode(409)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTENT_VERSION_CONFLICT"))
                .body("status", equalTo(409))
                .body("detail", containsString("«es»"))
                .body("detail", containsString("2"));
        saveServices(2, 1, "tercero", "C", "C").then().statusCode(409)
                .body("detail", containsString("«en»"));

        assertEquals(rows, rowCount("SELECT count(*) FROM content_version"));
        get("services").body("languages.es.content.services[0].title", equalTo("A"));
    }

    @Test
    void theConflictMessageFollowsTheRequestedLanguage() {
        saveServices(1, 1, "primero", "A", "A").then().statusCode(200);

        Response en = auth().queryParam("lang", "en").contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents",
                        Map.of("es", document("services", "es"), "en", document("services", "en"))))
                .when().put(BASE + "services");

        en.then().statusCode(409).body("title", equalTo("Version conflict"))
                .body("detail", containsString("'es'"));
    }

    @Test
    void twoSimultaneousSavesWithTheSameBaseGiveOneWinnerAndOneConflict() throws Exception { // T100 (extremo a extremo)
        // El solapamiento no se puede forzar desde HTTP; lo que fuerza y demuestra la serialización por el candado asesor es
        // PostgresContentVersionRepositoryTest#publishWaitsForTheAdvisoryLock.... Aquí se comprueba el resultado visible: el
        // reparto 200/409 y la ausencia de versiones duplicadas, sea cual sea el orden de llegada.
        Map<String, Object> es1 = document("services", "es");
        Map<String, Object> en1 = document("services", "en");
        item(es1, "services", 0).put("title", "Hilo 1");
        Map<String, Object> es2 = document("services", "es");
        Map<String, Object> en2 = document("services", "en");
        item(es2, "services", 0).put("title", "Hilo 2");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> {
                go.await();
                return put("services", 1, 1, "uno", es1, en1).statusCode();
            });
            Future<Integer> second = pool.submit(() -> {
                go.await();
                return put("services", 1, 1, "dos", es2, en2).statusCode();
            });
            go.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            statuses.sort(Integer::compare);
            assertEquals(List.of(200, 409), statuses);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, rowCount("SELECT count(*) FROM content_version WHERE kind = 'services' AND lang = 'es'"));
        assertEquals(2, rowCount("SELECT count(*) FROM content_version WHERE kind = 'services' AND lang = 'en'"));
        assertEquals(0, rowCount("SELECT count(*) FROM (SELECT version FROM content_version WHERE kind = 'services' "
                + "GROUP BY lang, version HAVING count(*) > 1) d"));
    }

    @Test
    void anAnonymousProjectWithAClientIsRejectedAndNothingIsStored() throws SQLException { // T102
        Map<String, Object> es = document("projects", "es");
        Map<String, Object> en = document("projects", "en");
        item(es, "projects", 1).put("client", "Nombre Real S.A.");
        int rows = rowCount("SELECT count(*) FROM content_version");

        put("projects", 1, 1, "x", es, en).then().statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTENT_INVALID"))
                .body("errors", hasSize(1))
                .body("errors[0].path", equalTo("es.projects[1].client"))
                .body("errors[0].code", equalTo("ANONYMOUS_CLIENT_FORBIDDEN"))
                .body("detail", containsString("1"));

        assertEquals(rows, rowCount("SELECT count(*) FROM content_version"));
        assertEquals(0, rowCount("SELECT count(*) FROM content_version WHERE content::text LIKE '%Nombre Real%'"));
    }

    @Test
    void anUnknownFieldInTheDocumentsIsRejectedNotIgnored() { // T103
        Map<String, Object> es = document("services", "es");
        item(es, "services", 0).put("descuento", 10);

        put("services", 1, 1, "x", es, document("services", "en")).then().statusCode(422)
                .body("code", equalTo("CONTENT_INVALID"))
                .body("errors[0].path", equalTo("es.services[0].descuento"))
                .body("errors[0].code", equalTo("UNKNOWN_FIELD"));
    }

    @Test
    void everyViolationOfBothLanguagesComesInOneResponse() {
        Map<String, Object> es = document("projects", "es");
        Map<String, Object> en = document("projects", "en");
        item(es, "projects", 0).put("id", "ID Con Espacios");
        item(en, "projects", 0).put("links", List.of(Map.of("label", "x", "url", "http://inseguro.example")));

        put("projects", 1, 1, "x", es, en).then().statusCode(422)
                .body("errors.path", hasItem("es.projects[0].id"))
                .body("errors.code", hasItem("INVALID_ID"))
                .body("errors.path", hasItem("en.projects[0].links[0].url"))
                .body("errors.code", hasItem("INVALID_URL"));
    }

    @Test
    void esAndEnWithADifferentStructureAreRejectedWithTheirDifferences() { // T104
        Map<String, Object> es = document("services", "es");
        Map<String, Object> en = document("services", "en");
        ((List<?>) en.get("services")).remove(1);
        item(es, "services", 0).put("price", Map.of("from", 1, "currency", "EUR"));

        put("services", 1, 1, "x", es, en).then().statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTENT_PARITY_BROKEN"))
                .body("errors.path", hasItem("services[0].price"))
                .body("errors.path", hasItem("services[1]"))
                .body("errors[0].code", equalTo("PARITY_MISMATCH"));
    }

    @Test
    void aBodyAboveTheContentLimitIsTooLarge() throws SQLException { // T105
        int rows = rowCount("SELECT count(*) FROM content_version");
        String body = "{\"baseVersions\":{\"es\":1,\"en\":1},\"comment\":\"" + "x".repeat(100_000) + "\",\"documents\":{}}";

        auth().contentType(ContentType.JSON).body(body).when().put(BASE + "services").then()
                .statusCode(413)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTENT_TOO_LARGE"))
                .body("detail", containsString("96"));

        assertEquals(rows, rowCount("SELECT count(*) FROM content_version"));
    }

    @Test
    void malformedAndIncompleteBodiesAreBadRequestsWithProblemJson() {
        for (String body : List.of("", "no json", "null", "[]", "{}", "{\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1},\"documents\":{}}", "{\"baseVersions\":{\"es\":-1,\"en\":1},\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1,\"en\":1}}", "{\"baseVersions\":{\"es\":1,\"en\":1},\"documents\":{},\"extra\":1}",
                "{\"baseVersions\":{\"es\":\"uno\",\"en\":1},\"documents\":{}}",
                // Tipos equivocados que Jackson coaccionaría en silencio: un 400, no un guardado con otro valor.
                "{\"baseVersions\":{\"es\":\"1\",\"en\":1},\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1,\"en\":true},\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1.5,\"en\":1},\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1,\"en\":1},\"comment\":5,\"documents\":{}}",
                "{\"baseVersions\":{\"es\":1,\"en\":1},\"comment\":true,\"documents\":{}}")) {
            auth().contentType(ContentType.JSON).body(body).when().put(BASE + "services").then()
                    .statusCode(400).contentType(PROBLEM_JSON).body("code", equalTo("BAD_REQUEST"));
        }
    }

    @Test
    void aTooLongCommentIsReportedAsAViolationOnTheComment() {
        put("services", 1, 1, "c".repeat(201), document("services", "es"), document("services", "en")).then()
                .statusCode(422).body("errors[0].path", equalTo("comment")).body("errors[0].code", equalTo("TOO_LONG"));
    }

    // ── Historial ────────────────────────────────────────────────────────────────

    @Test
    void historyIsDescendingWithTheRightActiveFlagAndTheLimitApplied() { // T107
        saveServices(1, 1, "dos", "V2", "V2").then().statusCode(200);
        saveServices(2, 2, "tres", "V3", "V3").then().statusCode(200);

        auth().when().get(BASE + "services/es/versions").then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("kind", equalTo("services"))
                .body("lang", equalTo("es"))
                .body("versions.version", equalTo(List.of(3, 2, 1)))
                .body("versions.active", equalTo(List.of(true, false, false)))
                .body("versions.source", equalTo(List.of("admin", "admin", "seed")))
                .body("versions.comment", equalTo(java.util.Arrays.asList("tres", "dos", null)));
        auth().queryParam("limit", 2).when().get(BASE + "services/es/versions").then().statusCode(200)
                .body("versions.version", equalTo(List.of(3, 2)));
        auth().queryParam("limit", 0).when().get(BASE + "services/es/versions").then().statusCode(200)
                .body("versions", hasSize(1));
    }

    @Test
    void aSnapshotIsReadByNumberAndAMissingOneIsNotFound() { // T108
        auth().when().get(BASE + "services/en/versions/1").then().statusCode(200)
                .body("kind", equalTo("services"))
                .body("lang", equalTo("en"))
                .body("version", equalTo(1))
                .body("active", equalTo(true))
                .body("content.services[0].title", equalTo("Sample service"));
        auth().when().get(BASE + "services/en/versions/9").then().statusCode(404)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTENT_VERSION_NOT_FOUND"))
                .body("detail", containsString("9"));
    }

    @Test
    void unknownKindsLanguagesAndVersionsAreGenericNotFound() {
        get("resume").statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("NOT_FOUND"));
        get("Services").statusCode(404);
        auth().when().get(BASE + "services/fr/versions").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        auth().when().get(BASE + "nope/es/versions/1").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        auth().when().get(BASE + "services/es/versions/abc").then().statusCode(404);
        auth().contentType(ContentType.JSON).body(Map.of("baseVersion", 1, "targetVersion", 1)).when()
                .post(BASE + "nope/es/rollback").then().statusCode(404);
        put("resume", 1, 1, "x", Map.of(), Map.of()).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // ── Rollback ─────────────────────────────────────────────────────────────────

    private Response rollback(String lang, int base, int target) {
        return auth().contentType(ContentType.JSON).body(Map.of("baseVersion", base, "targetVersion", target))
                .when().post(BASE + "services/" + lang + "/rollback");
    }

    @Test
    void anOldVersionBecomesActiveThePublicReadSeesItAndALaterSaveTakesTheMaximumPlusOne() { // T109
        saveServices(1, 1, "dos", "V2", "V2").then().statusCode(200);
        saveServices(2, 2, "tres", "V3", "V3").then().statusCode(200);

        rollback("es", 3, 1).then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("languages.es.version", equalTo(1))
                .body("languages.es.content.services[0].title", equalTo("Servicio de ejemplo"))
                .body("languages.en.version", equalTo(3))
                .body("parity.ok", equalTo(true));
        given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio de ejemplo"));
        auth().when().get(BASE + "services/es/versions").then()
                .body("versions.version", equalTo(List.of(3, 2, 1)))
                .body("versions.active", equalTo(List.of(false, false, true)));

        saveServices(1, 3, "cuatro", "V4", "V4").then().statusCode(200)
                .body("languages.es.version", equalTo(4))
                .body("languages.en.version", equalTo(4));
        auth().when().get(BASE + "services/es/versions").then().body("versions.version", equalTo(List.of(4, 3, 2, 1)));
    }

    @Test
    void aRollbackWithAStaleBaseAMissingTargetOrAnInvalidSnapshotChangesNothing() throws SQLException {
        saveServices(1, 1, "dos", "V2", "V2").then().statusCode(200);

        rollback("es", 1, 1).then().statusCode(409).body("code", equalTo("CONTENT_VERSION_CONFLICT"));
        rollback("es", 2, 7).then().statusCode(404).body("code", equalTo("CONTENT_VERSION_NOT_FOUND"));
        // Una versión vieja que hoy no se podría guardar: precio negativo escrito a mano.
        sql("INSERT INTO content_version (kind, lang, version, content, active) VALUES ('services', 'es', 9, "
                + "'{\"services\":[{\"id\":\"a\",\"title\":\"T\",\"summary\":\"S\",\"price\":{\"from\":-5,\"currency\":\"EUR\"}}]}', false)");
        rollback("es", 2, 9).then().statusCode(422)
                .body("code", equalTo("CONTENT_INVALID"))
                .body("errors[0].path", equalTo("es.services[0].price.from"))
                .body("errors[0].code", equalTo("INVALID_VALUE"));

        assertEquals(2, rowCount("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'es' AND active"));
    }

    @Test
    void rollbackBodiesAreStrict() {
        for (String body : List.of("", "{}", "{\"baseVersion\":1}", "{\"baseVersion\":1,\"targetVersion\":0}",
                "{\"baseVersion\":1,\"targetVersion\":1,\"extra\":true}", "[]",
                "{\"baseVersion\":\"1\",\"targetVersion\":1}", "{\"baseVersion\":1,\"targetVersion\":\"1\"}",
                "{\"baseVersion\":1,\"targetVersion\":1.5}")) {
            auth().contentType(ContentType.JSON).body(body).when().post(BASE + "services/es/rollback").then()
                    .statusCode(400).body("code", equalTo("BAD_REQUEST"));
        }
    }

    @Test
    void aRollbackLeavesTheSameStateAsMakeSeedRollback() throws Exception { // T110
        Path script = Path.of("").toAbsolutePath().getParent().resolve("scripts/make-seed.py");
        assumeTrue(Files.exists(script) && pythonAvailable(), "hace falta python3 y scripts/make-seed.py");

        saveServices(1, 1, "dos", "V2", "V2").then().statusCode(200);
        List<Object[]> afterSave = ContentVersions.snapshot(dataSource);

        rollback("es", 2, 1).then().statusCode(200);
        String panel = state();

        ContentVersions.restore(dataSource, afterSave, cache);
        Process python = new ProcessBuilder("python3", script.toString(), "--rollback", "es", "--kind", "services")
                .redirectErrorStream(true).start();
        String generated = new String(python.getInputStream().readAllBytes());
        assertEquals(0, python.waitFor(), generated);
        sql(generated);
        cache.invalidate("services", dev.julioflores.cv.domain.Language.ES);

        assertEquals(panel, state());
        get("services").body("languages.es.version", equalTo(1)).body("languages.en.version", equalTo(2));
    }

    private String state() {
        JsonPath es = auth().when().get(BASE + "services/es/versions").jsonPath();
        JsonPath en = auth().when().get(BASE + "services/en/versions").jsonPath();
        return es.getList("versions.version") + "" + es.getList("versions.active")
                + en.getList("versions.version") + en.getList("versions.active");
    }

    private static boolean pythonAvailable() {
        try {
            return new ProcessBuilder("python3", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    // ── Seguridad ────────────────────────────────────────────────────────────────

    @Test
    void everyContentRouteRequiresASession() {
        given().when().get(BASE + "services").then().statusCode(401).body("code", equalTo("ADMIN_UNAUTHORIZED"));
        given().contentType(ContentType.JSON).body("{}").when().put(BASE + "services").then().statusCode(401);
        given().when().get(BASE + "services/es/versions").then().statusCode(401);
        given().when().get(BASE + "services/es/versions/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").when().post(BASE + "services/es/rollback").then().statusCode(401);
    }

    @Test
    void aRevokedSessionCannotReadOrWriteContent() {
        support.execute("UPDATE admin_account SET session_version = session_version + 1");

        get("services").statusCode(401);
        put("services", 1, 1, "x", Map.of(), Map.of()).then().statusCode(401).body("code", equalTo("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void anImageKeyThatDoesNotExistInTheStorageIsAcceptedBecauseSavingNeverAsksR2() { // T141 (R-7)
        // Comportamiento documentado: la validación solo mira la forma de la clave. Una clave inexistente da una imagen rota
        // en la web, no un error de guardado, y guardar contenido no depende de que el almacenamiento esté arriba
        // (aquí R2 ni siquiera está configurado).
        String key = "site/projects/00000000-0000-4000-8000-00000000dead.png";
        Map<String, Object> es = document("projects", "es");
        Map<String, Object> en = document("projects", "en");
        item(es, "projects", 0).put("images", List.of(Map.of("key", key, "alt", "No existe", "width", 10, "height", 10)));
        item(en, "projects", 0).put("images", List.of(Map.of("key", key, "alt", "Missing", "width", 10, "height", 10)));

        put("projects", 1, 1, "imagen que no existe", es, en).then().statusCode(200)
                .body("languages.es.content.projects[0].images[0].key", equalTo(key));
        given().when().get("/api/v1/projects").then().statusCode(200).body("[0].images[0].url", containsString(key));
    }

    @Test
    void imagesOnAnAnonymousProjectBadKeysAndUnevenLanguagesAreRejectedWithTheirPaths() {
        Map<String, Object> es = document("projects", "es");
        Map<String, Object> en = document("projects", "en");
        String good = "site/projects/00000000-0000-4000-8000-000000000001.webp";
        item(es, "projects", 1).put("images", List.of(Map.of("key", good, "alt", "x", "width", 1, "height", 1)));
        item(es, "projects", 0).put("images", List.of(Map.of("key", "../x.png", "alt", "x", "width", 1, "height", 1)));
        put("projects", 1, 1, "mal", es, en).then().statusCode(422).body("code", equalTo("CONTENT_INVALID"))
                .body("errors.path", hasItem("es.projects[1].images"))
                .body("errors.code", hasItem("IMAGES_NOT_ALLOWED_FOR_PROJECT"))
                .body("errors.path", hasItem("es.projects[0].images[0].key"))
                .body("errors.code", hasItem("IMAGE_KEY_INVALID"));

        Map<String, Object> es2 = document("projects", "es");
        Map<String, Object> en2 = document("projects", "en");
        item(es2, "projects", 0).put("images", List.of(Map.of("key", good, "alt", "x", "width", 1, "height", 1)));
        item(en2, "projects", 0).put("images", List.of(Map.of("key", good, "alt", "y", "width", 2, "height", 1)));
        put("projects", 1, 1, "paridad", es2, en2).then().statusCode(422).body("code", equalTo("CONTENT_PARITY_BROKEN"))
                .body("errors.path", hasItem("projects[0].images[0].width"));
    }
}
