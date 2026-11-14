package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;
import java.util.List;

/** Base de los errores de contenido que llevan la lista de violaciones (se devuelve como {@code errors}). */
public abstract class ContentViolationsException extends ApplicationException {

    private final List<Violation> violations;

    protected ContentViolationsException(ErrorCode code, String developerMessage, List<Violation> violations) {
        super(code, developerMessage + " (" + violations.size() + ")", String.valueOf(violations.size()));
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
