package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentParity;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.domain.Language;

/** Lo que comparten los casos de uso de contenido editable: leer el tipo y el idioma de la petición y componer lo activo. */
final class ContentRequests {

    private ContentRequests() {}

    static ContentKind kind(String raw) {
        return ContentKind.fromValue(raw).orElseThrow(() -> new ContentNotFoundException(raw));
    }

    /** Estricto: solo {@code es} y {@code en} (el resto de la API es tolerante, pero aquí el idioma identifica un documento). */
    static Language language(String raw) {
        for (Language language : Language.values()) {
            if (language.tag().equals(raw)) {
                return language;
            }
        }
        throw new ContentNotFoundException(raw);
    }

    static EditableContent load(ContentVersionRepository versions, ContentKind kind) {
        ContentVersion es = versions.active(kind, Language.ES).orElseGet(ContentVersion::none);
        ContentVersion en = versions.active(kind, Language.EN).orElseGet(ContentVersion::none);
        return new EditableContent(kind, es, en, ContentParity.compare(kind, es.content(), en.content()));
    }
}
