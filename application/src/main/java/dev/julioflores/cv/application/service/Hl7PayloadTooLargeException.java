package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class Hl7PayloadTooLargeException extends ApplicationException {

    public Hl7PayloadTooLargeException(int maxBytes) {
        super(ErrorCode.HL7_PAYLOAD_TOO_LARGE, "The message is larger than " + maxBytes + " bytes", maxBytes / 1024);
    }
}
