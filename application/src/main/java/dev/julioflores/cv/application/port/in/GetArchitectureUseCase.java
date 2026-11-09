package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.Language;

public interface GetArchitectureUseCase {

    /** Lo publicado en el idioma pedido; vacío (nunca error) si todavía no hay nada publicado. */
    Architecture execute(Language language);
}
