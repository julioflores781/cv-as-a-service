package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.jboss.logmanager.ExtLogRecord;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContactMessagesResourceTest {

    private static final String PATH = "/api/v1/contact/messages";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BODY = "Hola, me gustaría hablar contigo sobre una vacante de backend.";

    @Inject
    DataSource dataSource;

    /** Cada test usa su propia IP (por cabecera de Cloudflare) para no compartir el cupo de envíos. */
    private static String freshIp() {
        return "203.0.113." + (Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 250 + 1) + "-" + UUID.randomUUID();
    }

    private static String json(String name, String email, String message, String website) {
        return "{\"name\":%s,\"email\":%s,\"message\":%s,\"website\":%s}"
                .formatted(quote(name), quote(email), quote(message), quote(website));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static ValidatableResponse post(String ip, String body) {
        return given().header("CF-Connecting-IP", ip).contentType(ContentType.JSON).body(body)
                .when().post(PATH).then();
    }

    private int countByEmail(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT count(*) FROM contact_message WHERE email = ?")) {
            s.setString(1, email);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void validMessageIsStoredAndAnswers201WithoutBody() throws SQLException {
        String email = UUID.randomUUID() + "@example.com";
        post(freshIp(), json("Ada Ejemplo", email, BODY, "")).statusCode(201).body(emptyOrNullString());
        assertEquals(1, countByEmail(email));
    }

    @Test
    void theIpIsStoredOnlyAsAHashNeverInClear() throws SQLException {
        String ip = freshIp();
        String email = UUID.randomUUID() + "@example.com";
        post(ip, json("Ada", email, BODY, null)).statusCode(201);
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT ip_hash FROM contact_message WHERE email = ?")) {
            s.setString(1, email);
            try (ResultSet rs = s.executeQuery()) {
                assertTrue(rs.next());
                String stored = rs.getString(1);
                assertEquals(64, stored.length());
                assertTrue(stored.matches("[0-9a-f]{64}"));
                assertFalse(stored.contains(ip));
            }
        }
    }

    @Test
    void filledHoneypotLooksSuccessfulButStoresNothing() throws SQLException {
        String email = UUID.randomUUID() + "@example.com";
        post(freshIp(), json("Bot", email, BODY, "http://spam.example")).statusCode(201);
        assertEquals(0, countByEmail(email));
    }

    @Test
    void invalidFieldAnswers400WithStableCodeAndTheFieldInTheDetail() {
        post(freshIp(), json("Ada", "no-es-un-correo", BODY, null)).statusCode(400)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTACT_INVALID"))
                .body("title", equalTo("Mensaje no válido"))
                .body("detail", containsString("«email»"));
    }

    @Test
    void errorTextsFollowTheRequestedLanguage() {
        given().header("CF-Connecting-IP", freshIp()).contentType(ContentType.JSON)
                .body(json("", "ada@example.com", BODY, null))
                .when().post(PATH + "?lang=en").then().statusCode(400)
                .body("code", equalTo("CONTACT_INVALID"))
                .body("title", equalTo("Invalid message"))
                .body("detail", containsString("\"name\""));
    }

    @Test
    void emptyBodyIsAnInvalidMessageNotAServerError() {
        given().header("CF-Connecting-IP", freshIp()).contentType(ContentType.JSON)
                .when().post(PATH).then().statusCode(400).body("code", equalTo("CONTACT_INVALID"));
    }

    @Test
    void malformedJsonIsABadRequest() {
        post(freshIp(), "{no es json").statusCode(400).contentType(PROBLEM_JSON).body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void sendersAreLimitedIndependentlyAndTheLimitAnswers429() {
        String busy = freshIp();
        for (int i = 0; i < 3; i++) {
            post(busy, json("Ada", "ada@example.com", BODY, null)).statusCode(201);
        }
        post(busy, json("Ada", "ada@example.com", BODY, null)).statusCode(429)
                .contentType(PROBLEM_JSON)
                .body("code", equalTo("CONTACT_RATE_LIMITED"));
        post(freshIp(), json("Ada", "ada@example.com", BODY, null)).statusCode(201);
    }

    @Test
    void withoutTheCloudflareHeaderTheSocketAddressIsUsed() {
        given().contentType(ContentType.JSON).body(json("Ada", "ada-socket@example.com", BODY, null))
                .when().post(PATH).then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(201), equalTo(429)));
    }

    private static final String STORED_LOGGER = "dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresContactMessageRepository";

    private static String requestId() {
        return "ct-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static ValidatableResponse postWithRid(String ip, String rid, String body) {
        return given().header("CF-Connecting-IP", ip).header("X-Request-Id", rid).contentType(ContentType.JSON).body(body)
                .when().post(PATH).then();
    }

    /** Líneas «stored» de esta petición: se espera a la de access (sale al cerrar la respuesta) y después se mira, sin sleeps. */
    private static List<ExtLogRecord> storedFor(LogRecords logs, String rid, String accessPrefix) {
        logs.awaitAccessLine(accessPrefix, rid);
        return logs.byLogger(STORED_LOGGER).stream().filter(r -> rid.equals(LogRecords.mdc(r, "request_id"))).toList();
    }

    private long idOf(String email) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT id FROM contact_message WHERE email = ?")) {
            s.setString(1, email);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // T56
    @Test
    void aValidMessageLeavesOneStoredLineWithTheRequestIdAndNoPersonalData() throws SQLException {
        String email = UUID.randomUUID() + "@example.com";
        String ip = freshIp();
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            postWithRid(ip, rid, json("Canary Nombre", email, "Canary mensaje largo de prueba", "")).statusCode(201);
            List<ExtLogRecord> stored = storedFor(logs, rid, "POST /api/v1/contact/messages 201");
            assertEquals(1, stored.size(), logs.dump());
            ExtLogRecord line = stored.get(0);
            assertEquals(Level.INFO.intValue(), line.getLevel().intValue());
            assertEquals("Contact message stored id=" + idOf(email), line.getFormattedMessage());
            String dump = logs.dump();
            for (String leak : new String[] {"Canary Nombre", email, "Canary mensaje", ip, "203.0.113."}) {
                assertFalse(dump.contains(leak), leak + " in " + dump);
            }
        }
    }

    // T57
    @Test
    void theHoneypotAnswers201AndLeavesNeitherAStoredLineNorATelegramOne() throws SQLException {
        String email = UUID.randomUUID() + "@example.com";
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            postWithRid(freshIp(), rid, json("Bot", email, BODY, "http://spam.example")).statusCode(201);
            assertTrue(storedFor(logs, rid, "POST /api/v1/contact/messages 201").isEmpty(), logs.dump());
            assertFalse(logs.dump().contains("stored id="), logs.dump());
            assertFalse(logs.dump().contains("Telegram"), logs.dump());
            assertEquals(0, countByEmail(email));
        }
    }

    // T58
    @Test
    void anExhaustedQuotaAnswers429WithTheCodeInTheAccessLineAndNoStoredLine() {
        String busy = freshIp();
        for (int i = 0; i < 3; i++) {
            post(busy, json("Ada", "ada@example.com", BODY, null)).statusCode(201);
        }
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            postWithRid(busy, rid, json("Ada", "ada@example.com", BODY, null)).statusCode(429);
            String line = logs.awaitAccessLine("POST /api/v1/contact/messages 429", rid);
            assertTrue(line.contains(" err=CONTACT_RATE_LIMITED "), line);
            assertTrue(storedFor(logs, rid, "POST /api/v1/contact/messages 429").isEmpty(), logs.dump());
        }
    }

    // T59
    @Test
    void anInvalidMessageAnswers400AndLeavesNoStoredLine() {
        String rid = requestId();
        try (LogRecords logs = LogRecords.attach()) {
            postWithRid(freshIp(), rid, json("Ada", "no-es-un-correo", BODY, null)).statusCode(400);
            String line = logs.awaitAccessLine("POST /api/v1/contact/messages 400", rid);
            assertTrue(line.contains(" err=CONTACT_INVALID "), line);
            assertTrue(storedFor(logs, rid, "POST /api/v1/contact/messages 400").isEmpty(), logs.dump());
        }
    }

    @TestHTTPResource(PATH)
    URI contact;

    @Test
    void bodiesOverTheFixedPublicLimitAreCutWhetherTheyDeclareTheirLengthOrAreChunked() { // T137 (límite global ampliado para las imágenes)
        // JSON válido: si el límite no cortara, el cuerpo se leería entero y daría un 400 de validación, no un 413.
        byte[] big = json("Ada", "ada@example.com", "x".repeat(300 * 1024), null).getBytes(StandardCharsets.UTF_8);
        byte[] huge = json("Ada", "ada@example.com", "x".repeat(4 * 1024 * 1024), null).getBytes(StandardCharsets.UTF_8);
        assertEquals(413, RawHttp.postWithLength(contact, "application/json", big));
        assertEquals(413, RawHttp.postChunked(contact, "application/json", big));
        assertEquals(413, RawHttp.postWithLength(contact, "application/json", huge));
        assertEquals(413, RawHttp.postChunked(contact, "application/json", huge));
    }
}
