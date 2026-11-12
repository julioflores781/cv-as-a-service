package dev.julioflores.cv.application.content;

import java.time.Instant;
import java.util.Map;

/**
 * Una instantánea de un contenido editable, en su forma almacenada (la misma que {@code db/sample/*.json}).
 * La versión 0 con contenido nulo significa «nada publicado todavía»: no es un error.
 */
public record ContentVersion(
        int version, boolean active, Instant publishedAt, String source, String comment, Map<String, Object> content) {

    public static ContentVersion none() {
        return new ContentVersion(0, false, null, null, null, null);
    }
}
