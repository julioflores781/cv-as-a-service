package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;

/** Un puerto del hexágono (base de datos, colas, notificaciones…) con los adaptadores intercambiables que admite. */
public record ArchitecturePort(String id, String name, String description, List<PortAdapter> adapters) {

    public ArchitecturePort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        adapters = Lists.copy(adapters);
    }
}
