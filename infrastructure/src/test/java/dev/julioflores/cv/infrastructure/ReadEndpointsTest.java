package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReadEndpointsTest {

    @Test
    void impact() {
        given().when().get("/api/v1/impact")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].value", equalTo("+10.000"));
    }

    @Test
    void impactInEnglish() {
        given().queryParam("lang", "en").when().get("/api/v1/impact")
                .then().statusCode(200)
                .body("[0].value", equalTo("10,000+"));
    }

    @Test
    void experienceListsNewestFirstWithPhases() {
        given().when().get("/api/v1/experience")
                .then().statusCode(200)
                .body("size()", equalTo(3))
                .body("id", hasItems("acme-corp", "globex", "initech"))
                .body("[0].id", equalTo("acme-corp"))
                .body("[0].period.start", equalTo("2020-01"))
                .body("[0].period.end", nullValue())
                .body("[0].period.current", equalTo(true))
                .body("[0].phases.size()", equalTo(2))
                .body("[0].phases[1].title", containsString("microservicios"))
                .body("[0].phases[1].client", notNullValue());
    }

    @Test
    void experienceById() {
        given().when().get("/api/v1/experience/globex")
                .then().statusCode(200)
                .body("company", equalTo("Globex"))
                .body("period.start", equalTo("2017-03"))
                .body("period.end", equalTo("2019-12"))
                .body("period.current", equalTo(false));
    }

    @Test
    void unknownExperienceIsAProblem404() {
        given().when().get("/api/v1/experience/nope")
                .then().statusCode(404)
                .header("Content-Type", containsString("application/problem+json"))
                .body("code", equalTo("EXPERIENCE_NOT_FOUND"))
                .body("title", equalTo("Experiencia no encontrada"))
                .body("status", equalTo(404))
                .body("detail", containsString("nope"));
    }

    @Test
    void skillsAllGroups() {
        given().when().get("/api/v1/skills")
                .then().statusCode(200)
                .body("size()", equalTo(3))
                .body("key", hasItems("backend", "data", "platform"));
    }

    @Test
    void skillsFilteredByGroup() {
        given().queryParam("group", "backend").when().get("/api/v1/skills")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].keywords", hasItem("Quarkus"));
    }

    @Test
    void unknownSkillGroupIsAProblem404() {
        given().queryParam("group", "nope").when().get("/api/v1/skills")
                .then().statusCode(404)
                .body("title", equalTo("Grupo de skills no encontrado"));
    }

    @Test
    void education() {
        given().when().get("/api/v1/education")
                .then().statusCode(200)
                .body("education.size()", equalTo(1))
                .body("certificates[0].name", containsString("EX-100"))
                .body("courses.size()", equalTo(2));
    }

    @Test
    void contactNeverExposesPersonalData() {
        given().when().get("/api/v1/contact")
                .then().statusCode(200)
                .body("email", equalTo("ada@example.com"))
                .body("profiles.network", hasItems("LinkedIn", "GitHub"))
                .body("phone", nullValue())
                .body("birthDate", nullValue());
    }

    @Test
    void fullResume() {
        given().when().get("/api/v1/resume")
                .then().statusCode(200)
                .body("language", equalTo("es"))
                .body("profile.name", equalTo("Ada Ejemplo"))
                .body("experience.size()", equalTo(3))
                .body("skills.size()", equalTo(3))
                .body("education.courses.size()", equalTo(2))
                .body("languages.language", hasItems("Español", "Inglés"));
    }

    @Test
    void fullResumeInEnglishViaAcceptLanguage() {
        given().header("Accept-Language", "en-US,en;q=0.9").when().get("/api/v1/resume")
                .then().statusCode(200)
                .body("language", equalTo("en"))
                .body("languages.language", hasItems("Spanish", "English"));
    }

    @Test
    void readEndpointsAreCacheableAndVaryByLanguage() {
        given().when().get("/api/v1/profile")
                .then().statusCode(200)
                .header("Cache-Control", containsString("max-age"))
                .header("Vary", containsString("Accept-Language"));
    }
}
