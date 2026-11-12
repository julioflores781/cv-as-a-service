package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminCurrentPasswordWrongException extends ApplicationException {

    public AdminCurrentPasswordWrongException() {
        super(ErrorCode.ADMIN_CURRENT_PASSWORD_WRONG, "The current admin password is wrong");
    }
}
