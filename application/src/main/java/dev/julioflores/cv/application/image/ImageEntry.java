package dev.julioflores.cv.application.image;

import java.time.Instant;

/** Una imagen del listado, con si alguna versión del contenido (activa o histórica) la referencia. */
public record ImageEntry(String key, String url, long bytes, Instant lastModified, boolean referenced) {}
