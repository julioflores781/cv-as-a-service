package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Profile;

public interface GetProfileUseCase {

    Profile execute(Language language);
}
