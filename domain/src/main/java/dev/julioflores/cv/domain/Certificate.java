package dev.julioflores.cv.domain;

import java.util.Objects;

public record Certificate(String name, String issuer) {

    public Certificate {
        Objects.requireNonNull(name, "name");
    }
}
