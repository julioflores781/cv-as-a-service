package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** El caso real de desplegar sin añadir CV_ADMIN_JWT_SECRET: secreto vacío. La API debe arrancar igual. */
@QuarkusTest
@TestProfile(AdminNoSecretTest.NoSecret.class)
class AdminNoSecretTest {

    public static class NoSecret implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("cv.admin.jwt.secret", "");
        }
    }

    @Test
    void theApiStartsServesThePublicPartAndAnswers404ForAdmin() { // T87 (sin secreto)
        given().when().get("/api/v1/profile").then().statusCode(200);
        given().contentType("application/json").body("{\"password\":\"una frase larga y secreta\"}")
                .when().post("/api/v1/admin/auth/login")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().when().get("/api/v1/admin/auth/session").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }
}
