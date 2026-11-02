package dev.julioflores.cv.domain;

import java.util.Objects;

/** Idioma hablado (no confundir con {@link Language}, el idioma del contenido del CV). */
public record SpokenLanguage(String language, String fluency) {

    public SpokenLanguage {
        Objects.requireNonNull(language, "language");
    }
}
