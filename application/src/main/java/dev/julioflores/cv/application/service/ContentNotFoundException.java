package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** Un tipo de contenido o un idioma que no existen: el mismo 404 genérico que una ruta inexistente. */
public class ContentNotFoundException extends ApplicationException {

    public ContentNotFoundException(String what) {
        super(ErrorCode.NOT_FOUND, "Unknown editable content: '" + what + "'");
    }
}
