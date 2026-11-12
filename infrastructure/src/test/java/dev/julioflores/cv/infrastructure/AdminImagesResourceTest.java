package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/** Subida, listado, borrado y limpieza de imágenes (T128-T133, T138-T140) con la API real, la BD real y un MinIO. */
@QuarkusTest
@QuarkusTestResource(value = MinioTestResource.class, restrictToAnnotatedClass = true)
class AdminImagesResourceTest {

    private static final String IMAGES = "/api/v1/admin/images";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String KEY_PATTERN =
            "site/projects/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|gif)";

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
        MinioSupport.clear();
        TestClockConfig.reset();
    }

    @AfterEach
    void restore() throws SQLException {
        ContentVersions.restore(dataSource, backup, cache);
        TestClockConfig.reset();
    }

    private RequestSpecification auth() {
        return given().header("Authorization", "Bearer " + token);
    }

    private Response upload(String fileName, String declaredType, byte[] content) {
        return auth().multiPart("file", fileName, content, declaredType).when().post(IMAGES);
    }

    private static HeadObjectResponse head(String key) {
        return MinioSupport.client().headObject(HeadObjectRequest.builder().bucket(MinioSupport.BUCKET).key(key).build());
    }

    private static String uuidOf(String key) {
        return key.substring("site/projects/".length(), key.lastIndexOf('.'));
    }

    // ── Subida ─────────────────────────────────────────────────────────────────

    @Test // T128
    void aRealPngIsStoredAndAnswers201WithKeyUrlTypeSizeAndDimensions() {
        byte[] png = ImageFiles.png(120, 80);

        Response response = upload("captura.png", "image/png", png);

        response.then().statusCode(201)
                .header("Cache-Control", "no-store")
                .body("key", matchesPattern(KEY_PATTERN))
                .body("contentType", equalTo("image/png"))
                .body("bytes", equalTo(png.length))
                .body("width", equalTo(120))
                .body("height", equalTo(80));
        String key = response.path("key");
        assertTrue(key.endsWith(".png"));
        assertEquals(MinioSupport.PUBLIC_URL + "/" + key, response.path("url"));
        HeadObjectResponse stored = head(key);
        assertEquals("image/png", stored.contentType());
        assertEquals("public, max-age=31536000, immutable", stored.cacheControl());
        assertEquals(png.length, stored.contentLength());
    }

    @Test
    void jpegWebpAndGifAreAcceptedWithTheirOwnExtensionAndType() {
        record Case(byte[] bytes, String extension, String type, int width, int height) {}
        for (Case c : List.of(
                new Case(ImageFiles.jpeg(64, 48), "jpg", "image/jpeg", 64, 48),
                new Case(ImageFiles.webp(1280, 720), "webp", "image/webp", 1280, 720),
                new Case(ImageFiles.gif(30, 20), "gif", "image/gif", 30, 20))) {
            upload("x", "application/octet-stream", c.bytes()).then().statusCode(201)
                    .body("key", matchesPattern(KEY_PATTERN))
                    .body("contentType", equalTo(c.type()))
                    .body("width", equalTo(c.width()))
                    .body("height", equalTo(c.height()));
        }
        assertEquals(3, MinioSupport.keys("site/projects/").size());
    }

    @Test
    void theClientsNameAndDeclaredTypeAreIgnoredByTheKeyAndTheStoredType() {
        // Un PNG real con nombre peligroso y tipo declarado falso: sale con clave .png del servidor y tipo image/png.
        Response response = upload("../../etc/x.php", "text/html", ImageFiles.png(10, 10));

        response.then().statusCode(201).body("contentType", equalTo("image/png"));
        String key = response.path("key");
        assertTrue(key.matches(KEY_PATTERN) && key.endsWith(".png") && !key.contains("x.php") && !key.contains(".."), key);
        assertEquals("image/png", head(key).contentType());
    }

    @Test // T129
    void aJpgThatIsReallyHtmlOrSvgIsRejectedAndNothingIsStored() {
        byte[] html = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        upload("foto.jpg", "image/jpeg", html).then().statusCode(400)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_INVALID"));
        upload("foto.jpg", "image/jpeg", svg).then().statusCode(415)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_UNSUPPORTED_TYPE"));
        upload("foto.jpg", "image/jpeg", new byte[0]).then().statusCode(400).body("code", equalTo("IMAGE_INVALID"));
        upload("foto.jpg", "image/jpeg", new byte[8]).then().statusCode(400).body("code", equalTo("IMAGE_INVALID"));

        assertEquals(List.of(), MinioSupport.keys(""));
    }

    @Test // T130
    void aFileOverFiveMegabytesIs413ImageTooLargeAndLeavesNoObject() {
        upload("grande.jpg", "image/jpeg", ImageFiles.jpegOfSize(5 * 1024 * 1024 + 1)).then().statusCode(413)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_TOO_LARGE"))
                .body("detail", containsString("5 MB"));

        assertEquals(List.of(), MinioSupport.keys(""));
        // El límite es inclusivo: 5 MB justos entran.
        upload("justo.jpg", "image/jpeg", ImageFiles.jpegOfSize(5 * 1024 * 1024)).then().statusCode(201);
        assertEquals(1, MinioSupport.keys("site/projects/").size());
    }

    @Test
    void aBodyOverTheSixMegabyteServerCeilingIsRejectedAndNothingIsStored() {
        // Con 7 MB por enviar el servidor responde 413 y cierra: según el momento el cliente ve el 413 o el corte (RawHttp
        // devuelve 413 en los dos casos). Lo estable es que se rechaza y no queda ningún objeto.
        byte[] head = ("--XX\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\r\n--XX--\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + 7 * 1024 * 1024 + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(tail, 0, body, body.length - tail.length, tail.length);

        assertEquals(413, RawHttp.postWithLengthAuthorized(imagesUri, "multipart/form-data; boundary=XX", "Bearer " + token, body));
        assertEquals(List.of(), MinioSupport.keys(""));
    }

    @TestHTTPResource(IMAGES)
    URI imagesUri;

    @Test
    void aChunkedBodyThatNeverDeclaredItsLengthAndPassesSixMegabytesIsStillImageTooLarge() {
        byte[] head = ("--XX\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\r\n--XX--\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + 7 * 1024 * 1024 + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(tail, 0, body, body.length - tail.length, tail.length);

        assertEquals(413, RawHttp.postChunkedAuthorized(imagesUri, "multipart/form-data; boundary=XX", "Bearer " + token, body));
        assertEquals(List.of(), MinioSupport.keys(""));
    }

    @Test // T131
    void withoutTheFilePartOrWithTwoOfThemItIsImageInvalid() {
        auth().multiPart("otra", "algo").when().post(IMAGES).then().statusCode(400)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_INVALID"));
        auth().multiPart("file", "a.png", ImageFiles.png(5, 5), "image/png")
                .multiPart("file", "b.png", ImageFiles.png(5, 5), "image/png")
                .when().post(IMAGES).then().statusCode(400).body("code", equalTo("IMAGE_INVALID"));
        assertEquals(List.of(), MinioSupport.keys(""));
    }

    @Test
    void aRequestThatIsNotMultipartIsImageInvalidOnceAuthenticated() {
        auth().contentType(ContentType.JSON).body("{}").when().post(IMAGES).then().statusCode(400)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_INVALID"));
    }

    @Test // T132
    void theTemporaryFileOfAnUploadIsGoneWhenTheRequestEnds() throws Exception {
        Set<String> before = uploadFiles();
        upload("captura.png", "image/png", ImageFiles.png(200, 100)).then().statusCode(201);
        upload("falsa.jpg", "image/jpeg", "no soy una imagen".getBytes(StandardCharsets.UTF_8)).then().statusCode(400);
        upload("grande.jpg", "image/jpeg", ImageFiles.jpegOfSize(5 * 1024 * 1024 + 1)).then().statusCode(413);

        assertTrue(noNewUploadFiles(before), "quedaron archivos temporales en " + UPLOADS);
    }

    private static final Path UPLOADS = Path.of("target/test-uploads");

    private static Set<String> uploadFiles() throws IOException {
        if (!Files.exists(UPLOADS)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(UPLOADS)) {
            return files.map(f -> f.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    /** Espera a que no quede ningún archivo temporal nuevo respecto a {@code before} (se borran al terminar la petición). */
    private static boolean noNewUploadFiles(Set<String> before) throws IOException, InterruptedException {
        for (int i = 0; i < 40; i++) {
            Set<String> now = new HashSet<>(uploadFiles());
            now.removeAll(before);
            if (now.isEmpty()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    @Test // T133
    void withoutATokenEveryImageRouteIs401BeforeAnythingIsRead() throws Exception {
        Set<String> before = uploadFiles();
        byte[] png = ImageFiles.png(50, 50);
        given().multiPart("file", "a.png", png, "image/png").when().post(IMAGES).then().statusCode(401)
                .contentType(PROBLEM_JSON).body("code", equalTo("ADMIN_UNAUTHORIZED"))
                .header("WWW-Authenticate", containsString("Bearer"));
        given().multiPart("file", "a.png", ImageFiles.jpegOfSize(3 * 1024 * 1024), "image/jpeg")
                .when().post(IMAGES).then().statusCode(401);
        given().when().get(IMAGES).then().statusCode(401);
        given().when().delete(IMAGES + "/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa").then().statusCode(401);
        given().when().post(IMAGES + "/cleanup").then().statusCode(401);
        given().header("Authorization", "Bearer basura").multiPart("file", "a.png", png, "image/png")
                .when().post(IMAGES).then().statusCode(401);

        assertEquals(List.of(), MinioSupport.keys(""));
        assertTrue(noNewUploadFiles(before));
    }

    // ── Referencias: guardar, borrar, listar, limpiar ───────────────────────────────

    private static final String K1 = "site/projects/00000000-0000-4000-8000-0000000000a1.png";
    private static final String K2 = "site/projects/00000000-0000-4000-8000-0000000000a2.png";
    private static final String K3 = "site/projects/00000000-0000-4000-8000-0000000000a3.png";

    private static Map<String, Object> image(String key, String alt) {
        return Map.of("key", key, "alt", alt, "width", 100, "height", 50);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> project(Map<String, Object> document, int index) {
        return ((List<Map<String, Object>>) document.get("projects")).get(index);
    }

    /** Guarda proyectos con esas imágenes en el primer proyecto (propio y público) en ES y EN; devuelve la respuesta del PUT. */
    private Response saveProjectImages(int base, String... keys) {
        Map<String, Object> es = auth().when().get("/api/v1/admin/content/projects").path("languages.es.content");
        Map<String, Object> en = auth().when().get("/api/v1/admin/content/projects").path("languages.en.content");
        List<Map<String, Object>> imagesEs = new ArrayList<>();
        List<Map<String, Object>> imagesEn = new ArrayList<>();
        for (String key : keys) {
            imagesEs.add(image(key, "Captura"));
            imagesEn.add(image(key, "Screenshot"));
        }
        project(es, 0).put("images", imagesEs);
        project(en, 0).put("images", imagesEn);
        return auth().contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", base, "en", base), "documents", Map.of("es", es, "en", en)))
                .when().put("/api/v1/admin/content/projects");
    }

    @Test // T138
    void anImageReferencedOnlyByAHistoricVersionIs409AndWithoutReferencesItIs204AndGone() throws SQLException {
        MinioSupport.put(K1, ImageFiles.png(10, 10));
        // v2 referencia K1; v3 ya no. K1 solo vive en la versión histórica (la 2).
        saveProjectImages(1, K1).then().statusCode(200);
        saveProjectImages(2).then().statusCode(200);

        auth().when().delete(IMAGES + "/" + uuidOf(K1)).then().statusCode(409)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_IN_USE"));
        assertEquals(List.of(K1), MinioSupport.keys("site/projects/"));

        // Sin referencias (se quitan las versiones que la citaban): 204 y el objeto desaparece.
        support.execute("DELETE FROM content_version WHERE kind = 'projects' AND version = 2");
        auth().when().delete(IMAGES + "/" + uuidOf(K1)).then().statusCode(204);
        assertEquals(List.of(), MinioSupport.keys("site/projects/"));
        auth().when().delete(IMAGES + "/" + uuidOf(K1)).then().statusCode(404).body("code", equalTo("IMAGE_NOT_FOUND"));
    }

    @Test
    void theDetailsOfTheDeleteErrorsNameTheImageAndLeaveNoUnsubstitutedPlaceholder() {
        String id = "9f1c2d7e-9999-4a2b-8c3d-aaaaaaaaaaaa";
        for (String lang : List.of("es", "en")) {
            String detail = auth().queryParam("lang", lang).when().delete(IMAGES + "/" + id).then().statusCode(404)
                    .body("code", equalTo("IMAGE_NOT_FOUND")).extract().path("detail");
            assertTrue(detail.contains(id) && !detail.contains("{"), lang + ": " + detail);
        }
        String key = "site/projects/" + id + ".png";
        MinioSupport.put(key, ImageFiles.png(10, 10));
        support.execute("UPDATE content_version SET content = jsonb_set(content, '{projects,0,images}', "
                + "'[{\"key\": \"" + key + "\", \"alt\": \"x\", \"width\": 1, \"height\": 1}]'::jsonb) "
                + "WHERE kind = 'projects' AND active");
        for (String lang : List.of("es", "en")) {
            String detail = auth().queryParam("lang", lang).when().delete(IMAGES + "/" + id).then().statusCode(409)
                    .body("code", equalTo("IMAGE_IN_USE")).extract().path("detail");
            assertTrue(detail.contains(key) && !detail.contains("{"), lang + ": " + detail);
        }
    }

    @Test
    void deletingWithAnIdThatIsNotAUuidIsNotFound() {
        auth().when().delete(IMAGES + "/no-es-un-uuid").then().statusCode(404)
                .contentType(PROBLEM_JSON).body("code", equalTo("IMAGE_NOT_FOUND"));
        auth().when().delete(IMAGES + "/9F1C2D7E-1111-4A2B-8C3D-AAAAAAAAAAAA").then().statusCode(404);
    }

    @Test // T139
    void theListSaysWhichImagesAreReferencedAndComesNewestFirst() throws InterruptedException {
        MinioSupport.put(K1, ImageFiles.png(10, 10));
        Thread.sleep(1100); // MinIO guarda la fecha con precisión de segundo
        MinioSupport.put(K2, ImageFiles.png(10, 10));
        saveProjectImages(1, K1).then().statusCode(200);

        auth().when().get(IMAGES).then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("images", hasSize(2))
                .body("images[0].key", equalTo(K2))
                .body("images[0].referenced", equalTo(false))
                .body("images[0].url", equalTo(MinioSupport.PUBLIC_URL + "/" + K2))
                .body("images[0].bytes", equalTo(ImageFiles.png(10, 10).length))
                .body("images[0].lastModified", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z"))
                .body("images[1].key", equalTo(K1))
                .body("images[1].referenced", equalTo(true));
    }

    @Test
    void anEmptyBucketListsNothing() {
        auth().when().get(IMAGES).then().statusCode(200).body("images", empty());
    }

    @Test // T140
    void cleanupDeletesOnlyOldOrphansAndADryRunDeletesNothing() {
        MinioSupport.put(K1, ImageFiles.png(10, 10)); // referenciada
        MinioSupport.put(K2, ImageFiles.png(10, 10)); // huérfana
        saveProjectImages(1, K1).then().statusCode(200);

        // Recién subidas: dentro de la gracia de 24 h no se toca nada.
        auth().queryParam("dryRun", false).when().post(IMAGES + "/cleanup").then().statusCode(200)
                .body("dryRun", equalTo(false)).body("deleted", empty()).body("wouldDelete", empty()).body("kept", equalTo(2));

        TestClockConfig.advance(Duration.ofHours(48));
        MinioSupport.put(K3, ImageFiles.png(10, 10)); // tan reciente como el reloj adelantado... pero escrita «ahora» real

        // Por defecto es un ensayo.
        auth().when().post(IMAGES + "/cleanup").then().statusCode(200)
                .body("dryRun", equalTo(true)).body("deleted", empty())
                .body("wouldDelete", hasItems(K2)).body("wouldDelete", not(hasItems(K1)));
        auth().queryParam("dryRun", true).queryParam("olderThanHours", 24).when().post(IMAGES + "/cleanup").then()
                .statusCode(200).body("dryRun", equalTo(true)).body("deleted", empty());
        assertEquals(3, MinioSupport.keys("site/projects/").size(), "un ensayo no borra nada");

        auth().queryParam("dryRun", false).queryParam("olderThanHours", 24).when().post(IMAGES + "/cleanup").then()
                .statusCode(200)
                .body("dryRun", equalTo(false))
                .body("wouldDelete", empty())
                .body("deleted", hasItems(K2))
                .body("deleted", not(hasItems(K1)));
        List<String> left = MinioSupport.keys("site/projects/");
        assertTrue(left.contains(K1), "la referenciada se conserva");
        assertTrue(!left.contains(K2), "la huérfana vieja se borra");
    }

    @Test
    void aGraceBelowOneHourIsRaisedSoNothingFreshIsDeleted() {
        MinioSupport.put(K1, ImageFiles.png(10, 10));

        auth().queryParam("dryRun", false).queryParam("olderThanHours", 0).when().post(IMAGES + "/cleanup").then()
                .statusCode(200).body("deleted", empty()).body("kept", equalTo(1));
        auth().queryParam("dryRun", false).queryParam("olderThanHours", -5).when().post(IMAGES + "/cleanup").then()
                .statusCode(200).body("deleted", empty());

        assertEquals(List.of(K1), MinioSupport.keys("site/projects/"));
    }
}
