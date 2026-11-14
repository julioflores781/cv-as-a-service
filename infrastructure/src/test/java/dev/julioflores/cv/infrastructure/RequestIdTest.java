package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code X-Request-Id}: se acepta el del cliente solo si es válido; si no, se genera, y el inválido nunca se refleja. */
@QuarkusTest
class RequestIdTest {

    @TestHTTPResource
    URI base;

    private LogRecords logs;

    @BeforeEach
    void capture() {
        logs = LogRecords.attach();
    }

    @AfterEach
    void release() {
        logs.close();
    }

    private static String ridOf(String line) {
        Matcher m = AccessLogTest.LINE.matcher(line);
        assertTrue(m.matches(), line);
        return m.group(6);
    }

    @Test
    void withoutAnIdTheApiGeneratesOneOf32HexThatMatchesTheLog() { // T7
        Response response = given().when().get("/api/v1/profile");
        String id = response.header("X-Request-Id");
        assertTrue(id.matches("[0-9a-f]{32}"), id);
        assertEquals(id, ridOf(logs.awaitAccessLine("GET /api/v1/profile 200", id)));
    }

    @Test
    void aValidClientIdIsEchoedAndLogged() { // T8
        Response response = given().header("X-Request-Id", "abc-123_XYZ.789").when().get("/api/v1/profile");
        assertEquals("abc-123_XYZ.789", response.header("X-Request-Id"));
        assertEquals("abc-123_XYZ.789", ridOf(logs.awaitAccessLine("GET /api/v1/profile 200", "abc-123_XYZ.789")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "x2345678901234567890123456789012345678901234567890123456789012345", // 65 caracteres
        "an id with spaces", "<script>alert(1)</script>", "", "identificación-ñ-é", "abcdefg" // 7
    })
    void invalidIdsAreReplacedAndNeverReflected(String invalid) throws IOException { // T9
        RawResponses.Head head = RawResponses.get(base, "/api/v1/profile", RawResponses.header("X-Request-Id", invalid));
        assertEquals(200, head.status());
        String generated = head.headers().get("x-request-id");
        assertTrue(generated != null && generated.matches("[0-9a-f]{32}"), head.text());
        assertNotEquals(invalid, generated);
        assertEquals(generated, ridOf(logs.awaitAccessLine("GET /api/v1/profile 200", generated)));
        if (!invalid.isEmpty()) {
            assertFalse(head.text().contains(invalid), "reflected in the response: " + head.text());
            assertFalse(logs.dump().contains(invalid), "reflected in a log: " + logs.dump());
        }
    }

    @Test
    void anInvalidIdViaRestAssuredIsReplacedToo() { // T9 (cliente estándar)
        Response response = given().header("X-Request-Id", "<script>alert(1)</script>").when().get("/api/v1/profile");
        String id = response.header("X-Request-Id");
        assertTrue(id.matches("[0-9a-f]{32}"), id);
        logs.awaitAccessLine("GET /api/v1/profile 200", id);
        assertFalse(logs.dump().contains("<script>"));
    }

    @Test
    void twoRequestsWithoutAnIdGetDifferentIds() { // T10
        String first = given().when().get("/api/v1/profile").header("X-Request-Id");
        String second = given().when().get("/api/v1/profile").header("X-Request-Id");
        assertNotEquals(first, second);
    }

    @Test
    void theHeaderIsOnEveryKindOfResponse() throws IOException { // extra fuera del plan: 404, 405, 500 y rutas admin sin sesión
        for (Map.Entry<String, Integer> e : Map.of("/api/v1/nope", 404, "/test/boom", 500, "/api/v1/admin/auth/session", 401).entrySet()) {
            Response response = given().when().get(e.getKey());
            assertEquals(e.getValue(), response.statusCode(), e.getKey());
            assertTrue(response.header("X-Request-Id").matches("[0-9a-f]{32}"), e.getKey());
        }
        assertEquals(405, given().when().post("/api/v1/profile").statusCode());
        assertTrue(given().when().post("/api/v1/profile").header("X-Request-Id").matches("[0-9a-f]{32}"));
    }
}
