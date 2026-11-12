package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.domain.Language;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Efecto de la invalidación sobre los GET públicos, con el puerto a mano (T111 parcial) y con guardado y rollback reales (T111, T112). */
@QuarkusTest
class ContentCacheInvalidationTest {

    @Inject
    ContentCacheInvalidator invalidator;

    @Inject
    DataSource dataSource;

    @Inject
    AdminTestSupport support;

    // T111 (parte de este slug: puerto + GET público; el guardado real queda para api-admin-content)
    @Test
    void aNewActiveVersionShowsUpOnTheNextPublicReadOnlyAfterInvalidating() throws SQLException {
        given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio de ejemplo"));
        ContentVersions.publishServices(dataSource, "es", "Servicio nuevo");
        try {
            given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio de ejemplo"));

            invalidator.invalidate("services", Language.ES);

            given().queryParam("lang", "es").when().get("/api/v1/services").then().body("[0].title", equalTo("Servicio nuevo"));
        } finally {
            ContentVersions.revertServices(dataSource, "es");
            invalidator.invalidate("services", Language.ES);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicesDocument(String token, String lang, String title) {
        Map<String, Object> document = given().header("Authorization", "Bearer " + token).when()
                .get("/api/v1/admin/content/services").then().statusCode(200).extract().path("languages." + lang + ".content");
        ((List<Map<String, Object>>) document.get("services")).get(0).put("title", title);
        return document;
    }

    private static void publicTitle(String lang, String expected) {
        given().queryParam("lang", lang).when().get("/api/v1/services").then().statusCode(200).body("[0].title", equalTo(expected));
    }

    // T111: un guardado real; el siguiente GET público (sin esperar el TTL de 5 min) ya trae lo nuevo
    @Test
    void aRealSaveShowsUpOnTheVeryNextPublicRead() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            publicTitle("es", "Servicio de ejemplo"); // llena la caché con lo viejo
            publicTitle("en", "Sample service");

            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of(
                            "es", servicesDocument(token, "es", "Guardado ya"), "en", servicesDocument(token, "en", "Saved now"))))
                    .when().put("/api/v1/admin/content/services").then().statusCode(200);

            publicTitle("es", "Guardado ya");
            publicTitle("en", "Saved now");
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }

    // T112: un rollback real; el siguiente GET público ya trae lo restaurado
    @Test
    void aRealRollbackShowsUpOnTheVeryNextPublicRead() throws SQLException {
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of(
                            "es", servicesDocument(token, "es", "Guardado"), "en", servicesDocument(token, "en", "Saved"))))
                    .when().put("/api/v1/admin/content/services").then().statusCode(200);
            publicTitle("es", "Guardado"); // la caché ya tiene lo guardado

            given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                    .body(Map.of("baseVersion", 2, "targetVersion", 1))
                    .when().post("/api/v1/admin/content/services/es/rollback").then().statusCode(200);

            publicTitle("es", "Servicio de ejemplo");
            publicTitle("en", "Saved"); // solo se hizo rollback del español
        } finally {
            ContentVersions.restore(dataSource, backup, invalidator);
        }
    }
}
