package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ImageNotFoundException extends ApplicationException {

    public ImageNotFoundException(String id) {
        super(ErrorCode.IMAGE_NOT_FOUND, "Image '" + id + "' does not exist", id);
    }
}
