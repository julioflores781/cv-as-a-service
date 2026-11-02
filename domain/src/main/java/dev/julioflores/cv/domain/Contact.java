package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

public record Contact(String email, List<ContactLink> links) {

    public Contact {
        Objects.requireNonNull(email, "email");
        links = Lists.copy(links);
    }
}
