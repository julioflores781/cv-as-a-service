package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.error.ErrorCode;
import java.util.List;

/** ES y EN no tienen la misma estructura; lleva las diferencias. */
public class ContentParityBrokenException extends ContentViolationsException {

    public ContentParityBrokenException(List<Violation> differences) {
        super(ErrorCode.CONTENT_PARITY_BROKEN, "Spanish and English content differ", differences);
    }
}
