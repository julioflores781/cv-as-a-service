package dev.julioflores.cv.domain;

import java.util.List;

final class Lists {

    private Lists() {}

    /** Copia defensiva e inmutable; null se trata como lista vacía. */
    static <T> List<T> copy(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }
}
