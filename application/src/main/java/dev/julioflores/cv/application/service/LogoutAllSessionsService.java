package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.LogoutAllSessionsUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;

public class LogoutAllSessionsService implements LogoutAllSessionsUseCase {

    private final AdminAccountRepository accounts;

    public LogoutAllSessionsService(AdminAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public void logoutAll() {
        accounts.invalidateSessions();
    }
}
