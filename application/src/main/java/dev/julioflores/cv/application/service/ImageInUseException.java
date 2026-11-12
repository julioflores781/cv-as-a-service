package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** La imagen la referencia alguna versión del contenido (activa o histórica). */
public class ImageInUseException extends ApplicationException {

    public ImageInUseException(String key) {
        super(ErrorCode.IMAGE_IN_USE, "Image '" + key + "' is referenced by saved content", key);
    }
}
