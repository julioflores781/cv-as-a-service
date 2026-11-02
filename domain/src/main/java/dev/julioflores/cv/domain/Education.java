package dev.julioflores.cv.domain;

import java.util.Objects;

public record Education(String institution, String studyType, String area) {

    public Education {
        Objects.requireNonNull(institution, "institution");
    }
}
