package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.ServiceOffer;
import java.util.List;

public interface GetServicesUseCase {

    /** Lo publicado en el idioma pedido; vacío (nunca error) si todavía no hay nada publicado. */
    List<ServiceOffer> execute(Language language);
}
