package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ExperienceNotFoundException extends ApplicationException {

    public ExperienceNotFoundException(String id) {
        super(ErrorCode.EXPERIENCE_NOT_FOUND, "No experience with id '" + id + "'", id);
    }
}
