package dev.julioflores.cv.domain;

import java.util.Objects;

/** Etapa dentro de un mismo puesto. `client` es opcional. */
public record Phase(String title, String client) {

    public Phase {
        Objects.requireNonNull(title, "title");
    }
}
