package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminAuthResourceTest {

    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String SESSION = "/api/v1/admin/auth/session";
    private static final String PASSWORD_PATH = "/api/v1/admin/auth/password";
    private static final String LOGOUT_ALL = "/api/v1/admin/auth/logout-all";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String NEW_PASSWORD = "otra frase larga y secreta";

    @Inject
    AdminTestSupport support;

    @BeforeEach
    void freshAccount() {
        support.resetAccount();
    }

    private static ValidatableResponse login(String password) {
        return given().contentType(ContentType.JSON).body("{\"password\":\"" + password + "\"}")
                .when().post(LOGIN).then();
    }

    private static String loginToken() {
        return login(AdminTestSupport.PASSWORD).statusCode(200).extract().path("accessToken");
    }

    private static JsonPath part(String jwt, int index) {
        return JsonPath.from(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8));
    }

    private static ValidatableResponse session(String token) {
        return given().header("Authorization", "Bearer " + token).when().get(SESSION).then();
    }

    @Test
    void correctPasswordReturnsAnHs256TokenWithItsClaims() { // T72
        var response = login(AdminTestSupport.PASSWORD).statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .body("expiresAt", notNullValue());
        String token = response.extract().path("accessToken");
        Instant expiresAt = Instant.parse(response.extract().path("expiresAt"));
        assertTrue(expiresAt.isAfter(Instant.now().plusSeconds(7 * 3600)), "8 horas por defecto");

        assertEquals("HS256", part(token, 0).getString("alg"));
        JsonPath claims = part(token, 1);
        assertEquals("cv-api", claims.getString("iss"));
        assertEquals("cv-admin", claims.getString("aud"));
        assertEquals("admin", claims.getString("sub"));
        assertEquals(1, claims.getInt("sv"));
        assertNotNull(claims.getString("jti"));
        assertEquals(expiresAt.getEpochSecond(), claims.getLong("exp"));
        assertEquals(java.util.List.of("admin"), claims.getList("groups"));
        assertEquals("no-store", given().contentType(ContentType.JSON).body("{\"password\":\"x\"}")
                .when().post(LOGIN).then().extract().header("Cache-Control"));
    }

    @Test
    void wrongPasswordIs401InSpanishByDefaultAndEnglishOnRequest() { // T73
        login("incorrecta").statusCode(401)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("code", equalTo("ADMIN_INVALID_CREDENTIALS"))
                .body("title", equalTo("Credenciales incorrectas"))
                .body("status", equalTo(401));
        given().queryParam("lang", "en").contentType(ContentType.JSON).body("{\"password\":\"incorrecta\"}")
                .when().post(LOGIN).then().statusCode(401)
                .body("code", equalTo("ADMIN_INVALID_CREDENTIALS"))
                .body("title", equalTo("Invalid credentials"))
                .body("detail", equalTo("The password is not correct."));
    }

    @Test
    void withoutAnAccountTheAnswerIsIdenticalToAWrongPassword() { // T74
        String wrong = login("incorrecta").statusCode(401).extract().asString();
        support.clear();
        String noAccount = login("incorrecta").statusCode(401).extract().asString();
        assertEquals(wrong, noAccount);
    }

    @Test
    void afterFiveFailuresEvenTheCorrectPasswordIs429WithRetryAfter() { // T75
        for (int i = 0; i < 5; i++) {
            login("incorrecta").statusCode(401);
        }
        var response = login(AdminTestSupport.PASSWORD).statusCode(429)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("ADMIN_LOGIN_LOCKED"))
                .header("Retry-After", notNullValue());
        long retryAfter = Long.parseLong(response.extract().header("Retry-After"));
        assertTrue(retryAfter > 0 && retryAfter <= 900, "Retry-After=" + retryAfter);
        assertTrue(response.extract().path("detail").toString().contains(Long.toString(retryAfter))
                || response.extract().path("detail").toString().contains("segundos"));
    }

    @Test
    void anEmptyMissingOrMalformedBodyIsNeverA500() { // T76
        given().contentType(ContentType.JSON).when().post(LOGIN).then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
        given().contentType(ContentType.JSON).body("{}").when().post(LOGIN).then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
        given().contentType(ContentType.JSON).body("{\"password\":").when().post(LOGIN).then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
        // Tipo equivocado, cuerpo que no es un objeto y cuerpo nulo: también problem+json, sin el cuerpo por defecto de Quarkus.
        for (String body : new String[] {"{\"password\":{\"a\":1}}", "{\"password\":[\"a\"]}", "[]", "null"}) {
            given().contentType(ContentType.JSON).body(body).when().post(LOGIN).then().statusCode(400)
                    .header("Content-Type", containsString(PROBLEM_JSON))
                    .body("code", equalTo("BAD_REQUEST"))
                    .body("objectName", org.hamcrest.Matchers.nullValue())
                    .body("attributeName", org.hamcrest.Matchers.nullValue());
        }
        given().contentType(ContentType.TEXT).body("password").when().post(LOGIN).then().statusCode(415)
                .body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void aValidTokenGetsTheSessionProbe() { // T77
        String token = loginToken();
        session(token).statusCode(200)
                .body("authenticated", equalTo(true))
                .body("passwordChangedAt", notNullValue())
                .body("expiresAt", notNullValue());
    }

    @Test
    void changingThePasswordReturnsANewTokenAndKillsTheOldOneAndThePreviousPassword() { // T78
        String oldToken = loginToken();
        String newToken = given().header("Authorization", "Bearer " + oldToken).contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(AdminTestSupport.PASSWORD, NEW_PASSWORD))
                .when().put(PASSWORD_PATH).then().statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .extract().path("accessToken");
        assertNotEquals(oldToken, newToken);
        assertEquals(2, part(newToken, 1).getInt("sv"));

        session(oldToken).statusCode(401).body("code", equalTo("ADMIN_UNAUTHORIZED"));
        session(newToken).statusCode(200);
        login(AdminTestSupport.PASSWORD).statusCode(401);
        login(NEW_PASSWORD).statusCode(200);
    }

    @Test
    void aWrongCurrentPasswordIs403NotA401() { // T79
        String token = loginToken();
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"mala\",\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD))
                .when().put(PASSWORD_PATH).then().statusCode(403)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("ADMIN_CURRENT_PASSWORD_WRONG"));
        session(token).statusCode(200);
    }

    @Test
    void aWeakNewPasswordIs422() { // T80
        String token = loginToken();
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"%s\",\"newPassword\":\"corta\"}".formatted(AdminTestSupport.PASSWORD))
                .when().put(PASSWORD_PATH).then().statusCode(422)
                .body("code", equalTo("ADMIN_WEAK_PASSWORD"))
                .body("title", equalTo("Contraseña débil"));
        session(token).statusCode(200);
    }

    @Test
    void logoutAllInvalidatesEveryToken() { // T81
        String first = loginToken();
        String second = loginToken();
        session(first).statusCode(200);
        session(second).statusCode(200);
        given().header("Authorization", "Bearer " + first).when().post(LOGOUT_ALL).then().statusCode(204)
                .header("Cache-Control", equalTo("no-store"));
        session(first).statusCode(401);
        session(second).statusCode(401);
        login(AdminTestSupport.PASSWORD).statusCode(200);
    }

    @Test
    void passwordChangeWithMissingFieldsIs400() { // extra fuera del plan
        String token = loginToken();
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body("{\"newPassword\":\"x\"}")
                .when().put(PASSWORD_PATH).then().statusCode(400).body("code", equalTo("BAD_REQUEST"));
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .when().put(PASSWORD_PATH).then().statusCode(400).body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void passwordChangeWithWrongTypesIsAProblem400() { // T76 ampliado a PUT /auth/password (extra fuera del plan)
        String token = loginToken();
        for (String body : new String[] {"{\"currentPassword\":{},\"newPassword\":\"x\"}", "[]", "{\"currentPassword\":1,\"newPassword\":[2]}"}) {
            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(body)
                    .when().put(PASSWORD_PATH).then().statusCode(400)
                    .header("Content-Type", containsString(PROBLEM_JSON))
                    .body("code", equalTo("BAD_REQUEST"))
                    .body("objectName", org.hamcrest.Matchers.nullValue());
        }
    }

    @Test
    void aLockedAccountCannotChangeThePasswordEvenWithAValidToken() { // extra fuera del plan
        String token = loginToken();
        support.execute("UPDATE admin_account SET failed_attempts = 5, locked_until = now() + interval '10 minutes'");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(AdminTestSupport.PASSWORD, NEW_PASSWORD))
                .when().put(PASSWORD_PATH).then().statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("ADMIN_LOGIN_LOCKED"));
    }

    @Test
    void sessionResponseNeverExposesTheHash() { // extra fuera del plan
        String body = session(loginToken()).statusCode(200).extract().asString();
        assertTrue(!body.contains("argon2") && !body.contains("hash"), body);
    }
}
