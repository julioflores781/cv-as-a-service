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

    /** Contenido editable: GET con lista (o diagrama) vacía en vez de 404, por eso no entran en ENDPOINTS. */
    private static final Set<String> CONTENT_ENDPOINTS =
            Set.of("/api/v1/services", "/api/v1/projects", "/api/v1/architecture");

    /** Endpoints de lectura (GET). El POST de la demo HL7 se comprueba aparte. */
    private static final Set<String> ENDPOINTS = Set.of(
            "/api/v1/profile", "/api/v1/impact", "/api/v1/experience", "/api/v1/experience/{id}",
            "/api/v1/skills", "/api/v1/education", "/api/v1/contact", "/api/v1/resume");

    private static final String ADMIN_PREFIX = "/api/v1/admin/auth";
    private static final Set<String> ADMIN_ENDPOINTS = Set.of(
            ADMIN_PREFIX + "/login", ADMIN_PREFIX + "/session", ADMIN_PREFIX + "/password", ADMIN_PREFIX + "/logout-all");

    private static final String CONTENT = "/api/v1/admin/content/{kind}";
    private static final String CONTENT_VERSIONS = CONTENT + "/{lang}/versions";
    private static final String CONTENT_VERSION = CONTENT_VERSIONS + "/{version}";
    private static final String CONTENT_ROLLBACK = CONTENT + "/{lang}/rollback";
    private static final Set<String> ADMIN_CONTENT_ENDPOINTS =
            Set.of(CONTENT, CONTENT_VERSIONS, CONTENT_VERSION, CONTENT_ROLLBACK);

    private static final String IMAGES = "/api/v1/admin/images";
    private static final Set<String> ADMIN_IMAGES_ENDPOINTS = Set.of(IMAGES, IMAGES + "/{id}", IMAGES + "/cleanup");

    private static final String ADMIN_MESSAGES = "/api/v1/admin/contact-messages";

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
        expected.addAll(CONTENT_ENDPOINTS);
        expected.addAll(ADMIN_ENDPOINTS);
        expected.addAll(ADMIN_CONTENT_ENDPOINTS);
        expected.addAll(ADMIN_IMAGES_ENDPOINTS);
        expected.add(ADMIN_MESSAGES);
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
    void contentEndpointsAreDocumentedWithExamplesAndLanguageParameters() {
        for (String path : CONTENT_ENDPOINTS) {
            Map<String, Object> op = operation(path);
            assertNotNull(op.get("operationId"), path + " sin operationId");
            assertNotNull(op.get("summary"), path + " sin summary");
            assertNotNull(op.get("description"), path + " sin description");
            assertEquals(List.of("Content"), op.get("tags"), path);
            String media = "paths.'" + path + "'.get.responses.'200'.content.'application/json'";
            assertNotNull(doc().get(media + ".examples.es"), path + " sin ejemplo en español");
            assertNotNull(doc().get(media + ".examples.en"), path + " sin ejemplo en inglés");
            List<Map<String, Object>> params = doc().getList("paths.'" + path + "'.get.parameters");
            List<String> names = params.stream().map(p -> (String) p.get("name")).toList();
            assertTrue(names.contains("lang") && names.contains("Accept-Language"), path + " sin parámetros de idioma");
            assertEquals("#/components/schemas/Problem",
                    doc().getString("paths.'" + path + "'.get.responses.'500'.content.'application/problem+json'.schema.'$ref'"));
        }
        List<Map<String, Object>> tags = doc().getList("tags");
        assertTrue(tags.stream().anyMatch(t -> "Content".equals(t.get("name")) && t.get("description") != null));
    }

    @Test
    void anonymousProjectsDocumentThatTheClientIsNeverExposed() {
        String description = doc().getString("components.schemas.Project.properties.client.description");
        assertTrue(description.contains("`public`") && description.contains("nunca"), description);
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

    // ── Administración: auth (T118, parte auth) ─────────────────────────────────

    private Map<String, Object> adminOperation(String path) {
        Map<String, Object> item = doc().getMap("paths.'" + path + "'");
        assertEquals(1, item.size(), path + " debe tener un solo método");
        return doc().getMap("paths.'" + path + "'." + item.keySet().iterator().next());
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminAuthOperationsAreDocumentedWithTagSecurityAndOutputs() { // T118
        Map<String, Set<String>> expectedResponses = Map.of(
                ADMIN_PREFIX + "/login", Set.of("200", "400", "401", "429", "500"),
                ADMIN_PREFIX + "/session", Set.of("200", "401", "500"),
                ADMIN_PREFIX + "/password", Set.of("200", "400", "401", "403", "422", "429", "500"),
                ADMIN_PREFIX + "/logout-all", Set.of("204", "401", "500"));
        for (String path : ADMIN_ENDPOINTS) {
            Map<String, Object> op = adminOperation(path);
            assertNotNull(op.get("operationId"), path + " sin operationId");
            assertNotNull(op.get("summary"), path + " sin summary");
            assertTrue(((String) op.get("description")).length() > 30, path + " con descripción pobre");
            assertEquals(List.of("Admin"), op.get("tags"), path);
            boolean isLogin = path.endsWith("/login");
            assertEquals(!isLogin, op.get("security") != null, path + ": bearerAuth solo fuera del login");
            Map<String, Object> responses = (Map<String, Object>) op.get("responses");
            assertEquals(expectedResponses.get(path), responses.keySet(), path);
            responses.forEach((code, response) -> {
                assertNotNull(((Map<String, Object>) response).get("description"), path + " " + code + " sin descripción");
                if (!"204".equals(code)) {
                    assertFalse(((Map<String, Object>) ((Map<String, Object>) response).get("content")).isEmpty(),
                            path + " " + code + " sin contenido");
                }
            });
        }
        assertEquals("http", doc().getString("components.securitySchemes.bearerAuth.type"));
        assertEquals("bearer", doc().getString("components.securitySchemes.bearerAuth.scheme"));
        assertEquals("JWT", doc().getString("components.securitySchemes.bearerAuth.bearerFormat"));
        List<Map<String, Object>> tags = doc().getList("tags");
        assertTrue(tags.stream().anyMatch(t -> "Admin".equals(t.get("name")) && t.get("description") != null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminAuthErrorsUseProblemJsonWithExamplesAndRequestBodiesAreDocumented() { // T118
        for (String path : ADMIN_ENDPOINTS) {
            Map<String, Object> op = adminOperation(path);
            Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) op.get("responses");
            responses.forEach((code, response) -> {
                if (code.compareTo("400") < 0) {
                    return;
                }
                Map<String, Object> media = (Map<String, Object>) ((Map<String, Object>) response.get("content"))
                        .get("application/problem+json");
                assertNotNull(media, path + " " + code + " no es problem+json");
                assertEquals("#/components/schemas/Problem", ((Map<String, Object>) media.get("schema")).get("$ref"),
                        path + " " + code);
                assertTrue(media.get("example") != null || media.get("examples") != null, path + " " + code + " sin ejemplo");
            });
        }
        for (String path : List.of(ADMIN_PREFIX + "/login", ADMIN_PREFIX + "/password")) {
            assertNotNull(adminOperation(path).get("requestBody"), path + " sin requestBody");
        }
        assertNotNull(doc().get("paths.'" + ADMIN_PREFIX + "/login'.post.responses.'429'.headers.'Retry-After'.description"));
        assertNotNull(doc().get("paths.'" + ADMIN_PREFIX + "/session'.get.responses.'200'.headers.'Cache-Control'.description"));
        assertNotNull(doc().get("paths.'" + ADMIN_PREFIX + "/session'.get.responses.'401'.headers.'WWW-Authenticate'.description"));
        assertNotNull(doc().get("components.schemas.AdminLoginRequest.properties.password.description"));
        assertNotNull(doc().get("components.schemas.AdminPasswordChangeRequest.properties.newPassword.description"));
        assertNotNull(doc().get("components.schemas.AdminToken.properties.accessToken.description"));
    }

    @Test
    void problemSchemaListsTheAdminCodes() { // T118
        String description = doc().getString("components.schemas.Problem.properties.code.description");
        assertTrue(description.contains("`ADMIN_UNAUTHORIZED` (401)"));
        assertTrue(description.contains("`ADMIN_LOGIN_LOCKED` (429)"));
        assertTrue(description.contains("`ADMIN_WEAK_PASSWORD` (422)"));
    }

    // ── Administración: contenido (T120 y la documentación de las rutas nuevas) ──────

    @SuppressWarnings("unchecked")
    @Test
    void problemSchemaDocumentsTheErrorsListAndTheContentCodes() { // T120
        assertNotNull(doc().get("components.schemas.Problem.properties.errors.description"));
        assertEquals("array", doc().getString("components.schemas.Problem.properties.errors.type"));
        assertFalse(doc().getList("components.schemas.Problem.required").contains("errors"), "errors es opcional");
        assertNotNull(doc().get("components.schemas.ProblemError.properties.path.description"));
        assertNotNull(doc().get("components.schemas.ProblemError.properties.code.description"));
        List<String> codes = doc().getList("components.schemas.Problem.properties.code.enum");
        for (String code : List.of("CONTENT_VERSION_CONFLICT", "CONTENT_VERSION_NOT_FOUND", "CONTENT_INVALID",
                "CONTENT_PARITY_BROKEN", "CONTENT_TOO_LARGE")) {
            assertTrue(codes.contains(code), code);
        }
        String description = doc().getString("components.schemas.Problem.properties.code.description");
        assertTrue(description.contains("`CONTENT_VERSION_CONFLICT` (409)"));
        assertTrue(description.contains("`CONTENT_INVALID` (422)"));
        assertTrue(description.contains("`CONTENT_TOO_LARGE` (413)"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminContentOperationsAreDocumentedWithTagSecurityRequestsAndEveryOutput() {
        Map<String, Set<String>> expected = Map.of(
                "get " + CONTENT, Set.of("200", "401", "404", "500"),
                "put " + CONTENT, Set.of("200", "400", "401", "404", "409", "413", "422", "500"),
                "get " + CONTENT_VERSIONS, Set.of("200", "401", "404", "500"),
                "get " + CONTENT_VERSION, Set.of("200", "401", "404", "500"),
                "post " + CONTENT_ROLLBACK, Set.of("200", "400", "401", "404", "409", "422", "500"));
        Set<String> operationIds = new HashSet<>();
        expected.forEach((key, codes) -> {
            String[] parts = key.split(" ");
            String op = "paths.'" + parts[1] + "'." + parts[0];
            assertNotNull(doc().get(op + ".operationId"), key);
            assertTrue(operationIds.add(doc().getString(op + ".operationId")), "operationId repetido en " + key);
            assertNotNull(doc().get(op + ".summary"), key);
            assertTrue(doc().getString(op + ".description").length() > 30, key);
            assertEquals(List.of("Admin"), doc().getList(op + ".tags"), key);
            assertNotNull(doc().get(op + ".security"), key + " sin bearerAuth");
            Map<String, Object> responses = doc().getMap(op + ".responses");
            assertEquals(codes, responses.keySet(), key);
            responses.forEach((code, response) -> {
                assertNotNull(((Map<String, Object>) response).get("description"), key + " " + code);
                Map<String, Object> content = (Map<String, Object>) ((Map<String, Object>) response).get("content");
                assertFalse(content.isEmpty(), key + " " + code + " sin contenido");
                content.forEach((media, definition) -> assertTrue(((Map<String, Object>) definition).get("example") != null
                        || ((Map<String, Object>) definition).get("examples") != null, key + " " + code + " sin ejemplo"));
                if (code.compareTo("400") >= 0) {
                    assertNotNull(content.get("application/problem+json"), key + " " + code + " no es problem+json");
                }
            });
            assertNotNull(doc().get(op + ".responses.'200'.headers.'Cache-Control'.description"), key);
            if (parts[0].equals("put") || parts[0].equals("post")) {
                assertNotNull(doc().get(op + ".requestBody.content.'application/json'.examples"), key + " sin ejemplo de petición");
                assertEquals(Boolean.TRUE, doc().getBoolean(op + ".requestBody.required"), key);
            }
        });
        assertNotNull(doc().get("paths.'" + CONTENT + "'.put.responses.'422'.content.'application/problem+json'.examples.invalid"));
        assertNotNull(doc().get("paths.'" + CONTENT + "'.put.responses.'422'.content.'application/problem+json'.examples.parityBroken"));
        assertNotNull(doc().get("paths.'" + CONTENT + "'.get.responses.'200'.content.'application/json'.examples.nothingPublished"));
        List<Map<String, Object>> params = doc().getList("paths.'" + CONTENT_VERSIONS + "'.get.parameters");
        for (String name : List.of("kind", "lang", "limit")) {
            assertTrue(params.stream().anyMatch(p -> name.equals(p.get("name")) && p.get("description") != null), name);
        }
        for (String schema : List.of("AdminContent", "AdminContentSave", "AdminContentVersions", "AdminContentSnapshot",
                "AdminContentRollback", "AdminContentParity", "AdminContentLanguage")) {
            assertNotNull(doc().get("components.schemas." + schema + ".description"), schema + " sin descripción");
            Map<String, Map<String, Object>> props = doc().getMap("components.schemas." + schema + ".properties");
            props.forEach((prop, def) -> assertTrue(def.containsKey("description") || def.containsKey("$ref")
                    || def.containsKey("example"), schema + "." + prop + " sin documentar"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminImageOperationsAreDocumentedWithTagSecurityMultipartAndEveryOutput() {
        Map<String, Set<String>> expected = Map.of(
                "post " + IMAGES, Set.of("201", "400", "401", "413", "415", "500", "503"),
                "get " + IMAGES, Set.of("200", "401", "500", "503"),
                "delete " + IMAGES + "/{id}", Set.of("204", "401", "404", "409", "500", "503"),
                "post " + IMAGES + "/cleanup", Set.of("200", "401", "500", "503"));
        Set<String> operationIds = new HashSet<>();
        expected.forEach((key, codes) -> {
            String[] parts = key.split(" ");
            String op = "paths.'" + parts[1] + "'." + parts[0];
            assertTrue(operationIds.add(doc().getString(op + ".operationId")), "operationId repetido en " + key);
            assertNotNull(doc().get(op + ".summary"), key);
            assertTrue(doc().getString(op + ".description").length() > 30, key);
            assertEquals(List.of("Admin"), doc().getList(op + ".tags"), key);
            assertNotNull(doc().get(op + ".security"), key + " sin bearerAuth");
            Map<String, Object> responses = doc().getMap(op + ".responses");
            assertEquals(codes, responses.keySet(), key);
            responses.forEach((code, response) -> {
                assertNotNull(((Map<String, Object>) response).get("description"), key + " " + code);
                if (code.equals("204")) {
                    return;
                }
                Map<String, Object> content = (Map<String, Object>) ((Map<String, Object>) response).get("content");
                assertFalse(content.isEmpty(), key + " " + code + " sin contenido");
                content.forEach((media, definition) -> assertTrue(((Map<String, Object>) definition).get("example") != null
                        || ((Map<String, Object>) definition).get("examples") != null, key + " " + code + " sin ejemplo"));
                if (code.compareTo("400") >= 0) {
                    assertNotNull(content.get("application/problem+json"), key + " " + code + " no es problem+json");
                }
            });
        });
        String upload = "paths.'" + IMAGES + "'.post.requestBody";
        assertEquals(Boolean.TRUE, doc().getBoolean(upload + ".required"));
        assertNotNull(doc().get(upload + ".content.'multipart/form-data'.schema"));
        assertNotNull(doc().get("components.schemas.AdminImageUploadForm.properties.file.description"));
        for (String schema : List.of("AdminImageUploaded", "AdminImageEntry", "AdminImageList", "AdminImageCleanup",
                "AdminImageUploadForm", "ProjectImage")) {
            assertNotNull(doc().get("components.schemas." + schema + ".description"), schema + " sin descripción");
            Map<String, Map<String, Object>> props = doc().getMap("components.schemas." + schema + ".properties");
            props.forEach((prop, def) -> assertTrue(def.containsKey("description") || def.containsKey("$ref")
                    || def.containsKey("example"), schema + "." + prop + " sin documentar"));
        }
        assertNotNull(doc().get("components.schemas.Project.properties.images.description"));
        for (String name : List.of("dryRun", "olderThanHours")) {
            List<Map<String, Object>> params = doc().getList("paths.'" + IMAGES + "/cleanup'.post.parameters");
            assertTrue(params.stream().anyMatch(p -> name.equals(p.get("name")) && p.get("description") != null), name);
        }
    }

    @Test
    void imageErrorCodesAreInTheProblemSchema() {
        List<String> codes = doc().getList("components.schemas.Problem.properties.code.enum");
        assertTrue(codes.containsAll(List.of("IMAGE_INVALID", "IMAGE_TOO_LARGE", "IMAGE_UNSUPPORTED_TYPE",
                "IMAGE_STORAGE_UNAVAILABLE", "IMAGE_NOT_FOUND", "IMAGE_IN_USE")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminContactMessagesOperationIsDocumentedWithTagSecurityParametersAndEveryOutput() {
        String op = "paths.'" + ADMIN_MESSAGES + "'.get";
        assertEquals(Set.of("get"), doc().getMap("paths.'" + ADMIN_MESSAGES + "'").keySet());
        assertEquals("adminListContactMessages", doc().getString(op + ".operationId"));
        assertTrue(doc().getString(op + ".description").length() > 30);
        assertEquals(List.of("Admin"), doc().getList(op + ".tags"));
        assertNotNull(doc().get(op + ".security"));
        Map<String, Object> responses = doc().getMap(op + ".responses");
        assertEquals(Set.of("200", "400", "401", "500"), responses.keySet());
        responses.forEach((code, response) -> {
            Map<String, Object> content = (Map<String, Object>) ((Map<String, Object>) response).get("content");
            assertFalse(content.isEmpty(), code);
            content.forEach((media, definition) -> assertTrue(((Map<String, Object>) definition).get("example") != null
                    || ((Map<String, Object>) definition).get("examples") != null, code + " sin ejemplo"));
        });
        List<Map<String, Object>> params = doc().getList(op + ".parameters");
        for (String name : List.of("limit", "before")) {
            assertTrue(params.stream().anyMatch(p -> name.equals(p.get("name")) && p.get("description") != null), name);
        }
        for (String schema : List.of("AdminContactMessage", "AdminContactMessageList")) {
            assertNotNull(doc().get("components.schemas." + schema + ".description"), schema);
            Map<String, Map<String, Object>> props = doc().getMap("components.schemas." + schema + ".properties");
            props.forEach((prop, def) -> assertTrue(def.containsKey("description") || def.containsKey("$ref"), schema + "." + prop));
        }
        Map<String, Object> messageProps = doc().getMap("components.schemas.AdminContactMessage.properties");
        assertEquals(Set.of("id", "name", "email", "body", "createdAt"), messageProps.keySet());
    }
}
