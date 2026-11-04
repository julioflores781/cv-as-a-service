package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProfileResourceTest {

    @Test
    void returnsSpanishProfileByDefault() {
        given().when().get("/api/v1/profile")
                .then().statusCode(200)
                .body("name", equalTo("Ada Ejemplo"))
                .body("label", containsString("foco backend"));
    }

    @Test
    void returnsEnglishProfileWithLangParam() {
        given().queryParam("lang", "en").when().get("/api/v1/profile")
                .then().statusCode(200)
                .body("label", containsString("backend focus"));
    }

    @Test
    void honoursAcceptLanguageHeader() {
        given().header("Accept-Language", "en-US").when().get("/api/v1/profile")
                .then().statusCode(200)
                .body("label", containsString("backend focus"));
    }
}
