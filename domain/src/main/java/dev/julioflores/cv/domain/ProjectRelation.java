package dev.julioflores.cv.domain;

import java.util.Arrays;

/** Relación de Julio con el proyecto: producto propio o trabajo para una empresa. */
public enum ProjectRelation {
    OWN("own"),
    EMPLOYER("employer");

    private final String value;

    ProjectRelation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProjectRelation fromValue(String raw) {
        return Arrays.stream(values()).filter(r -> r.value.equals(raw)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown project relation '" + raw + "'"));
    }
}
