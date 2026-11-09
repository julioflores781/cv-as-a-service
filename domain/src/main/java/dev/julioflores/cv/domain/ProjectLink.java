package dev.julioflores.cv.domain;

import java.util.Objects;

public record ProjectLink(String label, String url) {

    public ProjectLink {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(url, "url");
    }
}
