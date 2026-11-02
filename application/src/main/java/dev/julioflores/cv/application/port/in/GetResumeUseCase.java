package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;

public interface GetResumeUseCase {

    Resume execute(Language language);
}
