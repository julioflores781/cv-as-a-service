package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminLoginLockedException extends ApplicationException {

    private final long retryAfterSeconds;

    public AdminLoginLockedException(long retryAfterSeconds) {
        super(ErrorCode.ADMIN_LOGIN_LOCKED, "Admin account locked after too many failed attempts", retryAfterSeconds);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Segundos que faltan para que acabe el bloqueo (para la cabecera {@code Retry-After}). */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
