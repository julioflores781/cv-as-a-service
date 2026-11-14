package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versiones inmutables del contenido editable. Las escrituras son atómicas y serializadas por tipo e idioma; la
 * comparación con la versión base ocurre dentro de la misma operación, así que dos guardados concurrentes no se pisan.
 */
public interface ContentVersionRepository {

    /** La versión activa del tipo y el idioma, o vacío si no hay nada publicado. */
    Optional<ContentVersion> active(ContentKind kind, Language language);

    Optional<ContentVersion> find(ContentKind kind, Language language, int version);

    /** Historial ya ordenado de la más nueva a la más antigua y recortado a {@code limit} filas (el llamante no lo reordena). */
    List<ContentVersionSummary> list(ContentKind kind, Language language, int limit);

    /**
     * Publica una versión nueva (la mayor existente + 1) en cada idioma de {@code documents}, todo o nada. Los documentos ya
     * están validados.
     *
     * @param baseVersions versión activa que el editor tenía delante (0 si no había nada) por idioma
     * @throws dev.julioflores.cv.application.service.ContentVersionConflictException si alguna activa no coincide con su base
     */
    void publish(
            ContentKind kind,
            Map<Language, Integer> baseVersions,
            Map<Language, Map<String, Object>> documents,
            String comment);

    /**
     * Mueve el puntero de la activa a una versión existente, sin crear filas.
     *
     * @throws dev.julioflores.cv.application.service.ContentVersionConflictException si la activa no es {@code baseVersion}
     * @throws dev.julioflores.cv.application.service.ContentVersionNotFoundException si {@code targetVersion} no existe
     */
    void activate(ContentKind kind, Language language, int baseVersion, int targetVersion);
}
