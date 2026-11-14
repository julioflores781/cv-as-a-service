package dev.julioflores.cv.infrastructure;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Un reloj que adelanta la hora real lo que se le pida (solo pruebas): permite probar «más viejo que la gracia» sin esperar horas.
 * Con el desfase en cero es el reloj de siempre, así que no afecta al resto de pruebas.
 */
public class TestClockConfig {

    private static volatile Duration offset = Duration.ZERO;

    public static void advance(Duration by) {
        offset = offset.plus(by);
    }

    public static void reset() {
        offset = Duration.ZERO;
    }

    @Produces
    @Alternative
    @Priority(1)
    @ApplicationScoped
    Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.now().plus(offset);
            }
        };
    }
}
