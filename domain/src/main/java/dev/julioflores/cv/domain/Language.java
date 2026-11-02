package dev.julioflores.cv.domain;

import java.util.Locale;

public enum Language {
    ES, EN;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Acepta "es", "en-US", "EN"… Cualquier valor desconocido o vacío cae en español. */
    public static Language fromTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return ES;
        }
        String tag = raw.trim().toLowerCase(Locale.ROOT);
        return tag.startsWith("en") ? EN : ES;
    }
}
