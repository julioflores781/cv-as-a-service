package dev.julioflores.cv.application.content;

import java.util.Objects;

/** Un problema concreto de un documento: dónde ({@code es.projects[1].client}) y de qué tipo. */
public record Violation(String path, ViolationCode code) {

    public Violation {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(code, "code");
    }
}
