package dev.julioflores.cv.domain;

import java.util.Objects;

/**
 * Una imagen de un proyecto. Guarda la clave del objeto en el almacenamiento, no la URL (la URL pública se compone al
 * servir, así cambiar de dominio de medios no obliga a editar contenido). La primera de la lista es la portada.
 */
public record ProjectImage(String key, String alt, int width, int height) {

    public static final int MAX_ALT = 200;

    public ProjectImage {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(alt, "alt");
        if (alt.codePointCount(0, alt.length()) > MAX_ALT) {
            throw new IllegalArgumentException("Image alt text is longer than " + MAX_ALT + " characters");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image width and height must be positive");
        }
    }
}
