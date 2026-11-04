package dev.julioflores.cv.application.hl7;

import java.util.Objects;

/** Resultado de traducir un mensaje: el formato producido y el documento ya serializado como JSON. */
public record Hl7Translation(OutputFormat format, String body) {

    public Hl7Translation {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(body, "body");
    }
}
