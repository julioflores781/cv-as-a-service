package dev.julioflores.cv.domain;

import java.util.Objects;

public record ContactLink(String network, String url) {

    public ContactLink {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(url, "url");
    }
}
