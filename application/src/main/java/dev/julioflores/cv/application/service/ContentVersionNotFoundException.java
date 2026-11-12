package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ContentVersionNotFoundException extends ApplicationException {

    public ContentVersionNotFoundException(String languageTag, int version) {
        super(ErrorCode.CONTENT_VERSION_NOT_FOUND, "No '" + languageTag + "' version " + version, languageTag,
                String.valueOf(version));
    }
}
