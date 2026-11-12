package dev.julioflores.cv.application.admin;

import java.time.Instant;

/** Un token de sesión recién emitido y cuándo caduca. */
public record AdminSession(String accessToken, Instant expiresAt) {}
