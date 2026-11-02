package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Contact;
import dev.julioflores.cv.domain.Language;

public interface GetContactUseCase {

    Contact execute(Language language);
}
