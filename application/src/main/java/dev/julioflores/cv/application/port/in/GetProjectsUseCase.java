package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Project;
import java.util.List;

public interface GetProjectsUseCase {

    /** Lo publicado en el idioma pedido; vacío (nunca error) si todavía no hay nada publicado. */
    List<Project> execute(Language language);
}
