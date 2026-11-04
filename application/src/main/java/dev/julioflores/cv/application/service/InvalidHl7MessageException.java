package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** El mensaje no se pudo interpretar. El motivo nunca incluye contenido del mensaje. */
public class InvalidHl7MessageException extends ApplicationException {

    public InvalidHl7MessageException(String reason) {
        super(ErrorCode.HL7_INVALID_MESSAGE, "Invalid HL7 message: " + reason);
    }

    public InvalidHl7MessageException(String reason, Throwable cause) {
        this(reason);
        initCause(cause);
    }
}
