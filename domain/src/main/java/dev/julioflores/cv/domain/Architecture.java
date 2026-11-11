package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

/** El diagrama hexagonal de la web: un núcleo que no cambia y los puertos que lo rodean. */
public record Architecture(String coreName, String coreDescription, List<ArchitecturePort> ports) {

    public Architecture {
        Objects.requireNonNull(coreName, "coreName");
        ports = Lists.copy(ports);
    }

    /** Lo que se sirve mientras no haya nada publicado: sin puertos, para que la web omita el diagrama. */
    public static Architecture empty() {
        return new Architecture("", null, List.of());
    }
}
