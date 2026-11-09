package dev.julioflores.cv.domain;

import java.util.Arrays;

/** Si el nombre del cliente de un proyecto se puede publicar. */
public enum Disclosure {
    PUBLIC("public"),
    ANONYMOUS("anonymous");

    private final String value;

    Disclosure(String value) {
        this.value = value;
    }

    /** Valor en el contrato JSON (y en la base de datos). */
    public String value() {
        return value;
    }

    public static Disclosure fromValue(String raw) {
        return Arrays.stream(values()).filter(d -> d.value.equals(raw)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown disclosure '" + raw + "'"));
    }
}
