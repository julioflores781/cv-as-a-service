package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class InvalidContactMessageException extends ApplicationException {

    /** @param field {@code name}, {@code email} o {@code message}: el nombre del campo del formulario, sin traducir */
    public InvalidContactMessageException(String field) {
        super(ErrorCode.CONTACT_INVALID, "Invalid contact field '" + field + "'", field);
    }
}
