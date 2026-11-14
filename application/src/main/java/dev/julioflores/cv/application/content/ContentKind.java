package dev.julioflores.cv.application.content;

import java.util.Arrays;
import java.util.Optional;

/** Los contenidos editables de la web (tabla {@code content_version}). El CV no es editable desde el panel. */
public enum ContentKind {
    SERVICES("services"),
    PROJECTS("projects"),
    ARCHITECTURE("architecture");

    private final String value;

    ContentKind(String value) {
        this.value = value;
    }

    /** Valor en la URL y en la base de datos. */
    public String value() {
        return value;
    }

    public static Optional<ContentKind> fromValue(String raw) {
        return Arrays.stream(values()).filter(k -> k.value.equals(raw)).findFirst();
    }
}
