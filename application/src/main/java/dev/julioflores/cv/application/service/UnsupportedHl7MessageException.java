package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

/** Mensaje bien formado pero de un tipo o versión que la demo no traduce. */
public class UnsupportedHl7MessageException extends ApplicationException {

    /** @param received tipo y versión recibidos, por ejemplo {@code ADT^A08 (v2.3)}; ya acotado y sin datos clínicos */
    public UnsupportedHl7MessageException(String received) {
        super(ErrorCode.HL7_UNSUPPORTED_MESSAGE, "Unsupported HL7 message: " + received, received);
    }
}
