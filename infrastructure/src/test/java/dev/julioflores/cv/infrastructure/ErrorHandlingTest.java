package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ErrorHandlingTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    // ── Idioma: español por defecto ─────────────────────────────────────────────

    @Test
    void unknownRouteIsAProblem404InSpanishByDefault() {
        given().when().get("/api/v1/nope")
                .then().statusCode(404)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("type", equalTo("about:blank"))
                .body("code", equalTo("NOT_FOUND"))
                .body("title", equalTo("Recurso no encontrado"))
                .body("detail", equalTo("La ruta pedida no existe."))
                .body("status", equalTo(404));
    }

    @Test
    void wrongMethodIsAProblem405() {
        given().when().post("/api/v1/profile")
                .then().statusCode(405)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("METHOD_NOT_ALLOWED"))
                .body("title", equalTo("Método no permitido"))
                .body("status", equalTo(405));
    }

    @Test
    void unexpectedFailureIsAGeneric500ThatLeaksNothing() {
        given().when().get("/test/boom")
                .then().statusCode(500)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("UNEXPECTED"))
                .body("title", equalTo("Error interno"))
                .body("status", equalTo(500))
                .body("detail", equalTo("Ha ocurrido un error inesperado."))
                .body(not(containsString("hunter2")))
                .body(not(containsString("IllegalStateException")));
    }

    @Test
    void theUnexpectedErrorIsLoggedWithTheRequestIdAndTheAccessLineSaysUnexpected() { // T13
        try (LogRecords logs = LogRecords.attach()) {
            var response = given().when().get("/test/boom");
            response.then().statusCode(500);
            String rid = response.header("X-Request-Id");
            var error = logs.await(r -> r.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()
                    && "Unexpected error".equals(r.getFormattedMessage())).get(0);
            assertEquals(rid, LogRecords.mdc(error, "request_id"));
            String line = logs.awaitAccessLine("GET /test/boom 500", rid);
            org.junit.jupiter.api.Assertions.assertTrue(line.contains(" err=UNEXPECTED "), line);
        }
    }

    // ── Idioma: inglés por ?lang y por Accept-Language ──────────────────────────

    @Test
    void errorsSpeakEnglishWithLangParam() {
        given().queryParam("lang", "en").when().get("/api/v1/nope")
                .then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("title", equalTo("Not found"))
                .body("detail", equalTo("The requested path does not exist."));
    }

    @Test
    void errorsSpeakEnglishWithAcceptLanguage() {
        given().header("Accept-Language", "en-US,en;q=0.9").when().post("/api/v1/profile")
                .then().statusCode(405)
                .body("code", equalTo("METHOD_NOT_ALLOWED"))
                .body("title", equalTo("Method not allowed"));
    }

    @Test
    void theLangParamBeatsAcceptLanguageInErrorsToo() {
        given().queryParam("lang", "es").header("Accept-Language", "en").when().get("/api/v1/nope")
                .then().statusCode(404)
                .body("title", equalTo("Recurso no encontrado"));
    }

    @Test
    void unexpectedErrorsAreTranslatedButStillLeakNothing() {
        given().queryParam("lang", "en").when().get("/test/boom")
                .then().statusCode(500)
                .body("title", equalTo("Internal error"))
                .body("detail", equalTo("An unexpected error occurred."))
                .body(not(containsString("hunter2")));
    }

    // ── Errores de negocio con argumento ────────────────────────────────────────

    @Test
    void unknownExperienceCarriesCodeAndTranslatedDetail() {
        given().when().get("/api/v1/experience/nope")
                .then().statusCode(404)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("EXPERIENCE_NOT_FOUND"))
                .body("title", equalTo("Experiencia no encontrada"))
                .body("detail", equalTo("No existe ninguna experiencia con el id «nope»."));

        given().queryParam("lang", "en").when().get("/api/v1/experience/nope")
                .then().statusCode(404)
                .body("code", equalTo("EXPERIENCE_NOT_FOUND"))
                .body("title", equalTo("Experience not found"))
                .body("detail", equalTo("No experience with id 'nope'."));
    }

    @Test
    void unknownSkillGroupCarriesCodeAndTranslatedDetail() {
        given().queryParam("group", "nope").when().get("/api/v1/skills")
                .then().statusCode(404)
                .body("code", equalTo("SKILL_GROUP_NOT_FOUND"))
                .body("title", equalTo("Grupo de skills no encontrado"))
                .body("detail", equalTo("No existe el grupo de skills «nope»."));
    }

    @Test
    void theCodeDoesNotChangeWithTheLanguage() {
        String es = given().when().get("/api/v1/experience/nope").path("code");
        String en = given().queryParam("lang", "en").when().get("/api/v1/experience/nope").path("code");
        assertEquals(es, en);
    }

    @Test
    void errorsAreNotCached() {
        given().when().get("/test/boom").then().header("Cache-Control", nullValue());
        given().when().get("/api/v1/experience/nope").then().header("Cache-Control", nullValue());
    }

    @Test
    void securityForbiddenIsAProblem403() { // extra fuera del plan: mapeo explícito de ForbiddenException
        given().when().get("/test/boom/forbidden")
                .then().statusCode(403)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("FORBIDDEN"))
                .body("title", equalTo("Acceso denegado"));
    }
}
