package dev.julioflores.cv.application.content;

import java.util.List;
import java.util.Objects;

/** Lo activo de un tipo en español e inglés, con las diferencias de estructura entre ambos (vacías si hay paridad). */
public record EditableContent(ContentKind kind, ContentVersion es, ContentVersion en, List<Violation> parity) {

    public EditableContent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(es, "es");
        Objects.requireNonNull(en, "en");
        parity = List.copyOf(parity);
    }

    public boolean parityOk() {
        return parity.isEmpty();
    }
}
