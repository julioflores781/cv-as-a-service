package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** Una imagen de un tipo que se reconoce pero no se admite (SVG, HEIC…). */
public class ImageUnsupportedTypeException extends ApplicationException {

    public ImageUnsupportedTypeException() {
        super(ErrorCode.IMAGE_UNSUPPORTED_TYPE, "Unsupported image type: only JPEG, PNG, WebP and GIF are accepted");
    }
}
