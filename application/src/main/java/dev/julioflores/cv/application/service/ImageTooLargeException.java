package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ImageTooLargeException extends ApplicationException {

    public ImageTooLargeException(long maxBytes) {
        super(ErrorCode.IMAGE_TOO_LARGE, "Image is larger than " + maxBytes + " bytes", String.valueOf(maxBytes / (1024 * 1024)));
    }
}
