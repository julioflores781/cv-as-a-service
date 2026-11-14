package dev.julioflores.cv.infrastructure;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

/**
 * El formato JSON de producción, probado hacia un archivo: el perfil copia al {@code quarkus.log.file.json.*} las MISMAS claves
 * {@code quarkus.log.console.json.*} de {@code application.properties} (salvo {@code enabled}), así que valida su sintaxis real
 * ({@code key-overrides}, {@code excluded-keys}, {@code mdc.flat-fields}, campo adicional).
 */
@QuarkusTest
@TestProfile(JsonLogFormatTest.JsonToFile.class)
class JsonLogFormatTest {

    private static final Path LOG_FILE = Path.of("target", "json-log-test.log");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CONSOLE_PREFIX = "quarkus.log.console.json.";
    private static final List<String> EXCLUDED = List.of("sequence", "loggerClassName", "processName", "processId", "threadId", "hostName", "ndc");

    public static class JsonToFile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // El perfil y el test se cargan en cargadores distintos: el nombre del archivo es fijo y se borra antes de arrancar.
            try {
                Files.deleteIfExists(LOG_FILE);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Map<String, String> overrides = new HashMap<>();
            overrides.put("quarkus.log.file.rotation.rotate-on-boot", "false");
            overrides.put("quarkus.log.file.enabled", "true");
            overrides.put("quarkus.log.file.path", LOG_FILE.toString());
            overrides.put("quarkus.log.file.json.enabled", "true");
            Properties app = new Properties();
            try (InputStream in = JsonLogFormatTest.class.getResourceAsStream("/application.properties")) {
                app.load(in);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            app.stringPropertyNames().stream()
                    .filter(k -> k.startsWith(CONSOLE_PREFIX) && !k.equals(CONSOLE_PREFIX + "enabled") && !k.startsWith("%"))
                    .forEach(k -> overrides.put("quarkus.log.file.json." + k.substring(CONSOLE_PREFIX.length()), app.getProperty(k)));
            return overrides;
        }
    }

    /** Las líneas del archivo ya escritas; el logger vuelca al cerrar la respuesta, así que se espera con polling. */
    private static List<String> linesContaining(String text) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (true) {
            List<String> lines = Files.exists(LOG_FILE) ? Files.readAllLines(LOG_FILE) : List.of();
            if (lines.stream().anyMatch(l -> l.contains(text)) || System.nanoTime() > deadline) {
                return lines;
            }
            Thread.sleep(10);
        }
    }

    @Test
    void everyLineIsOneJsonObjectWithTheFlatFieldsAndWithoutTheExcludedKeys() throws Exception { // T21
        String rid = given().when().get("/api/v1/profile").header("X-Request-Id");
        String boomRid = given().when().get("/test/boom").header("X-Request-Id");
        List<String> lines = linesContaining("rid=" + rid);
        linesContaining("\"request_id\":\"" + boomRid + "\"");
        lines = Files.readAllLines(LOG_FILE);
        assertFalse(lines.isEmpty());

        List<JsonNode> requestLines = new ArrayList<>();
        for (String line : lines) {
            JsonNode node = JSON.readTree(line); // JSON válido, uno por línea (una traza no parte el registro)
            assertTrue(node.isObject(), line);
            for (String field : new String[] {"timestamp", "level", "message", "logger"}) {
                assertTrue(node.has(field), field + " missing in " + line);
            }
            assertEquals("cv-api", node.get("service").asText(), line);
            assertFalse(node.has("mdc"), "MDC anidado: " + line);
            assertFalse(node.has("loggerName"), "key-override no aplicado: " + line);
            for (String excluded : EXCLUDED) {
                assertFalse(node.has(excluded), excluded + " in " + line);
            }
            if (node.has("request_id")) {
                requestLines.add(node);
            }
        }
        assertTrue(requestLines.stream().anyMatch(n -> n.get("request_id").asText().equals(rid)), "request_id plano");

        JsonNode boom = requestLines.stream()
                .filter(n -> n.get("request_id").asText().equals(boomRid) && n.get("level").asText().equals("ERROR"))
                .findFirst().orElseThrow();
        assertEquals("Unexpected error", boom.get("message").asText());
        String trace = boom.get("stackTrace").asText();
        assertTrue(trace.contains("IllegalStateException") && trace.contains("\n"), "traza completa dentro de un campo de una línea de archivo");
    }

    @Test
    void theAccessLineInJsonHasNoIpAndItsMessageFollowsThePattern() throws Exception { // T22
        String rid = given().header("CF-Connecting-IP", "203.0.113.9").header("X-Client-IP", "203.0.113.10")
                .when().get("/api/v1/profile?lang=en&token=CANARY-Q").header("X-Request-Id");
        String line = linesContaining("rid=" + rid).stream().filter(l -> l.contains("rid=" + rid)).findFirst().orElseThrow();
        JsonNode node = JSON.readTree(line);
        assertEquals("access", node.get("logger").asText());
        Matcher m = AccessLogTest.LINE.matcher(node.get("message").asText());
        assertTrue(m.matches(), node.get("message").asText());
        assertEquals("-", m.group(8));
        assertEquals(rid, node.get("request_id").asText());
        assertFalse(line.contains("203.0.113"), line);
        assertFalse(line.contains("client_ip"), line);
        assertFalse(line.contains("CANARY-Q"), line);
    }
}
