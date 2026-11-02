package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.domain.Impact;
import dev.julioflores.cv.domain.Language;
import java.util.List;

public interface GetImpactUseCase {

    List<Impact> execute(Language language);
}
