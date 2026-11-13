package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.audit.AuditOutcome;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    private static AuditEvent event(Map<String, String> details) {
        return new AuditEvent(AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, details);
    }

    @Test
    void lineBreaksAndControlCharactersCannotForgeASecondLineOrField() { // T47
        AuditEvent event = event(Map.of("reason", "bad\r\naction=LOGIN_SUCCESS outcome=success\t\u0000\u001b[31m x\u0085y"));
        String value = event.details().get("reason");
        assertEquals("bad__action=LOGIN_SUCCESS_outcome=success___[31m_x_y", value);
        for (char c : value.toCharArray()) {
            assertFalse(Character.isISOControl(c) || Character.isWhitespace(c) || c == ' ' || c == '\u0085', "queda " + (int) c);
        }
        assertFalse(event.toString().contains("\n") || event.toString().contains("\r"));
    }

    @Test
    void valuesAreTruncatedTo120CharactersWithoutHalfASurrogatePair() { // T47
        assertEquals(120, event(Map.of("k", "x".repeat(500))).details().get("k").length());
        assertEquals("x".repeat(120), event(Map.of("k", "x".repeat(120))).details().get("k"));
        // La pareja suplente cae justo en el límite: no se deja la mitad.
        String cut = event(Map.of("k", "x".repeat(119) + "😀")).details().get("k");
        assertEquals(119, cut.length());
    }

    @Test
    void nullAndOddKeysAreHandledAndTheDetailsAreSortedAndImmutable() { // T47
        Map<String, String> raw = new HashMap<>();
        raw.put(null, "v");
        raw.put("  ", "v");
        raw.put("b k\n=", "2");
        raw.put("a", null);
        AuditEvent event = event(raw);
        assertEquals(List.of("a", "b_k__"), List.copyOf(event.details().keySet()));
        assertEquals("", event.details().get("a"));
        assertThrows(UnsupportedOperationException.class, () -> event.details().put("x", "y"));
    }

    @Test
    void aNullDetailsMapIsEmptyAndActionAndOutcomeAreMandatory() { // T47
        assertTrue(event(null).details().isEmpty());
        assertThrows(NullPointerException.class, () -> new AuditEvent(null, AuditOutcome.SUCCESS, Map.of()));
        assertThrows(NullPointerException.class, () -> new AuditEvent(AuditAction.LOGIN_SUCCESS, null, Map.of()));
    }

    @Test
    void theFactoriesBuildPairsAndRejectAnOddNumberOfArguments() { // extra fuera del plan
        AuditEvent ok = AuditEvent.success(AuditAction.IMAGES_CLEANUP, "kept", 3, "dry_run", true);
        assertEquals(AuditOutcome.SUCCESS, ok.outcome());
        assertEquals(Map.of("kept", "3", "dry_run", "true"), ok.details());
        assertEquals(AuditOutcome.FAILURE, AuditEvent.failure(AuditAction.LOGIN_LOCKED).outcome());
        assertNotNull(AuditEvent.failure(AuditAction.LOGIN_LOCKED, "retry_after_s", 5L));
        assertThrows(IllegalArgumentException.class, () -> AuditEvent.success(AuditAction.LOGIN_LOCKED, "solo"));
    }
}
