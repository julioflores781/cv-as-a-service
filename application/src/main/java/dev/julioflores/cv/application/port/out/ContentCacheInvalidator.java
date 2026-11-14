package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.Language;

/**
 * Descarta lo recordado de un contenido editable para que la siguiente lectura consulte la fuente. Se invoca después de
 * confirmar un cambio. Es genérico (tipo + idioma) para poder cubrir otros contenidos, como el CV, si algún día hace falta.
 */
public interface ContentCacheInvalidator {

    /** @param kind tipo de contenido: {@code services}, {@code projects} o {@code architecture} */
    void invalidate(String kind, Language language);
}
