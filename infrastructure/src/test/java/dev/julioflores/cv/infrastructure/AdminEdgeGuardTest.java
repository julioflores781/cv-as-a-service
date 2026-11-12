package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AdminEdgeGuardTest.EdgeBlock.class)
class AdminEdgeGuardTest {

    public static class EdgeBlock implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("cv.admin.edge-block", "true");
        }
    }

    @Inject
    AdminTestSupport support;

    @Test
    void adminRequestsViaCloudflareAre404AndInternalOnesWork() { // T88
        support.resetAccount();
        String body = "{\"password\":\"" + AdminTestSupport.PASSWORD + "\"}";
        given().header("CF-Ray", "abc123-MAD").contentType("application/json").body(body)
                .when().post("/api/v1/admin/auth/login").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().header("CF-Connecting-IP", "203.0.113.9").contentType("application/json").body(body)
                .when().post("/api/v1/admin/auth/login").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().header("CF-Ray", "abc123-MAD").header("Authorization", "Bearer " + support.token(1))
                .when().get("/api/v1/admin/auth/session").then().statusCode(404);
        given().contentType("application/json").body(body)
                .when().post("/api/v1/admin/auth/login").then().statusCode(200);
    }

    @Test
    void publicRoutesAreNotAffectedByTheEdgeBlock() { // extra fuera del plan
        given().header("CF-Ray", "abc123-MAD").header("CF-Connecting-IP", "203.0.113.9")
                .when().get("/api/v1/profile").then().statusCode(200);
    }
}
