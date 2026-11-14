package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminInvalidCredentialsException extends ApplicationException {

    public AdminInvalidCredentialsException() {
        super(ErrorCode.ADMIN_INVALID_CREDENTIALS, "Invalid admin credentials");
    }
}
