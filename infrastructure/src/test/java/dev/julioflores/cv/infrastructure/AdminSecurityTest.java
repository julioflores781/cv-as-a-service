package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Method;
import io.restassured.path.json.JsonPath;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminSecurityTest {

    private static final String SESSION = "/api/v1/admin/auth/session";
    private static final String TEST_SECRET = "test-only-secret-0123456789-abcdefghijklmnopqrstuv";
    private static final String OTHER_SECRET = "otro-secreto-distinto-0123456789-abcdefghijklmnop";

    @Inject
    AdminTestSupport support;

    @BeforeEach
    void account() {
        support.resetAccount();
    }

    private static String forge(String secret, String issuer, String audience, int sv, Instant expiresAt) {
        return Jwt.claims().issuer(issuer).audience(audience).subject("admin").claim("sv", sv)
                .issuedAt(Instant.now().minusSeconds(60)).expiresAt(expiresAt)
                .jws().algorithm(SignatureAlgorithm.HS256)
                .sign(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    }

    private static void assertUnauthorized(io.restassured.response.Response response) {
        response.then().statusCode(401)
                .header("Content-Type", containsString("application/problem+json"))
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("code", equalTo("ADMIN_UNAUTHORIZED"));
    }

    @Test
    void everyAdminRouteInOpenApiExceptLoginRejectsARequestWithoutToken() { // T82
        JsonPath doc = given().header("Accept", "application/json").when().get("/openapi.json")
                .then().statusCode(200).extract().jsonPath();
        Map<String, Map<String, Object>> paths = doc.getMap("paths");
        List<String> checked = new ArrayList<>();
        paths.forEach((path, operations) -> {
            if (!path.startsWith("/api/v1/admin") || path.endsWith("/auth/login")) {
                return;
            }
            for (String method : operations.keySet()) {
                String concrete = path.replaceAll("\\{[^}]+}", "1"); // /content/{kind}/... -> /content/1/...
                assertUnauthorized(given().contentType("application/json").body("{}")
                        .request(Method.valueOf(method.toUpperCase()), concrete));
                checked.add(method + " " + path);
            }
        });
        assertTrue(checked.size() >= 8, "se esperaban al menos las rutas de auth y las de contenido: " + checked);
        // Extra fuera del plan: una ruta admin que no existe no revela nada (404 genérico, sin pista de qué hay detrás).
        given().when().get("/api/v1/admin/nada/de/esto").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    private static io.restassured.response.Response getSession(String token) {
        return given().header("Authorization", "Bearer " + token).when().get(SESSION);
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void forgedExpiredOrMismatchedTokensAreAllRejected() { // T83
        Instant later = Instant.now().plusSeconds(3600);
        getSession(support.token(1)).then().statusCode(200); // el control: este sí vale
        assertUnauthorized(getSession(forge(OTHER_SECRET, "cv-api", "cv-admin", 1, later))); // firma de otra clave
        assertUnauthorized(getSession(forge(TEST_SECRET, "cv-api", "cv-admin", 1, Instant.now().minusSeconds(30)))); // caducado
        assertUnauthorized(getSession(forge(TEST_SECRET, "otro-emisor", "cv-admin", 1, later))); // iss distinto
        assertUnauthorized(getSession(forge(TEST_SECRET, "cv-api", "otra-audiencia", 1, later))); // aud distinto
        assertUnauthorized(getSession(forge(TEST_SECRET, "cv-api", "cv-admin", 99, later))); // sv que no es el de la BD
        String none = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + b64("{\"iss\":\"cv-api\",\"aud\":\"cv-admin\",\"sub\":\"admin\",\"sv\":1,\"exp\":"
                        + later.getEpochSecond() + "}") + ".";
        assertUnauthorized(getSession(none)); // alg=none
        String valid = support.token(1);
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + b64("{\"iss\":\"cv-api\",\"aud\":\"cv-admin\",\"sub\":\"admin\",\"sv\":1,\"exp\":"
                + later.getEpochSecond() + ",\"extra\":true}") + "." + parts[2];
        assertUnauthorized(getSession(tampered)); // payload manipulado
        assertUnauthorized(getSession("basura")); // ni siquiera es un JWT
        // Token firmado y válido pero sin el claim sv: no puede ser de esta API.
        String noSv = Jwt.claims().issuer("cv-api").audience("cv-admin").subject("admin").expiresAt(later)
                .jws().algorithm(SignatureAlgorithm.HS256)
                .sign(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertUnauthorized(getSession(noSv));
    }

    @Test
    void aMalformedBearerOnAPublicEndpointIsAnchoredToItsRealBehaviour() { // T84
        // Comportamiento real: con autenticación diferida, una ruta pública no mira la cabecera Authorization, así que un
        // Bearer inválido no le afecta. Queda anclado aquí para no depender de la memoria.
        given().header("Authorization", "Bearer basura").when().get("/api/v1/profile").then().statusCode(200);
        // Sin cabecera, o con un esquema distinto, la API pública sigue respondiendo con normalidad.
        given().when().get("/api/v1/profile").then().statusCode(200);
        given().header("Authorization", "Basic dXNlcjpwYXNz").when().get("/api/v1/profile").then().statusCode(200);
    }
}
