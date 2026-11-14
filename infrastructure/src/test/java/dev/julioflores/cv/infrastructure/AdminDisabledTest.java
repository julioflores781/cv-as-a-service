package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Con un secreto corto (o ausente) la API arranca, la parte pública funciona y el admin no existe. */
@QuarkusTest
@TestProfile(AdminDisabledTest.ShortSecret.class)
class AdminDisabledTest {

    public static class ShortSecret implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("cv.admin.jwt.secret", "demasiado-corto");
        }
    }

    @Test
    void theApiStartsServesThePublicPartAndAnswers404ForAdmin() { // T87
        given().when().get("/api/v1/profile").then().statusCode(200);
        given().contentType("application/json").body("{\"password\":\"una frase larga y secreta\"}")
                .when().post("/api/v1/admin/auth/login")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().when().get("/api/v1/admin/auth/session").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }
}
