package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** El compose y cv-web consultan {@code /q/health/ready} en el puerto principal: la interfaz de gestión no se activa. */
@QuarkusTest
class HealthStaysOnMainPortTest {

    @Test
    void healthAnswers200OnTheMainPortAndManagementIsOff() { // T29
        given().when().get("/q/health/ready").then().statusCode(200);
        given().when().get("/q/health/live").then().statusCode(200);
        boolean management = ConfigProvider.getConfig().getOptionalValue("quarkus.management.enabled", Boolean.class).orElse(false);
        assertFalse(management);
    }
}
