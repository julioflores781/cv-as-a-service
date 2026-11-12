package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T127: con {@code R2_*} sin configurar (el valor por defecto de las pruebas, sin MinIO) la API arranca, la subida y el resto de
 * operaciones de imágenes dan 503 {@code IMAGE_STORAGE_UNAVAILABLE} y NADA más se entera (CA17): guardar contenido y leer lo público siguen.
 */
@QuarkusTest
class AdminImagesStorageUnavailableTest {

    private static final String IMAGES = "/api/v1/admin/images";

    @Inject
    AdminTestSupport support;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    private List<Object[]> backup;
    private String token;

    @BeforeEach
    void setUp() throws SQLException {
        support.resetAccount();
        token = support.token(1);
        backup = ContentVersions.snapshot(dataSource);
    }

    @AfterEach
    void restore() throws SQLException {
        ContentVersions.restore(dataSource, backup, cache);
    }

    @Test // T127
    void everyImageOperationIs503ImageStorageUnavailableAsAProblemInBothLanguages() {
        given().header("Authorization", "Bearer " + token).multiPart("file", "a.png", ImageFiles.png(10, 10), "image/png")
                .when().post(IMAGES).then().statusCode(503)
                .contentType("application/problem+json")
                .body("code", equalTo("IMAGE_STORAGE_UNAVAILABLE"))
                .body("title", equalTo("Almacenamiento de imágenes no disponible"));
        given().header("Authorization", "Bearer " + token).queryParam("lang", "en")
                .multiPart("file", "a.png", ImageFiles.png(10, 10), "image/png")
                .when().post(IMAGES).then().statusCode(503).body("title", equalTo("Image storage unavailable"));
        given().header("Authorization", "Bearer " + token).when().get(IMAGES).then().statusCode(503)
                .body("code", equalTo("IMAGE_STORAGE_UNAVAILABLE"));
        given().header("Authorization", "Bearer " + token).when().delete(IMAGES + "/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa")
                .then().statusCode(503).body("code", equalTo("IMAGE_STORAGE_UNAVAILABLE"));
        given().header("Authorization", "Bearer " + token).when().post(IMAGES + "/cleanup").then().statusCode(503);
    }

    @Test
    void savingContentAndReadingThePublicApiKeepWorkingWithoutStorage() {
        Map<String, Object> es = given().header("Authorization", "Bearer " + token)
                .when().get("/api/v1/admin/content/services").path("languages.es.content");
        Map<String, Object> en = given().header("Authorization", "Bearer " + token)
                .when().get("/api/v1/admin/content/services").path("languages.en.content");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of("es", es, "en", en)))
                .when().put("/api/v1/admin/content/services").then().statusCode(200);
        given().when().get("/api/v1/services").then().statusCode(200);
        given().when().get("/api/v1/projects").then().statusCode(200);
        given().when().get("/q/health/ready").then().statusCode(200);
    }
}
