package dev.julioflores.cv.application.image;

import java.time.Instant;

/** Un objeto del almacenamiento de imágenes: su clave, su tamaño y cuándo se escribió por última vez. */
public record StoredObject(String key, long bytes, Instant lastModified) {}
