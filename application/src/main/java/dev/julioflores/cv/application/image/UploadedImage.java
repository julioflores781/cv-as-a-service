package dev.julioflores.cv.application.image;

/** Lo que devuelve una subida correcta: dónde quedó y cómo es la imagen (detectado por sus bytes). */
public record UploadedImage(String key, String url, String contentType, long bytes, int width, int height) {}
