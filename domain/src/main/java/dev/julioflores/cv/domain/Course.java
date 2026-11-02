package dev.julioflores.cv.domain;

import java.util.Objects;

public record Course(String name, String issuer) {

    public Course {
        Objects.requireNonNull(name, "name");
    }
}
