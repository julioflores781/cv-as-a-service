package dev.julioflores.cv.domain;

import java.util.Objects;

public record Profile(
        String name,
        String label,
        String tagline,
        String city,
        String countryCode,
        String summary,
        String availability) {

    public Profile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
    }
}
