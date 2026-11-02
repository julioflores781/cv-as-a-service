package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.WorkExperience;
import java.util.List;

public interface GetExperienceUseCase {

    /** Experiencia de más reciente a más antigua. */
    List<WorkExperience> list(Language language);

    WorkExperience byId(Language language, String id);
}
