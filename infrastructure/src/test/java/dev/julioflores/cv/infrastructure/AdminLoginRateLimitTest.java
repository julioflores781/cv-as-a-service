package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Perfil con el límite global real de producción (10 por minuto) para comprobar el 429 por HTTP. */
@QuarkusTest
@TestProfile(AdminLoginRateLimitTest.RealLimit.class)
class AdminLoginRateLimitTest {

    public static class RealLimit implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("cv.admin.login.rate-limit.max", "10", "cv.admin.login.max-failures", "1000");
        }
    }

    @Inject
    AdminTestSupport support;

    @Test
    void theEleventhLoginInAMinuteIs429RateLimited() { // T90 (parte HTTP)
        support.resetAccount();
        for (int i = 0; i < 10; i++) {
            given().contentType("application/json").body("{\"password\":\"incorrecta\"}")
                    .when().post("/api/v1/admin/auth/login").then().statusCode(401);
        }
        given().contentType("application/json").body("{\"password\":\"" + AdminTestSupport.PASSWORD + "\"}")
                .when().post("/api/v1/admin/auth/login").then().statusCode(429)
                .header("Content-Type", containsString("application/problem+json"))
                .body("code", equalTo("ADMIN_LOGIN_RATE_LIMITED"));
    }
}
