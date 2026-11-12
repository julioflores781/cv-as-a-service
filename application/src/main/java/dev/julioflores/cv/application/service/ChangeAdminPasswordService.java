package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.in.ChangeAdminPasswordUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import dev.julioflores.cv.domain.AdminAccount;
import dev.julioflores.cv.domain.AdminPasswordPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class ChangeAdminPasswordService implements ChangeAdminPasswordUseCase {

    private final AdminAccountRepository accounts;
    private final PasswordHasher hasher;
    private final SessionTokenIssuer issuer;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lock;

    public ChangeAdminPasswordService(
            AdminAccountRepository accounts,
            PasswordHasher hasher,
            SessionTokenIssuer issuer,
            Clock clock,
            int maxFailures,
            Duration lock) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.issuer = issuer;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lock = lock;
    }

    @Override
    public AdminSession change(String currentPassword, String newPassword) {
        AdminAccount account = accounts.find().orElseThrow(AdminUnauthorizedException::new);
        Instant now = clock.instant();
        if (account.isLockedAt(now)) {
            // Un token robado no debe servir para adivinar la contraseña actual sin freno.
            throw new AdminLoginLockedException(Math.max(1, Duration.between(now, account.lockedUntil()).toSeconds()));
        }
        if (!hasher.verify(currentPassword, account.passwordHash())) {
            accounts.recordFailure(maxFailures, lock);
            throw new AdminCurrentPasswordWrongException();
        }
        if (!AdminPasswordPolicy.isAcceptable(newPassword, currentPassword)) {
            throw new AdminWeakPasswordException();
        }
        int sessionVersion = accounts.changePassword(hasher.hash(newPassword));
        return issuer.issue(sessionVersion);
    }
}
