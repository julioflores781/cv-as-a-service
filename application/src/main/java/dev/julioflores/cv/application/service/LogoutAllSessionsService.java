package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.port.in.LogoutAllSessionsUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.AuditLog;

public class LogoutAllSessionsService implements LogoutAllSessionsUseCase {

    private final AdminAccountRepository accounts;
    private final AuditLog audit;

    public LogoutAllSessionsService(AdminAccountRepository accounts, AuditLog audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Override
    public void logoutAll() {
        accounts.invalidateSessions();
        audit.record(AuditEvent.success(AuditAction.SESSIONS_REVOKED));
    }
}
