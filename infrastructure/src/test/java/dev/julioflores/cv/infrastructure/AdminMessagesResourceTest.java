package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lectura de los mensajes de contacto desde la zona de administración (T142 y sus bordes), con la API y la BD reales. */
@QuarkusTest
class AdminMessagesResourceTest {

    private static final String PATH = "/api/v1/admin/contact-messages";
    private static final String PUBLIC_PATH = "/api/v1/contact/messages";
    private static final String BODY = "Hola, me gustaría hablar contigo sobre una vacante.";

    @Inject
    AdminTestSupport support;

    private String token;

    @BeforeEach
    void setUp() {
        support.resetAccount();
        token = support.token(1);
        support.execute("DELETE FROM contact_message");
    }

    private RequestSpecification admin() {
        return given().header("Authorization", "Bearer " + token);
    }

    /** Inserta con el id y la fecha dados (UTC) y un {@code ip_hash} reconocible para comprobar que no sale. */
    private void insert(String name, String email, String createdAt) {
        support.execute("INSERT INTO contact_message (name, email, body, ip_hash, created_at) VALUES (?, ?, ?, 'IPHASH-SECRETO', ?::timestamptz)",
                name, email, BODY, createdAt);
    }

    private void insertFive() {
        for (int i = 1; i <= 5; i++) {
            insert("Ada Ejemplo " + i, "ada" + i + "@example.com", "2026-11-1" + i + "T09:00:00Z");
        }
    }

    private List<Integer> ids(Response response) {
        return response.jsonPath().getList("messages.id");
    }

    @Test
    void messagesArePaginatedNewestFirstWithoutIpHashAndNeedAToken() { // T142
        // Mensajes enviados por la vía pública real, con su IP; luego se leen con el token.
        for (int i = 1; i <= 3; i++) {
            given().header("CF-Connecting-IP", "203.0.113." + (10 + i))
                    .contentType(ContentType.JSON)
                    .body("{\"name\":\"Ada Ejemplo " + i + "\",\"email\":\"ada" + i + "@example.com\",\"message\":\"" + BODY + " " + i
                            + "\",\"website\":\"\"}")
                    .when().post(PUBLIC_PATH).then().statusCode(201);
        }
        // Los tres remitentes sí guardaron su hash (no vacío, 64 hex de SHA-256, nunca la IP en claro): está en BD y aun así no sale.
        assertEquals(3, support.queryInt("SELECT count(*) FROM contact_message WHERE ip_hash ~ '^[0-9a-f]{64}$'"));
        assertEquals(3, support.queryInt("SELECT count(DISTINCT ip_hash) FROM contact_message"));
        assertEquals(0, support.queryInt("SELECT count(*) FROM contact_message WHERE ip_hash LIKE '%203.0.113%'"));
        Response first = admin().queryParam("limit", 2).when().get(PATH);
        first.then().statusCode(200).contentType("application/json")
                .header("Cache-Control", containsString("no-store"))
                .body("messages", hasSize(2))
                .body("messages[0].name", equalTo("Ada Ejemplo 3"))
                .body("messages[0].email", equalTo("ada3@example.com"))
                .body("messages[0].body", containsString("3"))
                .body("messages[0].createdAt", not(nullValue()))
                .body("messages[1].name", equalTo("Ada Ejemplo 2"));
        String next = first.jsonPath().getString("next");
        assertEquals(first.jsonPath().getString("messages[1].id"), next);

        Response second = admin().queryParam("limit", 2).queryParam("before", next).when().get(PATH);
        second.then().statusCode(200).body("messages", hasSize(1)).body("messages[0].name", equalTo("Ada Ejemplo 1"))
                .body("next", nullValue());

        for (Response r : List.of(first, second)) {
            String raw = r.asString();
            assertFalse(raw.contains("IPHASH") || raw.toLowerCase().contains("ip_hash") || raw.toLowerCase().contains("iphash")
                    || raw.contains("ipHash") || raw.contains("senderKey"), raw);
            r.jsonPath().getList("messages", java.util.Map.class)
                    .forEach(m -> assertEquals(java.util.Set.of("id", "name", "email", "body", "createdAt"), m.keySet()));
        }

        given().when().get(PATH).then().statusCode(401).body("code", equalTo("ADMIN_UNAUTHORIZED"));
        given().header("Authorization", "Bearer " + support.token(99)).when().get(PATH).then().statusCode(401);
    }

    @Test
    void theDefaultLimitIsFiftyAndAFullWalkVisitsEveryMessageOnce() {
        for (int i = 1; i <= 55; i++) {
            insert("Ada Ejemplo", "a" + i + "@example.com", "2026-11-01T00:00:00Z");
        }
        Response first = admin().when().get(PATH);
        first.then().statusCode(200).body("messages", hasSize(50));
        Response second = admin().queryParam("before", first.jsonPath().getString("next")).when().get(PATH);
        second.then().statusCode(200).body("messages", hasSize(5)).body("next", nullValue());
        List<Integer> all = new java.util.ArrayList<>(ids(first));
        all.addAll(ids(second));
        assertEquals(55, all.stream().distinct().count());
    }

    @Test
    void messagesWithTheSameTimestampKeepAStableOrderByIdDescending() {
        for (int i = 1; i <= 4; i++) {
            insert("Ada Ejemplo", "same" + i + "@example.com", "2026-11-12T09:00:00Z");
        }
        Response all = admin().when().get(PATH);
        List<Integer> ordered = ids(all);
        assertEquals(ordered.stream().sorted(java.util.Comparator.reverseOrder()).toList(), ordered);
        Response first = admin().queryParam("limit", 3).when().get(PATH);
        Response second = admin().queryParam("limit", 3).queryParam("before", first.jsonPath().getString("next")).when().get(PATH);
        List<Integer> walked = new java.util.ArrayList<>(ids(first));
        walked.addAll(ids(second));
        assertEquals(ordered, walked);
    }

    @Test
    void aNewerIdWithAnOlderDateStillFollowsTheDateOrder() {
        insert("Ada Ejemplo", "old@example.com", "2026-01-01T00:00:00Z");
        insert("Ada Ejemplo", "new@example.com", "2026-11-01T00:00:00Z");
        admin().when().get(PATH).then().statusCode(200)
                .body("messages.email", equalTo(List.of("new@example.com", "old@example.com")));
    }

    @Test
    void anEmptyTableGivesAnEmptyListAndNoCursor() {
        admin().when().get(PATH).then().statusCode(200).body("messages", empty()).body("next", nullValue());
    }

    @Test
    void aCursorThatDoesNotExistOrIsPastTheLastMessageGivesAnEmptyList() {
        insertFive();
        admin().queryParam("before", 999999999).when().get(PATH).then().statusCode(200)
                .body("messages", empty()).body("next", nullValue());
        String oldest = admin().when().get(PATH).jsonPath().getString("messages[4].id");
        admin().queryParam("before", oldest).when().get(PATH).then().statusCode(200).body("messages", empty());
        admin().queryParam("before", "99999999999999999999").when().get(PATH).then().statusCode(400).body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void anInvalidCursorIsABadRequestProblem() {
        insertFive();
        for (String before : List.of("-1", "0", "abc", "1.5", " ", "1;DROP TABLE contact_message")) {
            admin().queryParam("before", before).when().get(PATH).then().statusCode(400)
                    .contentType("application/problem+json").body("code", equalTo("BAD_REQUEST"));
        }
        assertEquals(5, support.queryInt("SELECT count(*) FROM contact_message"));
    }

    @Test
    void anInvalidOrOutOfRangeLimitIsABadRequestProblem() {
        insertFive();
        for (String limit : List.of("0", "-3", "101", "100000", "99999999999", "abc", "2.5", " ")) {
            admin().queryParam("limit", limit).when().get(PATH).then().statusCode(400)
                    .contentType("application/problem+json").body("code", equalTo("BAD_REQUEST"));
        }
        admin().queryParam("limit", 100).when().get(PATH).then().statusCode(200).body("messages", hasSize(5));
        admin().queryParam("limit", 1).when().get(PATH).then().statusCode(200).body("messages", hasSize(1));
    }

    @Test
    void theRouteIsReadOnlyAndRejectsOtherMethodsWithAToken() {
        admin().when().delete(PATH).then().statusCode(405);
        admin().contentType(ContentType.JSON).body("{}").when().post(PATH).then().statusCode(405);
    }

    @Test
    void anEmptyValueCountsAsAbsentAndUsesTheDefaults() { // decisión: RESTEasy entrega "" como ausente
        insertFive();
        admin().queryParam("limit", "").queryParam("before", "").when().get(PATH).then().statusCode(200)
                .body("messages", hasSize(5)).body("next", nullValue());
    }
}
