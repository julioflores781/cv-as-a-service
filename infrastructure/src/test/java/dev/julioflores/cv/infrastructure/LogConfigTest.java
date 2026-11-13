package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogConfigTest {

    private final Config config = ConfigProvider.getConfig();

    @Test
    void testsRunWithTextLogsThatShowTheRequestId() { // T23
        assertFalse(config.getValue("quarkus.log.console.json.enabled", Boolean.class));
        assertTrue(config.getValue("quarkus.log.console.format", String.class).contains("%X{request_id}"));
    }

    @Test
    void theAccessLogPatternNeverUsesTokensThatLeakTheQueryTheIpOrHeaders() { // extra fuera del plan: la garantía de privacidad del patrón
        String pattern = config.getValue("quarkus.http.access-log.pattern", String.class);
        for (String forbidden : new String[] {"%q", "%r", "%U", "%h", "%a", "%{i,", "%{ALL_REQUEST_HEADERS}", "%{c,", "%{q,"}) {
            assertFalse(pattern.contains(forbidden), forbidden + " in " + pattern);
        }
        assertEquals("access", config.getValue("quarkus.http.access-log.category", String.class));
    }
}
