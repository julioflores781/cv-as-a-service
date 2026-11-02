package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.domain.Language;

public class ResumeNotFoundException extends ApplicationException {

    public ResumeNotFoundException(Language language) {
        super(ErrorCode.RESUME_NOT_FOUND, "No active resume for language " + language.tag(), language.tag());
    }
}
