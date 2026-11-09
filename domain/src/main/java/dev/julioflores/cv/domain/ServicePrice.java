package dev.julioflores.cv.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Precio «desde» de un servicio. Un servicio sin precio se muestra como «a consultar». */
public record ServicePrice(BigDecimal from, String currency) {

    public ServicePrice {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(currency, "currency");
        if (from.signum() < 0) {
            throw new IllegalArgumentException("price " + from + " is negative");
        }
    }
}
