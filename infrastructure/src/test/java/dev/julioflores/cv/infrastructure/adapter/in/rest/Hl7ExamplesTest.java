package dev.julioflores.cv.infrastructure.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.infrastructure.adapter.out.hl7.HapiHl7Translator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Los ejemplos de Swagger se comparan con la traducción real: si el mapeo cambia, esta prueba lo avisa. */
@QuarkusTest
class Hl7ExamplesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    HapiHl7Translator translator;

    private void assertTranslation(String request, OutputFormat format, String documented) throws IOException {
        JsonNode actual = JSON.readTree(translator.translate(request, format).body());
        assertEquals(JSON.readTree(documented), actual);
    }

    @Test
    void admissionExamplesMatchTheRealTranslation() throws IOException {
        assertTranslation(Hl7Examples.ADT_A01, OutputFormat.JSON, Hl7Examples.ADT_A01_JSON);
        assertTranslation(Hl7Examples.ADT_A01, OutputFormat.FHIR, Hl7Examples.ADT_A01_FHIR);
    }

    @Test
    void labResultExamplesMatchTheRealTranslation() throws IOException {
        assertTranslation(Hl7Examples.ORU_R01, OutputFormat.JSON, Hl7Examples.ORU_R01_JSON);
        assertTranslation(Hl7Examples.ORU_R01, OutputFormat.FHIR, Hl7Examples.ORU_R01_FHIR);
    }

    @Test
    void problemExamplesMatchTheRealErrorResponses() throws IOException {
        assertProblem(Hl7Examples.PROBLEM_EMPTY_BODY, "", "");
        assertProblem(Hl7Examples.PROBLEM_UNSUPPORTED_FORMAT, Hl7Examples.ADT_A01, "?format=xml");
        assertProblem(Hl7Examples.PROBLEM_PAYLOAD_TOO_LARGE, "x".repeat(70 * 1024), "");
        assertProblem(Hl7Examples.PROBLEM_INVALID_MESSAGE, "esto no es HL7", "");
        assertProblem(Hl7Examples.PROBLEM_UNSUPPORTED_MESSAGE, Hl7Examples.ADT_A01.replace("ADT^A01^ADT_A01", "ADT^A08"), "");
    }

    private static void assertProblem(String documented, String body, String query) throws IOException {
        String actual = given().contentType("text/plain; charset=UTF-8").body(body.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/v1/hl7/translate" + query).then().extract().asString();
        assertEquals(JSON.readTree(documented), JSON.readTree(actual));
    }
}
