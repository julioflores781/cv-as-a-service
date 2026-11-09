package dev.julioflores.cv.domain;

import java.util.Objects;

/** Una tecnología concreta que se enchufa a un puerto. */
public record PortAdapter(String id, String name, AdapterStatus status, String note) {

    public PortAdapter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }
}
