package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class UnsupportedHl7FormatException extends ApplicationException {

    public UnsupportedHl7FormatException(String format) {
        super(ErrorCode.HL7_UNSUPPORTED_FORMAT, "Unsupported output format '" + format + "'", format);
    }
}
