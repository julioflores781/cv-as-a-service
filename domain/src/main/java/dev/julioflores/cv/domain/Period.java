package dev.julioflores.cv.domain;

import java.time.YearMonth;
import java.util.Objects;

/** Periodo de tiempo; `end` nulo significa que sigue en curso. */
public record Period(YearMonth start, YearMonth end) {

    public Period {
        Objects.requireNonNull(start, "start");
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    public boolean current() {
        return end == null;
    }
}
