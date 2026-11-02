package dev.julioflores.cv.domain;

import java.util.Objects;

public record Impact(String value, String text) {

    public Impact {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(text, "text");
    }
}
