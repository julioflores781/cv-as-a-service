package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Servicios, proyectos y arquitectura, con el contenido ficticio de db/sample. */
@QuarkusTest
class SiteContentEndpointsTest {

    @Test
    void servicesListPricedAndOnRequestOnes() {
        given().when().get("/api/v1/services")
                .then().statusCode(200)
                .header("Cache-Control", containsString("max-age=300"))
                .body("size()", equalTo(2))
                .body("[0].id", equalTo("servicio-ejemplo"))
                .body("[0].price.from", equalTo(1500))
                .body("[0].price.currency", equalTo("EUR"))
                .body("[1].price", nullValue());
    }

    @Test
    void servicesFollowTheRequestedLanguage() {
        given().queryParam("lang", "en").when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Sample service"));
        given().header("Accept-Language", "en-US,en;q=0.9").when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Sample service"));
        given().when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Servicio de ejemplo"));
    }

    @Test
    void publicProjectExposesItsClientAndLinks() {
        given().when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("[0].id", equalTo("proyecto-publico"))
                .body("[0].relation", equalTo("own"))
                .body("[0].disclosure", equalTo("public"))
                .body("[0].client", equalTo("Ejemplo S.A."))
                .body("[0].period.start", equalTo("2022-01"))
                .body("[0].period.current", equalTo(true))
                .body("[0].stack", hasItems("Java", "Quarkus"))
                .body("[0].links[0].label", equalTo("Ver el proyecto"));
    }

    @Test
    void anonymousProjectOnlyDescribesItsClient() {
        given().when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("[1].id", equalTo("proyecto-anonimo"))
                .body("[1].disclosure", equalTo("anonymous"))
                .body("[1].client", nullValue())
                .body("[1].clientLabel", equalTo("prepaga de ejemplo"))
                .body("[1].period.end", equalTo("2020-11"))
                .body("[1].period.current", equalTo(false))
                .body("[1].highlights", empty());
    }

    @Test
    void architectureListsPortsWithTheirAdaptersAndHonestStatus() {
        given().when().get("/api/v1/architecture")
                .then().statusCode(200)
                .body("core.name", equalTo("Dominio"))
                .body("ports.size()", equalTo(2))
                .body("ports[0].id", equalTo("database"))
                .body("ports[0].adapters.id", hasItems("postgresql", "mongodb"))
                .body("ports[0].adapters[0].status", equalTo("in-this-api"))
                .body("ports[0].adapters[1].status", equalTo("production"))
                .body("ports[0].adapters[1].note", equalTo("Nota de ejemplo"))
                .body("ports[1].adapters[0].status", equalTo("experiment"))
                .body("ports[1].adapters[0].note", nullValue());
    }

    @Test
    void architectureInEnglish() {
        given().queryParam("lang", "en").when().get("/api/v1/architecture")
                .then().statusCode(200).body("core.name", equalTo("Domain")).body("ports[0].name", equalTo("Database"));
    }

    @Test
    void theRealClientNameOfAnonymousProjectsIsNotInTheResponseBody() {
        String body = given().when().get("/api/v1/projects").then().statusCode(200).extract().asString();
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("\"client\":\"prepaga")));
        org.hamcrest.MatcherAssert.assertThat(body, notNullValue());
    }

    @Test
    void postIsNotAllowed() {
        given().when().post("/api/v1/services").then().statusCode(405);
    }
}
