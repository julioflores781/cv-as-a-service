package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** El almacenamiento de imágenes no está configurado o no responde. El contenido de la web no depende de él. */
public class ImageStorageUnavailableException extends ApplicationException {

    public ImageStorageUnavailableException(String reason, Throwable cause) {
        super(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, "Image storage is unavailable: " + reason);
        if (cause != null) {
            initCause(cause);
        }
    }

    public ImageStorageUnavailableException(String reason) {
        this(reason, null);
    }
}
