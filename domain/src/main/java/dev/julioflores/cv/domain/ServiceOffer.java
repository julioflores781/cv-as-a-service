package dev.julioflores.cv.domain;

import java.util.Objects;

/** Un servicio que Julio ofrece. {@code price} es nulo cuando se cotiza caso a caso. */
public record ServiceOffer(String id, String title, String summary, ServicePrice price) {

    public ServiceOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
    }
}
