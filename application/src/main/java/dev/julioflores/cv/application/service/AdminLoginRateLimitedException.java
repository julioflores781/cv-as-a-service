package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.error.ErrorCode;

public class AdminLoginRateLimitedException extends ApplicationException {

    public AdminLoginRateLimitedException() {
        super(ErrorCode.ADMIN_LOGIN_RATE_LIMITED, "Global admin login rate limit reached");
    }
}
