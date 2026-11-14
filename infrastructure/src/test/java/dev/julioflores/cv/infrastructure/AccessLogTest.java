package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** La línea de access: método, ruta sin query, estado, duración, bytes, {@code rid}, {@code err} e {@code ip}. */
@QuarkusTest
class AccessLogTest {

    /** {@code GET /api/v1/profile 200 12ms 1534b rid=… err=- ip=-}. */
    static final Pattern LINE =
            Pattern.compile("^([A-Z]+) (\\S+) (\\d{3}) (\\d+)ms (\\d+)b rid=(\\S+) err=(\\S+) ip=(\\S+)$");

    private static final String HL7 = String.join("\r",
            "MSH|^~\\&|HIS|HOSPITAL|LAB|HOSPITAL|20260101120000||ADT^A01^ADT_A01|MSG1|P|2.5.1",
            "EVN|A01|20260101120000",
            "PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M",
            "PV1|1|I|UCI^1^A");

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

    private static Matcher parse(String line) {
        Matcher m = LINE.matcher(line);
        assertTrue(m.matches(), "access line does not follow the pattern: " + line);
        return m;
    }

    @Test
    void oneAccessLineWithMethodPathStatusDurationAndBytesAndTheResponseRid() { // T1
        Response response = given().when().get("/api/v1/profile");
        String rid = response.header("X-Request-Id");
        String line = logs.awaitAccessLine("GET /api/v1/profile 200", rid);
        Matcher m = parse(line);
        assertEquals(rid, m.group(6));
        assertTrue(Long.parseLong(m.group(5)) > 0, "bytes: " + line);
        assertEquals(1, logs.accessLines().stream().filter(l -> l.contains("rid=" + rid + " ")).count(), "exactly one line");
    }

    static Stream<Arguments> publicEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/profile", 200),
                Arguments.of("GET", "/api/v1/impact", 200),
                Arguments.of("GET", "/api/v1/experience", 200),
                Arguments.of("GET", "/api/v1/experience/{first}", 200),
                Arguments.of("GET", "/api/v1/skills", 200),
                Arguments.of("GET", "/api/v1/education", 200),
                Arguments.of("GET", "/api/v1/contact", 200),
                Arguments.of("GET", "/api/v1/resume", 200),
                Arguments.of("GET", "/api/v1/services", 200),
                Arguments.of("GET", "/api/v1/projects", 200),
                Arguments.of("GET", "/api/v1/architecture", 200),
                Arguments.of("POST", "/api/v1/hl7/translate", 200),
                Arguments.of("POST", "/api/v1/contact/messages", 201));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("publicEndpoints")
    void everyPublicEndpointLeavesItsLine(String method, String path, int expected) { // T2
        String actualPath = path.replace("{first}", firstExperienceId());
        var request = given().header("CF-Connecting-IP", "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode()) % 200));
        if (method.equals("POST") && path.endsWith("hl7/translate")) {
            request = request.contentType("text/plain; charset=UTF-8").body(HL7.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST")) {
            request = request.contentType(ContentType.JSON).body("""
                    {"name":"Ada Ejemplo","email":"%s@example.com","message":"Hola, quiero hablar contigo de una vacante.","website":""}
                    """.formatted(UUID.randomUUID()));
        }
        Response response = request.when().request(method, actualPath);
        assertEquals(expected, response.statusCode());
        Matcher m = parse(logs.awaitAccessLine(method + " " + actualPath + " " + expected, response.header("X-Request-Id")));
        assertEquals(method, m.group(1));
        assertEquals(actualPath, m.group(2));
        assertEquals(String.valueOf(expected), m.group(3));
    }

    private static String firstExperienceId() {
        return given().when().get("/api/v1/experience").path("[0].id");
    }

    @Test
    void anUnknownRouteLeavesA404Line() { // T3
        Response response = given().when().get("/api/v1/does/not/exist");
        assertEquals(404, response.statusCode());
        Matcher m = parse(logs.awaitAccessLine("GET /api/v1/does/not/exist 404", response.header("X-Request-Id")));
        assertEquals("NOT_FOUND", m.group(7));
    }

    @Test
    void anOversizedDeclaredBodyIsLoggedAs413AndAnswersWithTheRid() throws IOException { // T4
        RawResponses.Head head = RawResponses.postDeclaringLength(base, "/api/v1/hl7/translate", "text/plain", 200_000);
        assertEquals(413, head.status());
        String rid = head.headers().get("x-request-id");
        assertTrue(rid != null && rid.matches("[0-9a-f]{32}"), "X-Request-Id on the early 413: " + head.text());
        Matcher m = parse(logs.awaitAccessLine("POST /api/v1/hl7/translate 413", rid));
        assertEquals("-", m.group(7));
        assertEquals("-", m.group(8));
    }

    @Test
    void healthSwaggerAndOpenApiLeaveNoAccessLine() { // T5
        given().when().get("/q/health/ready").then().statusCode(200);
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/swagger/").then().statusCode(200);
        given().when().get("/openapi.json").then().statusCode(200);
        // Las líneas salen al cerrar cada respuesta, en orden: cuando sale la de este centinela, las anteriores ya habrían salido.
        String sentinel = given().when().get("/api/v1/profile").header("X-Request-Id");
        logs.awaitAccessLine("GET /api/v1/profile 200", sentinel);
        List<String> lines = logs.accessLines();
        assertFalse(lines.stream().anyMatch(l -> l.contains("/q/health") || l.contains("/swagger") || l.contains("/openapi")), lines.toString());
        assertEquals(1, lines.size(), lines.toString());
    }

    @Test
    void theQueryStringNeverReachesAnyRecord() { // T6
        Response response = given().when().get("/api/v1/profile?lang=en&token=CANARY-Q");
        String rid = response.header("X-Request-Id");
        Matcher m = parse(logs.awaitAccessLine("GET /api/v1/profile 200", rid));
        assertEquals("/api/v1/profile", m.group(2));
        String dump = logs.dump();
        assertFalse(dump.contains("CANARY-Q"), dump);
        assertFalse(dump.contains("token="), dump);
        assertFalse(dump.contains("lang="), dump);
    }

    @Test
    void aProblemResponseCarriesItsCodeInTheLine() { // T14
        Response response = given().when().get("/api/v1/experience/nope");
        Matcher m = parse(logs.awaitAccessLine("GET /api/v1/experience/nope 404", response.header("X-Request-Id")));
        assertEquals("EXPERIENCE_NOT_FOUND", m.group(7));
    }
}
