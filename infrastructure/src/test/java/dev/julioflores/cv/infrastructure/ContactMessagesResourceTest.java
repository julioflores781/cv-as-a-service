package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
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
}
