package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@QuarkusTest
class Hl7ResourceTest {

    private static final String PATH = "/api/v1/hl7/translate";
    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String ADT = String.join("\n",
            "MSH|^~\\&|HIS|HOSPITAL|LAB|HOSPITAL|20260101120000||ADT^A01^ADT_A01|MSG1|P|2.5.1",
            "EVN|A01|20260101120000",
            "PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M",
            "PV1|1|I|UCI^1^A");

    private static final String ORU = String.join("\n",
            "MSH|^~\\&|LAB|HOSPITAL|HIS|HOSPITAL|20260101130000||ORU^R01^ORU_R01|MSG2|P|2.5.1",
            "PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M",
            "OBR|1|||718-7^Hemoglobina^LN",
            "OBX|1|NM|718-7^Hemoglobina^LN||13.5|g/dL|12.0-16.0|N|||F");

    private static ValidatableResponse post(String body, String query) {
        return given().contentType("text/plain; charset=UTF-8").body(body.getBytes(StandardCharsets.UTF_8))
                .when().post(PATH + query).then();
    }

    // ── Casos felices ───────────────────────────────────────────────────────────

    @Test
    void jsonIsTheDefaultFormat() {
        post(ADT, "").statusCode(200)
                .contentType(ContentType.JSON)
                .body("messageType", equalTo("ADT^A01"))
                .body("patient.familyName", equalTo("Pérez"))
                .body("visit.location", equalTo("UCI/1/A"));
    }

    @Test
    void fhirFormatReturnsABundleWithFhirContentType() {
        post(ORU, "?format=fhir").statusCode(200)
                .header("Content-Type", containsString("application/fhir+json"))
                .body("resourceType", equalTo("Bundle"))
                .body("entry[1].resource.valueQuantity.value", equalTo(13.5f))
                .body("entry[1].resource.code.coding[0].system", equalTo("http://loinc.org"));
    }

    @Test
    void translationsAreNotCached() {
        post(ADT, "").statusCode(200).header("Cache-Control", nullValue());
    }

    // ── Errores: código estable, estado HTTP e idioma ───────────────────────────

    @Test
    void emptyBodyIs400InSpanishByDefault() {
        post("", "").statusCode(400)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("HL7_EMPTY_BODY"))
                .body("title", equalTo("Mensaje vacío"))
                .body("status", equalTo(400));
    }

    @Test
    void unknownFormatIs400AndEchoesTheValueInTheRequestedLanguage() {
        post(ADT, "?format=xml&lang=en").statusCode(400)
                .body("code", equalTo("HL7_UNSUPPORTED_FORMAT"))
                .body("detail", equalTo("The format 'xml' does not exist: use 'json' or 'fhir'."));
    }

    @Test
    void notHl7Is422() {
        post("esto no es HL7", "").statusCode(422)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("HL7_INVALID_MESSAGE"))
                .body("detail", not(containsString("esto no es HL7")));
    }

    @Test
    void unsupportedTypeIs422AndSaysWhatWasReceived() {
        post(ADT.replace("ADT^A01^ADT_A01", "ADT^A08"), "?lang=en").statusCode(422)
                .body("code", equalTo("HL7_UNSUPPORTED_MESSAGE"))
                .body("title", equalTo("Unsupported HL7 message"))
                .body("detail", containsString("Received ADT^A08 (v2.5.1)."));
    }

    @Test
    void messageOverTheLimitIs413WithAProblemBody() {
        String big = "MSH|^~\\&|" + "x".repeat(70 * 1024);
        post(big, "").statusCode(413)
                .header("Content-Type", containsString(PROBLEM_JSON))
                .body("code", equalTo("HL7_PAYLOAD_TOO_LARGE"))
                .body("detail", equalTo("El mensaje supera el máximo de 64 KB."));
    }

    @Test
    void bodyOverTheHardServerLimitIsCutBeforeReading() {
        post("x".repeat(200 * 1024), "").statusCode(413);
    }

    @Test
    void onlyPlainTextIsAccepted() {
        given().contentType(ContentType.JSON).body("{}").when().post(PATH)
                .then().statusCode(415).body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void getIsNotAllowed() {
        given().when().get(PATH).then().statusCode(405).body("code", equalTo("METHOD_NOT_ALLOWED"));
    }
}
