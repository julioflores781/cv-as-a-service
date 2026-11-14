package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditOutcome;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * El recorrido completo de un administrador por HTTP, con {@code CF-Connecting-IP}: login bien y mal, cambio de contraseña, guardado
 * con comentario, rollback, subida, borrado, limpieza (ensayo y real), lectura de mensajes y cierre de todas las sesiones. Lo comparten
 * {@code AdminAuditLogTest} (T49, T52) y {@code SensitiveDataNotLoggedTest} (T50). Requiere el MinIO de pruebas y que quien lo llame
 * restaure {@code content_version} y el reloj.
 */
final class AdminAuditFlow {

    static final String BASE = "/api/v1/admin";
    static final String CONTENT = BASE + "/content/services";

    /** Valores que no pueden aparecer jamás en ningún registro. */
    record Canaries(String currentPassword, String newPassword, String comment, String title, String contactName,
            String contactEmail, String contactBody) {

        static Canaries unique() {
            return new Canaries(AdminTestSupport.PASSWORD, "CANARY-NEW-PASSWORD-9e1d4c7b2a", "CANARY-COMMENT-5b8f3a1c",
                    "CANARY-TITLE-77c2e9d1", "CANARY-NAME-Zora", "canary-mail-4d6e@example.com",
                    "CANARY-BODY-hola-esto-es-privado-31ab");
        }
    }

    /** Una acción del recorrido: lo que debe auditar, lo que el cliente recibió y los detalles exactos que se esperan (o {@code null}). */
    record Step(String label, AuditAction action, AuditOutcome outcome, Response response, Map<String, String> details) {
        String requestId() {
            return response.header("X-Request-Id");
        }
    }

    /** Todo lo que el recorrido tocó o recibió que no debe filtrarse a los logs. */
    record Result(List<Step> steps, List<String> secrets, List<Response> reads, String token) {}

    private AdminAuditFlow() {}

    static Result run(AdminTestSupport support, String ip, Canaries canaries) {
        List<Step> steps = new ArrayList<>();
        List<String> secrets = new ArrayList<>(List.of(canaries.currentPassword(), canaries.newPassword(), canaries.comment(),
                canaries.title(), canaries.contactName(), canaries.contactEmail(), canaries.contactBody(), "$argon2",
                support.queryString("SELECT password_hash FROM admin_account")));
        MinioSupport.clear();

        // 1. Login correcto.
        Response login = given().header("CF-Connecting-IP", ip).contentType(ContentType.JSON)
                .body("{\"password\":\"" + canaries.currentPassword() + "\"}").when().post(BASE + "/auth/login");
        login.then().statusCode(200);
        steps.add(new Step("login", AuditAction.LOGIN_SUCCESS, AuditOutcome.SUCCESS, login, Map.of()));
        String firstToken = login.path("accessToken");
        secrets.add(firstToken);

        // 2. Login incorrecto (una vez: no bloquea).
        Response bad = given().header("CF-Connecting-IP", ip).contentType(ContentType.JSON)
                .body("{\"password\":\"CANARY-WRONG-PASSWORD-0c5a\"}").when().post(BASE + "/auth/login");
        bad.then().statusCode(401);
        secrets.add("CANARY-WRONG-PASSWORD-0c5a");
        steps.add(new Step("bad login", AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, bad, Map.of("reason", "bad_credentials")));

        // 3. Cambio de contraseña (devuelve un token nuevo; el anterior muere).
        Response change = given().header("CF-Connecting-IP", ip).header("Authorization", "Bearer " + firstToken)
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", canaries.currentPassword(), "newPassword", canaries.newPassword()))
                .when().put(BASE + "/auth/password");
        change.then().statusCode(200);
        steps.add(new Step("password", AuditAction.PASSWORD_CHANGED, AuditOutcome.SUCCESS, change, Map.of()));
        String token = change.path("accessToken");
        secrets.add(token);
        secrets.add(support.queryString("SELECT password_hash FROM admin_account"));

        // 4. Guardado de contenido con comentario y un título único.
        Response current = auth(ip, token).when().get(CONTENT);
        current.then().statusCode(200);
        int es = current.path("languages.es.version");
        int en = current.path("languages.en.version");
        Map<String, Object> docEs = current.path("languages.es.content");
        Map<String, Object> docEn = current.path("languages.en.content");
        firstService(docEs).put("title", canaries.title());
        firstService(docEn).put("title", canaries.title());
        Response save = auth(ip, token).contentType(ContentType.JSON)
                .body(Map.of("baseVersions", Map.of("es", es, "en", en), "comment", canaries.comment(),
                        "documents", Map.of("es", docEs, "en", docEn)))
                .when().put(CONTENT);
        save.then().statusCode(200);
        steps.add(new Step("save", AuditAction.CONTENT_SAVED, AuditOutcome.SUCCESS, save, Map.of("kind", "services",
                "es_version", String.valueOf(es + 1), "en_version", String.valueOf(en + 1), "comment_present", "true",
                "comment_length", String.valueOf(canaries.comment().length()))));

        // 5. Rollback del español a la versión de antes.
        Response rollback = auth(ip, token).contentType(ContentType.JSON)
                .body(Map.of("baseVersion", es + 1, "targetVersion", es)).when().post(CONTENT + "/es/rollback");
        rollback.then().statusCode(200);
        steps.add(new Step("rollback", AuditAction.CONTENT_ROLLED_BACK, AuditOutcome.SUCCESS, rollback, Map.of("kind", "services",
                "lang", "es", "from_version", String.valueOf(es + 1), "to_version", String.valueOf(es))));

        // 6. Dos subidas, 7. borrado de una.
        Response first = upload(ip, token, ImageFiles.png(40, 30));
        first.then().statusCode(201);
        steps.add(new Step("upload", AuditAction.IMAGE_UPLOADED, AuditOutcome.SUCCESS, first, null));
        Response second = upload(ip, token, ImageFiles.jpeg(64, 48));
        second.then().statusCode(201);
        steps.add(new Step("upload 2", AuditAction.IMAGE_UPLOADED, AuditOutcome.SUCCESS, second, null));
        String key = first.path("key");
        String id = key.substring("site/projects/".length(), key.lastIndexOf('.'));
        Response delete = auth(ip, token).when().delete(BASE + "/images/" + id);
        delete.then().statusCode(204);
        steps.add(new Step("delete", AuditAction.IMAGE_DELETED, AuditOutcome.SUCCESS, delete, Map.of("id", id)));

        // 8. Limpieza: ensayo y real, con el reloj adelantado para que la imagen restante sea una huérfana vieja.
        TestClockConfig.advance(Duration.ofHours(48));
        Response dry = auth(ip, token).queryParam("dryRun", true).queryParam("olderThanHours", 24).when().post(BASE + "/images/cleanup");
        dry.then().statusCode(200);
        steps.add(new Step("cleanup dry", AuditAction.IMAGES_CLEANUP, AuditOutcome.SUCCESS, dry, Map.of("dry_run", "true",
                "older_than_hours", "24", "deleted", "0", "would_delete", "1", "kept", "0")));
        Response real = auth(ip, token).queryParam("dryRun", false).queryParam("olderThanHours", 24).when().post(BASE + "/images/cleanup");
        real.then().statusCode(200);
        steps.add(new Step("cleanup real", AuditAction.IMAGES_CLEANUP, AuditOutcome.SUCCESS, real, Map.of("dry_run", "false",
                "older_than_hours", "24", "deleted", "1", "would_delete", "0", "kept", "0")));

        // 9. Lectura de mensajes de contacto (con uno de verdad en la base).
        support.execute("DELETE FROM contact_message");
        support.execute("INSERT INTO contact_message (name, email, body, ip_hash) VALUES (?, ?, ?, 'IPHASH-CANARY-88e1')",
                canaries.contactName(), canaries.contactEmail(), canaries.contactBody());
        secrets.add("IPHASH-CANARY-88e1");
        Response messages = auth(ip, token).queryParam("limit", 10).when().get(BASE + "/contact-messages");
        messages.then().statusCode(200);
        if (!messages.asString().contains(canaries.contactBody())) {
            throw new AssertionError("el mensaje canario debería salir en la respuesta: " + messages.asString());
        }
        steps.add(new Step("messages", AuditAction.MESSAGES_READ, AuditOutcome.SUCCESS, messages,
                Map.of("limit", "10", "returned", "1", "paged", "false")));

        // Lecturas que NO auditan (T52): contenido, versiones, una versión, sesión, listado de imágenes.
        List<Response> reads = new ArrayList<>();
        reads.add(auth(ip, token).when().get(CONTENT));
        reads.add(auth(ip, token).when().get(CONTENT + "/es/versions"));
        reads.add(auth(ip, token).when().get(CONTENT + "/es/versions/1"));
        reads.add(auth(ip, token).when().get(BASE + "/auth/session"));
        reads.add(auth(ip, token).when().get(BASE + "/images"));
        reads.forEach(r -> r.then().statusCode(200));

        // 10. Cerrar todas las sesiones.
        Response logout = auth(ip, token).when().post(BASE + "/auth/logout-all");
        logout.then().statusCode(204);
        steps.add(new Step("logout-all", AuditAction.SESSIONS_REVOKED, AuditOutcome.SUCCESS, logout, Map.of()));
        return new Result(List.copyOf(steps), List.copyOf(secrets), List.copyOf(reads), token);
    }

    private static RequestSpecification auth(String ip, String token) {
        return given().header("CF-Connecting-IP", ip).header("Authorization", "Bearer " + token);
    }

    private static Response upload(String ip, String token, byte[] image) {
        return auth(ip, token).multiPart("file", "captura.png", image, "image/png").when().post(BASE + "/images");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstService(Map<String, Object> document) {
        return ((List<Map<String, Object>>) document.get("services")).get(0);
    }
}
