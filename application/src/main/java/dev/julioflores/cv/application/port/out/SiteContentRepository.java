package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ServiceOffer;
import java.util.List;

/** Contenido editable de la web que no es el CV. Si no hay versión publicada devuelve vacío, nunca lanza por eso. */
public interface SiteContentRepository {

    List<ServiceOffer> services(Language language);

    List<Project> projects(Language language);

    Architecture architecture(Language language);
}
