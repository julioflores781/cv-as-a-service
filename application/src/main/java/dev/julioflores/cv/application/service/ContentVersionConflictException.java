package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** La versión activa ya no es la que el panel tenía delante (alguien publicó antes). */
public class ContentVersionConflictException extends ApplicationException {

    public ContentVersionConflictException(String languageTag, int currentVersion) {
        super(ErrorCode.CONTENT_VERSION_CONFLICT,
                "The active '" + languageTag + "' version is " + currentVersion + ", not the one the edit was based on",
                languageTag, String.valueOf(currentVersion));
    }
}
