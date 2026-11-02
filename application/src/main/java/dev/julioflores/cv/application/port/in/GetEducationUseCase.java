package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Qualifications;

public interface GetEducationUseCase {

    Qualifications execute(Language language);
}
