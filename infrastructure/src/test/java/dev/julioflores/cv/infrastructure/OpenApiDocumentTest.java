package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Falla si un endpoint queda sin documentar: es la red de seguridad de Swagger. */
@QuarkusTest
class OpenApiDocumentTest {

    private static final String HL7_PATH = "/api/v1/hl7/translate";
    private static final String CONTACT_MESSAGES_PATH = "/api/v1/contact/messages";

    /** Endpoints de lectura (GET). El POST de la demo HL7 se comprueba aparte. */
    private static final Set<String> ENDPOINTS = Set.of(
            "/api/v1/profile", "/api/v1/impact", "/api/v1/experience", "/api/v1/experience/{id}",
            "/api/v1/skills", "/api/v1/education", "/api/v1/contact", "/api/v1/resume");

    private static JsonPath cached;

    /**
     * Carga perezosa dentro de los tests. No usar @BeforeAll para hacer peticiones HTTP: en @QuarkusTest
     * puede ejecutarse antes de que la app esté lista (da 500, ClassNotFound o conexión rechazada según el orden).
     */
    private static JsonPath doc() {
        if (cached == null) {
            cached = given().header("Accept", "application/json").when().get("/openapi.json")
                    .then().statusCode(200).extract().jsonPath();
        }
        return cached;
    }

    private Map<String, Object> operation(String path) {
        return doc().getMap("paths.'" + path + "'.get");
    }

    @Test
    void everyEndpointIsPresentAndOnlyThose() {
        Set<String> documented = doc().<Map<String, Object>>getJsonObject("paths").keySet();
        documented.removeIf(p -> !p.startsWith("/api/v1"));
        Set<String> expected = new HashSet<>(ENDPOINTS);
        expected.add(HL7_PATH);
        expected.add(CONTACT_MESSAGES_PATH);
        assertEquals(expected, documented);
    }

    @Test
    void contactMessageOperationIsDocumentedWithItsErrors() {
        String post = "paths.'" + CONTACT_MESSAGES_PATH + "'.post";
        assertEquals("sendContactMessage", doc().getString(post + ".operationId"));
        assertEquals(List.of("Contact"), doc().getList(post + ".tags"));
        assertNotNull(doc().get(post + ".summary"));
        assertNotNull(doc().get(post + ".description"));
        assertNotNull(doc().get(post + ".requestBody"));
        for (String code : List.of("201", "400", "429", "500")) {
            assertNotNull(doc().get(post + ".responses.'" + code + "'.description"), "sin respuesta " + code);
        }
        for (String code : List.of("400", "429")) {
            String problems = post + ".responses.'" + code + "'.content.'application/problem+json'";
            assertEquals("#/components/schemas/Problem", doc().getString(problems + ".schema.'$ref'"), code);
            assertNotNull(doc().get(problems + ".examples"), code + " sin ejemplo");
        }
        assertNotNull(doc().get("components.schemas.ContactMessageRequest.properties.website.description"));
        List<Map<String, Object>> tags = doc().getList("tags");
        assertTrue(tags.stream().anyMatch(t -> "Contact".equals(t.get("name")) && t.get("description") != null));
    }

    @Test
    void everyOperationHasIdSummaryDescriptionAndTag() {
        for (String path : ENDPOINTS) {
            Map<String, Object> op = operation(path);
            assertNotNull(op.get("operationId"), path + " sin operationId");
            assertNotNull(op.get("summary"), path + " sin summary");
            assertTrue(((String) op.get("description")).length() > 30, path + " con descripción pobre");
            assertEquals(List.of("Resume"), op.get("tags"), path);
        }
    }

    @Test
    void operationIdsAreUnique() {
        long distinct = ENDPOINTS.stream().map(p -> operation(p).get("operationId")).distinct().count();
        assertEquals(ENDPOINTS.size(), distinct);
    }

    @Test
    void everyOperationDocumentsAllItsPossibleOutputs() {
        for (String path : ENDPOINTS) {
            Map<String, Object> responses = doc().getMap("paths.'" + path + "'.get.responses");
            assertEquals(Set.of("200", "404", "500"), responses.keySet(), path);
            for (String code : responses.keySet()) {
                String body = "paths.'" + path + "'.get.responses.'" + code + "'";
                assertNotNull(doc().get(body + ".description"), path + " " + code + " sin descripción");
                assertFalse(doc().getMap(body + ".content").isEmpty(), path + " " + code + " sin contenido");
            }
        }
    }

    @Test
    void successResponsesDocumentCacheHeaders() {
        for (String path : ENDPOINTS) {
            String headers = "paths.'" + path + "'.get.responses.'200'.headers";
            assertNotNull(doc().get(headers + ".'Cache-Control'.description"), path);
            assertNotNull(doc().get(headers + ".Vary.description"), path);
        }
    }

    @Test
    void errorsUseTheProblemSchemaAsProblemJson() {
        for (String path : ENDPOINTS) {
            for (String code : List.of("404", "500")) {
                String media = "paths.'" + path + "'.get.responses.'" + code + "'.content.'application/problem+json'";
                assertEquals("#/components/schemas/Problem", doc().getString(media + ".schema.'$ref'"), path + " " + code);
                assertTrue(doc().get(media + ".example") != null || doc().get(media + ".examples") != null,
                        path + " " + code + " sin ejemplo");
            }
        }
    }

    @Test
    void successResponsesHaveExamples() {
        for (String path : ENDPOINTS) {
            String media = "paths.'" + path + "'.get.responses.'200'.content.'application/json'";
            assertTrue(doc().get(media + ".example") != null || doc().get(media + ".examples") != null,
                    path + " 200 sin ejemplo");
        }
    }

    @Test
    void languageParametersAreDocumentedOnEveryEndpoint() {
        for (String path : ENDPOINTS) {
            List<Map<String, Object>> params = doc().getList("paths.'" + path + "'.get.parameters");
            List<String> names = params.stream().map(p -> (String) p.get("name")).toList();
            assertTrue(names.contains("lang"), path + " sin ?lang");
            assertTrue(names.contains("Accept-Language"), path + " sin Accept-Language");
            params.forEach(p -> assertNotNull(p.get("description"), path + " parámetro " + p.get("name")));
        }
    }

    @Test
    void pathAndQueryParametersAreDocumented() {
        List<Map<String, Object>> idParams = doc().getList("paths.'/api/v1/experience/{id}'.get.parameters");
        assertTrue(idParams.stream().anyMatch(p -> "id".equals(p.get("name")) && "path".equals(p.get("in"))
                && Boolean.TRUE.equals(p.get("required"))));
        List<Map<String, Object>> skillParams = doc().getList("paths.'/api/v1/skills'.get.parameters");
        assertTrue(skillParams.stream().anyMatch(p -> "group".equals(p.get("name")) && "query".equals(p.get("in"))));
    }

    @Test
    void schemasAreDocumentedFieldByField() {
        Map<String, Object> schemas = doc().getMap("components.schemas");
        for (String name : List.of("Profile", "Contact", "Link", "Impact", "Period", "Phase", "Experience",
                "SkillGroup", "Education", "Certificate", "Course", "Qualifications", "SpokenLanguage",
                "Resume", "Problem")) {
            assertTrue(schemas.containsKey(name), "falta el esquema " + name);
            assertNotNull(doc().get("components.schemas." + name + ".description"), name + " sin descripción");
            Map<String, Map<String, Object>> props = doc().getMap("components.schemas." + name + ".properties");
            // Una propiedad que solo apunta a otro esquema ($ref) hereda la descripción de ese esquema.
            props.forEach((prop, def) -> assertTrue(def.containsKey("description") || def.containsKey("$ref"),
                    name + "." + prop + " sin descripción"));
        }
    }

    @Test
    void globalInfoAndTagAreDocumented() {
        assertEquals("Julio Flores · CV as a Service", doc().getString("info.title"));
        String description = doc().getString("info.description");
        for (String needle : List.of("Accept-Language", "problem+json", "404", "405", "500", "Cache-Control", "`code`")) {
            assertTrue(description.contains(needle), "la descripción global no menciona " + needle);
        }
        assertNotNull(doc().getString("info.contact.email"));
        List<Map<String, Object>> tags = doc().getList("tags");
        assertTrue(tags.stream().anyMatch(t -> "Resume".equals(t.get("name")) && t.get("description") != null));
    }

    @Test
    void problemSchemaListsEveryStableErrorCode() {
        List<String> documented = doc().getList("components.schemas.Problem.properties.code.enum");
        List<String> actual = java.util.Arrays.stream(
                dev.julioflores.cv.infrastructure.adapter.in.rest.error.ApiError.values()).map(Enum::name).toList();
        assertEquals(actual, documented, "el enum de `code` debe salir de ApiError");
        assertTrue(doc().getList("components.schemas.Problem.required").contains("code"));
        String description = doc().getString("components.schemas.Problem.properties.code.description");
        assertTrue(description.contains("`EXPERIENCE_NOT_FOUND` (404)"));
        assertTrue(description.contains("`UNEXPECTED` (500)"));
    }

    @Test
    void errorExamplesCarryTheStableCode() {
        String examples = "paths.'/api/v1/experience/{id}'.get.responses.'404'.content.'application/problem+json'.examples";
        assertEquals("EXPERIENCE_NOT_FOUND", doc().getString(examples + ".unknownId.value.code"));
        assertEquals("Experience not found", doc().getString(examples + ".unknownIdEn.value.title"));
        assertEquals("UNEXPECTED",
                doc().getString("paths.'/api/v1/profile'.get.responses.'500'.content.'application/problem+json'.example.code"));
    }

    // ── Demo HL7 (POST) ─────────────────────────────────────────────────────────

    private Map<String, Object> hl7() {
        return doc().getMap("paths.'" + HL7_PATH + "'.post");
    }

    @Test
    void hl7OperationIsDocumented() {
        assertEquals("translateHl7", hl7().get("operationId"));
        assertEquals(List.of("HL7"), hl7().get("tags"));
        assertNotNull(hl7().get("summary"));
        String description = (String) hl7().get("description");
        for (String needle : List.of("HL7 v2.5.1", "ADT^A01", "ORU^R01", "64 KB", "no se guarda")) {
            assertTrue(description.contains(needle), "la descripción no menciona " + needle);
        }
        List<Map<String, Object>> tags = doc().getList("tags");
        assertTrue(tags.stream().anyMatch(t -> "HL7".equals(t.get("name")) && t.get("description") != null));
    }

    @Test
    void hl7DocumentsItsRequestBodyAndFormatParameter() {
        String body = "paths.'" + HL7_PATH + "'.post.requestBody";
        assertEquals(Boolean.TRUE, doc().getBoolean(body + ".required"));
        assertNotNull(doc().get(body + ".description"));
        assertNotNull(doc().get(body + ".content.'text/plain'.examples.admission.value"));
        assertNotNull(doc().get(body + ".content.'text/plain'.examples.labResult.value"));

        List<Map<String, Object>> params = doc().getList("paths.'" + HL7_PATH + "'.post.parameters");
        Map<String, Object> format = params.stream().filter(p -> "format".equals(p.get("name"))).findFirst().orElseThrow();
        assertEquals("query", format.get("in"));
        assertNotNull(format.get("description"));
        List<String> names = params.stream().map(p -> (String) p.get("name")).toList();
        assertTrue(names.contains("lang") && names.contains("Accept-Language"));
    }

    @Test
    void hl7DocumentsEveryOutputWithExamples() {
        Map<String, Object> responses = doc().getMap("paths.'" + HL7_PATH + "'.post.responses");
        assertEquals(Set.of("200", "400", "413", "422", "500"), responses.keySet());
        for (String code : responses.keySet()) {
            String response = "paths.'" + HL7_PATH + "'.post.responses.'" + code + "'";
            assertNotNull(doc().get(response + ".description"), code);
            assertFalse(doc().getMap(response + ".content").isEmpty(), code);
        }
        for (String media : List.of("application/json", "application/fhir+json")) {
            String ok = "paths.'" + HL7_PATH + "'.post.responses.'200'.content.'" + media + "'.examples";
            assertNotNull(doc().get(ok + ".admission.value"), media);
            assertNotNull(doc().get(ok + ".labResult.value"), media);
        }
        String problems = "paths.'" + HL7_PATH + "'.post.responses.'%s'.content.'application/problem+json'";
        assertEquals("HL7_EMPTY_BODY", doc().getString(problems.formatted("400") + ".examples.emptyBody.value.code"));
        assertEquals("HL7_UNSUPPORTED_FORMAT", doc().getString(problems.formatted("400") + ".examples.unknownFormat.value.code"));
        assertEquals("HL7_PAYLOAD_TOO_LARGE", doc().getString(problems.formatted("413") + ".examples.tooLarge.value.code"));
        assertEquals("HL7_INVALID_MESSAGE", doc().getString(problems.formatted("422") + ".examples.notHl7.value.code"));
        assertEquals("HL7_UNSUPPORTED_MESSAGE", doc().getString(problems.formatted("422") + ".examples.unsupportedType.value.code"));
        assertEquals("UNEXPECTED", doc().getString(problems.formatted("500") + ".example.code"));
    }

    @Test
    void problemSchemaListsTheHl7Codes() {
        String description = doc().getString("components.schemas.Problem.properties.code.description");
        assertTrue(description.contains("`HL7_INVALID_MESSAGE` (422)"));
        assertTrue(description.contains("`HL7_PAYLOAD_TOO_LARGE` (413)"));
    }

    @Test
    void swaggerUiIsServedWhereTheDocsSayItIs() {
        given().when().get("/swagger/").then().statusCode(200);
    }

    @Test
    void testOnlyEndpointStaysOutOfTheDocument() {
        assertFalse(doc().<Map<String, Object>>getJsonObject("paths").containsKey("/test/boom"));
    }
}
