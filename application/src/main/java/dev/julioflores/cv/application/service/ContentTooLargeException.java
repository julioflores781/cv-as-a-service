package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ContentTooLargeException extends ApplicationException {

    public ContentTooLargeException(int maxBytes) {
        super(ErrorCode.CONTENT_TOO_LARGE, "Content body is larger than " + maxBytes + " bytes", String.valueOf(maxBytes / 1024));
    }
}
