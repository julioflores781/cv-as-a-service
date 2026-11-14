package dev.julioflores.cv.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * La cuenta del único administrador. {@code sessionVersion} sube con cada cambio de contraseña o cierre de
 * sesiones: un token solo vale mientras lleve la versión vigente.
 */
public record AdminAccount(
        String passwordHash,
        int sessionVersion,
        int failedAttempts,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant lastLoginAt) {

    public AdminAccount {
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
