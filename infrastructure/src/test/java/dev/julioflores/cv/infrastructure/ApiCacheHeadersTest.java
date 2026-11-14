package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApiCacheHeadersTest {

    @Inject
    AdminTestSupport support;

    @BeforeEach
    void account() {
        support.resetAccount();
    }

    @Test
    void adminResponsesAreNoStoreAndNeverPublicMaxAge() { // T85
        String token = support.token(1);
        given().header("Authorization", "Bearer " + token).when().get("/api/v1/admin/auth/session")
                .then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .header("Cache-Control", not(org.hamcrest.Matchers.containsString("max-age")));
        // También los errores admin (sin token) y el 404 de una ruta admin con sesión.
        given().when().get("/api/v1/admin/auth/session").then().statusCode(401)
                .header("Cache-Control", equalTo("no-store"));
        given().header("Authorization", "Bearer " + token).when().get("/api/v1/admin/nada").then().statusCode(404)
                .header("Cache-Control", equalTo("no-store"));
    }

    @Test
    void publicGetsKeepTheirPublicCacheControlAndVary() { // T86
        given().when().get("/api/v1/profile").then().statusCode(200)
                .header("Cache-Control", equalTo("public, max-age=300"))
                .header("Vary", equalTo("Accept-Language"));
        given().when().get("/api/v1/services").then().statusCode(200)
                .header("Cache-Control", equalTo("public, max-age=300"));
    }
}
