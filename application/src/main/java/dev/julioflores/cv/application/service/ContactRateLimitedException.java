package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class ContactRateLimitedException extends ApplicationException {

    public ContactRateLimitedException() {
        super(ErrorCode.CONTACT_RATE_LIMITED, "Too many contact messages from the same sender");
    }
}
