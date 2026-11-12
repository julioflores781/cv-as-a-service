package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminUnauthorizedException extends ApplicationException {

    public AdminUnauthorizedException() {
        super(ErrorCode.ADMIN_UNAUTHORIZED, "Missing, invalid or revoked admin session");
    }
}
