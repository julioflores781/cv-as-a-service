package dev.julioflores.cv.infrastructure.adapter.out.hl7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.application.service.InvalidHl7MessageException;
import dev.julioflores.cv.application.service.UnsupportedHl7MessageException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * El adaptador se prueba directamente, con mensajes sintéticos. Va como @QuarkusTest para que su cobertura
 * cuente: en este módulo solo se mide lo que se carga dentro de Quarkus.
 */
@QuarkusTest
class HapiHl7TranslatorTest {

    @Inject
    HapiHl7Translator translator;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String ADT = String.join("\r",
            "MSH|^~\\&|HIS|HOSPITAL|LAB|HOSPITAL|20260101120000||ADT^A01^ADT_A01|MSG1|P|2.5.1",
            "EVN|A01|20260101120000",
            "PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M",
            "PV1|1|I|UCI^1^A");

    private static final String ORU = String.join("\r",
            "MSH|^~\\&|LAB|HOSPITAL|HIS|HOSPITAL|20260101130000||ORU^R01^ORU_R01|MSG2|P|2.5.1",
            "PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|F",
            "OBR|1|||718-7^Hemoglobina^LN",
            "OBX|1|NM|718-7^Hemoglobina^LN||13.5|g/dL|12.0-16.0|N|||F",
            "OBX|2|ST|11502-2^Comentario^LN||Muestra sin hemólisis||||||P",
            "OBX|3|CWE|5778-6^Color^LN||amarillo^Amarillo^L||||||C",
            "OBX|4|NM|2345-7^Glucosa^LN||alto|mg/dL|||||X");

    private JsonNode translate(String message, OutputFormat format) throws IOException {
        return JSON.readTree(translator.translate(message, format).body());
    }

    // ── ADT^A01 ─────────────────────────────────────────────────────────────────

    @Test
    void admissionToJson() throws IOException {
        JsonNode json = translate(ADT, OutputFormat.JSON);
        assertEquals("ADT^A01", json.path("messageType").asText());
        assertEquals("2.5.1", json.path("version").asText());
        assertEquals("MSG1", json.path("controlId").asText());
        assertEquals("12345", json.at("/patient/id").asText());
        assertEquals("HOSP", json.at("/patient/idAssigner").asText());
        assertEquals("Pérez", json.at("/patient/familyName").asText());
        assertEquals("Juan", json.at("/patient/givenName").asText());
        assertEquals("1980-01-01", json.at("/patient/birthDate").asText());
        assertEquals("M", json.at("/patient/gender").asText());
        assertEquals("I", json.at("/visit/patientClass").asText());
        assertEquals("UCI/1/A", json.at("/visit/location").asText());
        assertFalse(json.has("observations"), "un ingreso no tiene observaciones");
    }

    @Test
    void admissionToFhirBundle() throws IOException {
        JsonNode bundle = translate(ADT, OutputFormat.FHIR);
        assertEquals("Bundle", bundle.path("resourceType").asText());
        assertEquals("collection", bundle.path("type").asText());
        assertEquals("MSG1", bundle.path("id").asText());
        assertEquals(2, bundle.path("entry").size());

        JsonNode patient = bundle.at("/entry/0/resource");
        assertEquals("Patient", patient.path("resourceType").asText());
        assertEquals("male", patient.path("gender").asText());
        assertEquals("1980-01-01", patient.path("birthDate").asText());
        assertEquals("Pérez", patient.at("/name/0/family").asText());
        assertEquals("Juan", patient.at("/name/0/given/0").asText());
        assertEquals("12345", patient.at("/identifier/0/value").asText());

        JsonNode encounter = bundle.at("/entry/1/resource");
        assertEquals("Encounter", encounter.path("resourceType").asText());
        assertEquals("in-progress", encounter.path("status").asText());
        assertEquals("IMP", encounter.at("/class/code").asText());
        assertEquals("Patient/patient", encounter.at("/subject/reference").asText());
        assertEquals("UCI/1/A", encounter.at("/location/0/location/display").asText());
    }

    @Test
    void outpatientAndEmergencyClassesMapToActCodeAndUnknownOnesKeepTheirHl7Code() throws IOException {
        assertEquals("AMB", encounterClass(ADT.replace("|I|UCI", "|O|UCI")).path("code").asText());
        assertEquals("EMER", encounterClass(ADT.replace("|I|UCI", "|E|UCI")).path("code").asText());
        JsonNode other = encounterClass(ADT.replace("|I|UCI", "|B|UCI"));
        assertEquals("B", other.path("code").asText());
        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0004", other.path("system").asText());
        assertEquals("AMB", encounterClass(ADT.replace("|I|UCI", "||UCI")).path("code").asText());
    }

    private JsonNode encounterClass(String message) throws IOException {
        return translate(message, OutputFormat.FHIR).at("/entry/1/resource/class");
    }

    // ── ORU^R01 ─────────────────────────────────────────────────────────────────

    @Test
    void labResultToJson() throws IOException {
        JsonNode json = translate(ORU, OutputFormat.JSON);
        assertEquals("ORU^R01", json.path("messageType").asText());
        assertEquals("F", json.at("/patient/gender").asText());
        assertFalse(json.has("visit"), "un resultado sin PV1 no tiene visita");
        JsonNode observations = json.path("observations");
        assertEquals(4, observations.size());

        JsonNode hemoglobin = observations.get(0);
        assertEquals("718-7", hemoglobin.path("code").asText());
        assertEquals("Hemoglobina", hemoglobin.path("name").asText());
        assertEquals("LN", hemoglobin.path("codingSystem").asText());
        assertEquals(13.5, hemoglobin.path("numericValue").asDouble());
        assertEquals("g/dL", hemoglobin.path("unit").asText());
        assertEquals("12.0-16.0", hemoglobin.path("referenceRange").asText());
        assertEquals("F", hemoglobin.path("status").asText());

        assertEquals("Muestra sin hemólisis", observations.get(1).path("textValue").asText());
        assertEquals("Amarillo", observations.get(2).path("textValue").asText(), "un código CWE aporta su texto");
        assertEquals("alto", observations.get(3).path("textValue").asText(), "un NM que no es número se conserva como texto");
        assertFalse(observations.get(3).has("numericValue"));
    }

    @Test
    void labResultToFhirBundle() throws IOException {
        JsonNode bundle = translate(ORU, OutputFormat.FHIR);
        assertEquals(5, bundle.path("entry").size());
        assertEquals("female", bundle.at("/entry/0/resource/gender").asText());

        JsonNode hemoglobin = bundle.at("/entry/1/resource");
        assertEquals("Observation", hemoglobin.path("resourceType").asText());
        assertEquals("final", hemoglobin.path("status").asText());
        assertEquals("http://loinc.org", hemoglobin.at("/code/coding/0/system").asText());
        assertEquals("718-7", hemoglobin.at("/code/coding/0/code").asText());
        assertEquals(13.5, hemoglobin.at("/valueQuantity/value").asDouble());
        assertEquals("g/dL", hemoglobin.at("/valueQuantity/unit").asText());
        assertEquals("12.0-16.0", hemoglobin.at("/referenceRange/0/text").asText());
        assertEquals("Patient/patient", hemoglobin.at("/subject/reference").asText());

        assertEquals("preliminary", bundle.at("/entry/2/resource/status").asText());
        assertEquals("Muestra sin hemólisis", bundle.at("/entry/2/resource/valueString").asText());
        assertEquals("corrected", bundle.at("/entry/3/resource/status").asText());
        assertEquals("cancelled", bundle.at("/entry/4/resource/status").asText());
    }

    @Test
    void resultWithoutPatientHasNoSubjectReference() throws IOException {
        String noPatient = String.join("\r",
                "MSH|^~\\&|LAB|H|HIS|H|20260101||ORU^R01|M3|P|2.5.1",
                "OBR|1|||1^Nota^L",
                "OBX|1|ST|1^Nota^L||texto||||||Z");
        JsonNode bundle = translate(noPatient, OutputFormat.FHIR);
        assertEquals(1, bundle.path("entry").size());
        JsonNode observation = bundle.at("/entry/0/resource");
        assertFalse(observation.has("subject"));
        assertEquals("unknown", observation.path("status").asText(), "un estado desconocido no se inventa");
        assertFalse(observation.at("/code/coding/0").has("system"), "solo LN tiene sistema FHIR conocido");
    }

    // ── Tolerancia con la entrada ───────────────────────────────────────────────

    @Test
    void acceptsAnySegmentSeparatorAndSurroundingWhitespace() throws IOException {
        String expected = translator.translate(ADT, OutputFormat.JSON).body();
        assertEquals(expected, translator.translate(ADT.replace("\r", "\n"), OutputFormat.JSON).body());
        assertEquals(expected, translator.translate("\n  " + ADT.replace("\r", "\r\n") + "\r\n\r\n", OutputFormat.JSON).body());
    }

    @Test
    void invalidBirthDateAndMissingOptionalFieldsAreDroppedNotInvented() throws IOException {
        String message = String.join("\r",
                "MSH|^~\\&|A|B|C|D|2026||ADT^A01|M4|P|2.5.1",
                "PID|1||||Solo||19801345|");
        JsonNode patient = translate(message, OutputFormat.JSON).path("patient");
        assertEquals("Solo", patient.path("familyName").asText());
        assertFalse(patient.has("birthDate"));
        assertFalse(patient.has("id"));
        assertFalse(patient.has("gender"));
        assertEquals("unknown", translate(message, OutputFormat.FHIR).at("/entry/0/resource/gender").asText());
    }

    @Test
    void messageWithoutPidOrPv1HasNeitherPatientNorVisit() throws IOException {
        JsonNode json = translate("MSH|^~\\&|A|B|C|D|2026||ADT^A01|M5|P|2.5.1", OutputFormat.JSON);
        assertFalse(json.has("patient"));
        assertFalse(json.has("visit"));
        assertEquals(0, translate("MSH|^~\\&|A|B|C|D|2026||ADT^A01|M5|P|2.5.1", OutputFormat.FHIR).path("entry").size());
    }

    // ── Rechazos ────────────────────────────────────────────────────────────────

    @Test
    void textThatIsNotHl7IsInvalid() {
        for (String body : new String[] {"hola", "{\"a\":1}", "PID|1||x", "MSH|", "MSH|^~\\&|A|B"}) {
            assertThrows(InvalidHl7MessageException.class, () -> translator.translate(body, OutputFormat.JSON), body);
        }
    }

    @Test
    void headerWithoutTypeOrVersionIsInvalid() {
        for (String body : new String[] {
            "MSH|^~\\&|A|B|C|D|2026||ADT|M|P|2.5.1", // sin evento
            "MSH|^~\\&|A|B|C|D|2026||^A01|M|P|2.5.1", // sin código
            "MSH|^~\\&|A|B|C|D|2026||ADT^A01|M|P|", // sin versión
        }) {
            assertThrows(InvalidHl7MessageException.class, () -> translator.translate(body, OutputFormat.JSON), body);
        }
    }

    @Test
    void otherMessageTypesAndVersionsAreUnsupportedAndReportedBack() {
        var type = assertThrows(UnsupportedHl7MessageException.class,
                () -> translator.translate("MSH|^~\\&|A|B|C|D|2026||ADT^A08|M|P|2.5.1", OutputFormat.JSON));
        assertEquals("ADT^A08 (v2.5.1)", type.args().get(0));

        var version = assertThrows(UnsupportedHl7MessageException.class,
                () -> translator.translate("MSH|^~\\&|A|B|C|D|2026||ADT^A01|M|P|2.3", OutputFormat.JSON));
        assertEquals("ADT^A01 (v2.3)", version.args().get(0));
    }

    @Test
    void whatIsEchoedBackIsCleanedAndBounded() {
        String hostile = "MSH|^~\\&|A|B|C|D|2026||<script>alert(1)</script>^\"x\"" + "y".repeat(100) + "|M|P|2.5.1";
        var e = assertThrows(UnsupportedHl7MessageException.class, () -> translator.translate(hostile, OutputFormat.JSON));
        String echoed = (String) e.args().get(0);
        assertEquals("scriptalert1script^x (v2.5.1)", echoed);
        assertFalse(echoed.contains("<") || echoed.contains("\""));
    }

    @Test
    void failureDetailsNeverContainTheMessage() {
        var e = assertThrows(InvalidHl7MessageException.class,
                () -> translator.translate("MSH|^~\\&|A|B|C|D|2026||ADT|DATO-CLINICO-SECRETO|P|2.5.1", OutputFormat.JSON));
        assertFalse(e.getMessage().contains("SECRETO"));
        assertNull(e.getCause());
    }
}
