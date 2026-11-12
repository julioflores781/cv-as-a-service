package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** Falta el archivo, está vacío o sus bytes no son una imagen soportada. */
public class ImageInvalidException extends ApplicationException {

    public ImageInvalidException(String reason) {
        super(ErrorCode.IMAGE_INVALID, "Invalid image: " + reason);
    }
}
