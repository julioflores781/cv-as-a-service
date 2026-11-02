package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.SkillGroup;
import java.util.List;

public interface GetSkillsUseCase {

    List<SkillGroup> all(Language language);

    SkillGroup byGroup(Language language, String group);
}
