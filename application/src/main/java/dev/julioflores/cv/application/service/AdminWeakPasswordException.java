package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminWeakPasswordException extends ApplicationException {

    public AdminWeakPasswordException() {
        super(ErrorCode.ADMIN_WEAK_PASSWORD, "The new admin password does not meet the policy");
    }
}
