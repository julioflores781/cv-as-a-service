package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.VerifyAdminSessionUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.domain.AdminAccount;
import java.time.Instant;
import java.util.Optional;

public class VerifyAdminSessionService implements VerifyAdminSessionUseCase {

    private final AdminAccountRepository accounts;

    public VerifyAdminSessionService(AdminAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<Instant> verify(int sessionVersion) {
        return accounts.find()
                .filter(account -> account.sessionVersion() == sessionVersion)
                .map(AdminAccount::passwordChangedAt);
    }
}
