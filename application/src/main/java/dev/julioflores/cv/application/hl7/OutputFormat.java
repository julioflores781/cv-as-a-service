package dev.julioflores.cv.application.hl7;

import dev.julioflores.cv.application.service.UnsupportedHl7FormatException;
import java.util.Locale;

public enum OutputFormat {
    /** Resumen sencillo del mensaje (paciente, visita, observaciones). */
    JSON,
    /** Bundle FHIR R4 de tipo {@code collection}. */
    FHIR;

    public static OutputFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "json" -> JSON;
            case "fhir" -> FHIR;
            default -> throw new UnsupportedHl7FormatException(value);
        };
    }
}
