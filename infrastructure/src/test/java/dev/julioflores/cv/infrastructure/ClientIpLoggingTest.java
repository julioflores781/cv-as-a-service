package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Matcher;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** La IP del cliente solo se registra en rutas admin y solo si es válida; en las públicas no aparece jamás. */
@QuarkusTest
class ClientIpLoggingTest {

    private static final String IP = "203.0.113.9";
    private static final String OTHER_IP = "203.0.113.10";
    private static final String LOGIN = "/api/v1/admin/auth/login";

    @TestHTTPResource
    URI base;

    @Inject
    AdminTestSupport support;

    private LogRecords logs;

    @BeforeEach
    void capture() {
        logs = LogRecords.attach();
        support.resetAccount();
    }

    @AfterEach
    void release() {
        logs.close();
    }

    private static String ipOf(String line) {
        Matcher m = AccessLogTest.LINE.matcher(line);
        assertTrue(m.matches(), line);
        return m.group(8);
    }

    private Response login(String cfIp, String xClientIp) {
        var request = given().contentType(ContentType.JSON).body("{\"password\":\"incorrecta incorrecta incorrecta\"}");
        if (cfIp != null) {
            request = request.header("CF-Connecting-IP", cfIp);
        }
        if (xClientIp != null) {
            request = request.header("X-Client-IP", xClientIp);
        }
        return request.when().post(LOGIN);
    }

    @Test
    void publicRequestsNeverLogTheIpEvenIfTheyCarryTheHeaders() { // T15
        String[] paths = {"/api/v1/profile", "/api/v1/resume", "/api/v1/experience/nope", "/api/v1/nope", "/api/v1/projects"};
        for (String path : paths) {
            Response r = given().header("CF-Connecting-IP", IP).header("X-Client-IP", OTHER_IP).when().get(path);
            assertEquals("-", ipOf(logs.awaitAccessLine("GET " + path + " " + r.statusCode(), r.header("X-Request-Id"))), path);
        }
        Response post = given().header("CF-Connecting-IP", IP).header("X-Client-IP", OTHER_IP).contentType(ContentType.JSON)
                .body("""
                        {"name":"Ada Ejemplo","email":"%s@example.com","message":"Hola, quiero hablar contigo de una vacante.","website":""}
                        """.formatted(UUID.randomUUID()))
                .when().post("/api/v1/contact/messages");
        assertEquals(201, post.statusCode());
        assertEquals("-", ipOf(logs.awaitAccessLine("POST /api/v1/contact/messages 201", post.header("X-Request-Id"))));

        String dump = logs.dump();
        assertFalse(dump.contains(IP), dump);
        assertFalse(dump.contains(OTHER_IP), dump);
        for (ExtLogRecord record : logs.all()) {
            assertFalse(record.getMdcCopy().containsKey("client_ip"), "client_ip en una ruta pública: " + record.getFormattedMessage());
        }
    }

    @Test
    void adminLoginsLogTheValidIpAndIgnoreInvalidOnes() { // T16
        Response valid = login(IP, null);
        assertEquals(401, valid.statusCode());
        assertEquals(IP, ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", valid.header("X-Request-Id"))));

        Response invalid = login("not-an-ip", null);
        assertEquals("-", ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", invalid.header("X-Request-Id"))));

        Response xClient = login(null, OTHER_IP);
        assertEquals(OTHER_IP, ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", xClient.header("X-Request-Id"))));
    }

    /** Variantes de ruta que JAX-RS enruta (o no) al recurso admin: el log debe decidir igual que el enrutado. */
    @ParameterizedTest(name = "{0} -> ip logged: {1}")
    @CsvSource({
        "/api/v1/admin/auth/session, true",
        "/api/v1/admin, true",
        "/api/v1/admin/, true",
        "/api/v1/admin/auth/session/, true",
        "//api/v1//admin/auth/session, true",
        "/api/v1/./admin/auth/session, true",
        "/api/v1/%61dmin/auth/session, true",
        "/api/v1/admin;x=1/auth/session, true",
        "/api/v1/admin/auth;x=1/session, true",
        "/api/v1/ADMIN/auth/session, false",
        "/api/v1/adminx/auth/session, false",
        "/api/v1/profile, false"
    })
    void theIpIsLoggedForExactlyTheRoutesTheAdminZoneOwns(String path, boolean admin) throws IOException { // extra fuera del plan (review ronda 1)
        RawResponses.Head head = RawResponses.get(base, path, RawResponses.header("X-Client-IP", "192.0.2.5"));
        String rid = head.headers().get("x-request-id");
        String line = logs.await(r -> LogRecords.ACCESS.equals(r.getLoggerName())
                && r.getFormattedMessage().contains(" rid=" + rid + " ")).get(0).getFormattedMessage();
        assertEquals(admin ? "192.0.2.5" : "-", ipOf(line), path + " -> " + line);
    }

    @Test
    void anInvalidCloudflareHeaderFallsBackToXClientIpAndTheCloudflareOneWinsWhenValid() { // extra fuera del plan
        Response fallback = login("garbage", OTHER_IP);
        assertEquals(OTHER_IP, ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", fallback.header("X-Request-Id"))));
        Response both = login(IP, OTHER_IP);
        assertEquals(IP, ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", both.header("X-Request-Id"))));
    }

    @Test
    void aBadIpNeverReachesAnyRecord() { // extra fuera del plan: ni un valor con saltos de línea ni un nombre de host
        Response r = login("evil.example.com", "203.0.113.9\tinjected");
        assertEquals("-", ipOf(logs.awaitAccessLine("POST " + LOGIN + " 401", r.header("X-Request-Id"))));
        assertFalse(logs.dump().contains("evil.example.com"));
        assertFalse(logs.dump().contains("injected"));
    }
}
