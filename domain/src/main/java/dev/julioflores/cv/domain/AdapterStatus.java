package dev.julioflores.cv.domain;

import java.util.Arrays;

/** Qué tan real es un adaptador en el diagrama de arquitectura: lo que se afirma debe ser cierto. */
public enum AdapterStatus {
    /** Está implementado en esta misma API. */
    IN_THIS_API("in-this-api"),
    /** Lo he usado en sistemas en producción. */
    PRODUCTION("production"),
    /** Solo en proyectos de prueba. */
    EXPERIMENT("experiment");

    private final String value;

    AdapterStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AdapterStatus fromValue(String raw) {
        return Arrays.stream(values()).filter(s -> s.value.equals(raw)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown adapter status '" + raw + "'"));
    }
}
