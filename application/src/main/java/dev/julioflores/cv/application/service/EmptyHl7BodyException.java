package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class EmptyHl7BodyException extends ApplicationException {

    public EmptyHl7BodyException() {
        super(ErrorCode.HL7_EMPTY_BODY, "The request body is empty");
    }
}
