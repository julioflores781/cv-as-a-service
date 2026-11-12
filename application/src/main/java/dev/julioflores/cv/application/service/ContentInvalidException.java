package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.error.ErrorCode;
import java.util.List;

/** El contenido incumple las reglas de validación; lleva todas las violaciones. */
public class ContentInvalidException extends ContentViolationsException {

    public ContentInvalidException(List<Violation> violations) {
        super(ErrorCode.CONTENT_INVALID, "Content is not valid", violations);
    }
}
