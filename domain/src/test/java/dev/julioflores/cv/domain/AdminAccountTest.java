package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Extra fuera del plan: invariantes y bloqueo de AdminAccount. */
class AdminAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private static AdminAccount account(Instant lockedUntil) {
        return new AdminAccount("$argon2id$x", 1, 0, lockedUntil, NOW, null);
    }

    @Test
    void isLockedOnlyWhileTheLockIsInTheFuture() {
        assertFalse(account(null).isLockedAt(NOW));
        assertTrue(account(NOW.plusSeconds(1)).isLockedAt(NOW));
        assertFalse(account(NOW).isLockedAt(NOW));
        assertFalse(account(NOW.minusSeconds(5)).isLockedAt(NOW));
    }

    @Test
    void requiresHashAndChangeDate() {
        assertThrows(NullPointerException.class, () -> new AdminAccount(null, 1, 0, null, NOW, null));
        assertThrows(NullPointerException.class, () -> new AdminAccount("h", 1, 0, null, null, null));
    }
}
