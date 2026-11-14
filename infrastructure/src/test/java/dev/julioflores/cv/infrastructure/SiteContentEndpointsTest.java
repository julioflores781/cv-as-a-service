package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Servicios, proyectos y arquitectura, con el contenido ficticio de db/sample. */
@QuarkusTest
class SiteContentEndpointsTest {

    @Inject
    AdminTestSupport support;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    @Test
    void servicesListPricedAndOnRequestOnes() {
        given().when().get("/api/v1/services")
                .then().statusCode(200)
                .header("Cache-Control", containsString("max-age=300"))
                .body("size()", equalTo(2))
                .body("[0].id", equalTo("servicio-ejemplo"))
                .body("[0].price.from", equalTo(1500))
                .body("[0].price.currency", equalTo("EUR"))
                .body("[1].price", nullValue());
    }

    @Test
    void servicesFollowTheRequestedLanguage() {
        given().queryParam("lang", "en").when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Sample service"));
        given().header("Accept-Language", "en-US,en;q=0.9").when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Sample service"));
        given().when().get("/api/v1/services")
                .then().statusCode(200).body("[0].title", equalTo("Servicio de ejemplo"));
    }

    @Test
    void publicProjectExposesItsClientAndLinks() {
        given().when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("[0].id", equalTo("proyecto-publico"))
                .body("[0].relation", equalTo("own"))
                .body("[0].disclosure", equalTo("public"))
                .body("[0].client", equalTo("Ejemplo S.A."))
                .body("[0].period.start", equalTo("2022-01"))
                .body("[0].period.current", equalTo(true))
                .body("[0].stack", hasItems("Java", "Quarkus"))
                .body("[0].links[0].label", equalTo("Ver el proyecto"));
    }

    @Test
    void anonymousProjectOnlyDescribesItsClient() {
        given().when().get("/api/v1/projects")
                .then().statusCode(200)
                .body("[1].id", equalTo("proyecto-anonimo"))
                .body("[1].disclosure", equalTo("anonymous"))
                .body("[1].client", nullValue())
                .body("[1].clientLabel", equalTo("prepaga de ejemplo"))
                .body("[1].period.end", equalTo("2020-11"))
                .body("[1].period.current", equalTo(false))
                .body("[1].highlights", empty());
    }

    @Test
    void architectureListsPortsWithTheirAdaptersAndHonestStatus() {
        given().when().get("/api/v1/architecture")
                .then().statusCode(200)
                .body("core.name", equalTo("Dominio"))
                .body("ports.size()", equalTo(2))
                .body("ports[0].id", equalTo("database"))
                .body("ports[0].adapters.id", hasItems("postgresql", "mongodb"))
                .body("ports[0].adapters[0].status", equalTo("in-this-api"))
                .body("ports[0].adapters[1].status", equalTo("production"))
                .body("ports[0].adapters[1].note", equalTo("Nota de ejemplo"))
                .body("ports[1].adapters[0].status", equalTo("experiment"))
                .body("ports[1].adapters[0].note", nullValue());
    }

    @Test
    void architectureInEnglish() {
        given().queryParam("lang", "en").when().get("/api/v1/architecture")
                .then().statusCode(200).body("core.name", equalTo("Domain")).body("ports[0].name", equalTo("Database"));
    }

    @Test
    void theRealClientNameOfAnonymousProjectsIsNotInTheResponseBody() {
        String body = given().when().get("/api/v1/projects").then().statusCode(200).extract().asString();
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("\"client\":\"prepaga")));
        org.hamcrest.MatcherAssert.assertThat(body, notNullValue());
    }

    @Test
    void postIsNotAllowed() {
        given().when().post("/api/v1/services").then().statusCode(405);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> document, String list, int index) {
        return ((List<Map<String, Object>>) document.get(list)).get(index);
    }

    private static Map<String, Object> stored(String token, String kind, String lang) {
        return given().header("Authorization", "Bearer " + token).when().get("/api/v1/admin/content/" + kind)
                .then().statusCode(200).extract().path("languages." + lang + ".content");
    }

    private static void save(String token, String kind, Map<String, Object> es, Map<String, Object> en) {
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", 1, "en", 1), "documents", Map.of("es", es, "en", en)))
                .when().put("/api/v1/admin/content/" + kind).then().statusCode(200);
    }

    @Test
    void savedServicesProjectsAndArchitectureShowUpInTheNextPublicReadWithDisclosureAndNoAnonymousClient()
            throws SQLException { // T116
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            Map<String, Object> servicesEs = stored(token, "services", "es");
            Map<String, Object> servicesEn = stored(token, "services", "en");
            item(servicesEs, "services", 0).put("title", "Servicio guardado");
            item(servicesEn, "services", 0).put("title", "Saved service");
            save(token, "services", servicesEs, servicesEn);

            Map<String, Object> projectsEs = stored(token, "projects", "es");
            Map<String, Object> projectsEn = stored(token, "projects", "en");
            item(projectsEs, "projects", 0).put("title", "Proyecto guardado");
            item(projectsEs, "projects", 1).put("clientLabel", "una prepaga guardada");
            item(projectsEn, "projects", 0).put("title", "Saved project");
            save(token, "projects", projectsEs, projectsEn);

            Map<String, Object> architectureEs = stored(token, "architecture", "es");
            Map<String, Object> architectureEn = stored(token, "architecture", "en");
            ((Map<String, Object>) architectureEs.get("core")).put("name", "Dominio guardado");
            save(token, "architecture", architectureEs, architectureEn);

            given().when().get("/api/v1/services").then().statusCode(200)
                    .body("[0].title", equalTo("Servicio guardado"))
                    .body("[0].price.from", equalTo(1500));
            given().queryParam("lang", "en").when().get("/api/v1/services").then().body("[0].title", equalTo("Saved service"));
            String projects = given().when().get("/api/v1/projects").then().statusCode(200)
                    .body("[0].title", equalTo("Proyecto guardado"))
                    .body("[0].disclosure", equalTo("public"))
                    .body("[0].client", equalTo("Ejemplo S.A."))
                    .body("[1].disclosure", equalTo("anonymous"))
                    .body("[1].clientLabel", equalTo("una prepaga guardada"))
                    .body("[1].client", nullValue())
                    .extract().asString();
            org.hamcrest.MatcherAssert.assertThat(projects, not(containsString("prepaga de ejemplo")));
            given().when().get("/api/v1/architecture").then().statusCode(200).body("core.name", equalTo("Dominio guardado"));
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
        }
    }

    @Test
    void anAnonymousClientWrittenStraightIntoTheDatabaseIsStillNeverExposed() throws SQLException { // T116 (segunda barrera)
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE content_version SET content = jsonb_set(content, '{projects,1,client}', "
                    + "'\"Nombre Filtrado S.A.\"') WHERE kind = 'projects' AND lang = 'es' AND active");
            cache.invalidate("projects", dev.julioflores.cv.domain.Language.ES);

            String body = given().when().get("/api/v1/projects").then().statusCode(200)
                    .body("[1].disclosure", equalTo("anonymous"))
                    .body("[1].client", nullValue())
                    .extract().asString();
            org.hamcrest.MatcherAssert.assertThat(body, not(containsString("Nombre Filtrado")));
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
        }
    }

    // ── Imágenes (api-admin-images) ────────────────────────────────────────────────

    private static final String K1 = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp";
    private static final String K2 = "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png";

    @Test
    void anOwnPublicProjectsSavedImagesComeOutAsUrlsInTheSavedOrder() throws SQLException { // T134
        support.resetAccount();
        String token = support.token(1);
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try {
            Map<String, Object> es = stored(token, "projects", "es");
            Map<String, Object> en = stored(token, "projects", "en");
            // El sample ya trae una imagen: se sustituye por dos, en otro orden, con el alt traducido.
            item(es, "projects", 0).put("images", List.of(
                    Map.of("key", K2, "alt", "Detalle", "width", 800, "height", 600),
                    Map.of("key", K1, "alt", "Portada", "width", 1280, "height", 720)));
            item(en, "projects", 0).put("images", List.of(
                    Map.of("key", K2, "alt", "Detail", "width", 800, "height", 600),
                    Map.of("key", K1, "alt", "Cover", "width", 1280, "height", 720)));
            save(token, "projects", es, en);

            given().when().get("/api/v1/projects").then().statusCode(200)
                    .body("[0].images.size()", equalTo(2))
                    .body("[0].images[0].url", equalTo("https://media.example.test/" + K2))
                    .body("[0].images[0].alt", equalTo("Detalle"))
                    .body("[0].images[0].width", equalTo(800))
                    .body("[0].images[0].height", equalTo(600))
                    .body("[0].images[1].url", equalTo("https://media.example.test/" + K1))
                    .body("[0].images[1].alt", equalTo("Portada"));
            given().queryParam("lang", "en").when().get("/api/v1/projects").then()
                    .body("[0].images[1].alt", equalTo("Cover"))
                    .body("[0].images[0].url", equalTo("https://media.example.test/" + K2));
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
        }
    }

    @Test
    void imagesStoredOnEmployerOrAnonymousProjectsNeverComeOut() throws SQLException { // T135
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            // Escritas directamente en la BD (se salta el validador): la segunda barrera es el dominio.
            String image = "'[{\"key\": \"" + K1 + "\", \"alt\": \"x\", \"width\": 10, \"height\": 10}]'::jsonb";
            s.execute("UPDATE content_version SET content = jsonb_set(content, '{projects,1,images}', " + image + ") "
                    + "WHERE kind = 'projects' AND active");
            s.execute("UPDATE content_version SET content = jsonb_set(jsonb_set(content, '{projects,0,relation}', '\"employer\"'), "
                    + "'{projects,0,images}', " + image + ") WHERE kind = 'projects' AND active");
            cache.invalidate("projects", dev.julioflores.cv.domain.Language.ES);

            String body = given().when().get("/api/v1/projects").then().statusCode(200)
                    .body("[0].relation", equalTo("employer"))
                    .body("[0].images", empty())
                    .body("[1].disclosure", equalTo("anonymous"))
                    .body("[1].images", empty())
                    .extract().asString();
            org.hamcrest.MatcherAssert.assertThat(body, not(containsString(K1)));
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
        }
    }

    @Test
    void projectsWithoutTheImagesFieldGiveAnEmptyListAndNothingFails() throws SQLException { // T136
        List<Object[]> backup = ContentVersions.snapshot(dataSource);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            // Datos «antiguos»: ningún proyecto trae el campo.
            s.execute("UPDATE content_version SET content = jsonb_set(content, '{projects,0}', (content #> '{projects,0}') - 'images') "
                    + "WHERE kind = 'projects' AND active");
            cache.invalidate("projects", dev.julioflores.cv.domain.Language.ES);

            given().when().get("/api/v1/projects").then().statusCode(200)
                    .body("[0].id", equalTo("proyecto-publico"))
                    .body("[0].images", empty())
                    .body("[1].images", empty());
        } finally {
            ContentVersions.restore(dataSource, backup, cache);
        }
    }

    @Test
    void theSampleProjectCarriesItsFictionalImageWithTheConfiguredPublicDomain() {
        given().when().get("/api/v1/projects").then().statusCode(200)
                .body("[0].images.size()", equalTo(1))
                .body("[0].images[0].url", equalTo("https://media.example.test/site/projects/00000000-0000-4000-8000-000000000001.webp"))
                .body("[0].images[0].width", equalTo(1280));
    }
}
