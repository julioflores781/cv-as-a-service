package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.content.ContentVersionSummary;
import java.util.List;

public interface ListContentVersionsUseCase {

    /** Historial de un idioma, de la más nueva a la más antigua, con el límite acotado a 1..200. */
    List<ContentVersionSummary> list(String kind, String language, int limit);
}
