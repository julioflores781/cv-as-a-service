package dev.julioflores.cv.application.content;

import java.time.Instant;

/** Una fila del historial: todo salvo el contenido. */
public record ContentVersionSummary(int version, boolean active, Instant publishedAt, String source, String comment) {}
