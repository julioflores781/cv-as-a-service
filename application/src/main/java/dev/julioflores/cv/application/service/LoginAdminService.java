package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.in.LoginAdminUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import dev.julioflores.cv.domain.AdminAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class LoginAdminService implements LoginAdminUseCase {

    /** Clave única del límite global: con un solo administrador no se limita por IP (ver el plan, §5.3). */
    static final String GLOBAL_KEY = "admin-login";

    private final AdminAccountRepository accounts;
    private final PasswordHasher hasher;
    private final SessionTokenIssuer issuer;
    private final RateLimiter globalLimiter;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lock;
    /** Hash válido de una contraseña que nadie conoce: sin cuenta se verifica contra él para igualar tiempos. */
    private final String decoyHash;

    public LoginAdminService(
            AdminAccountRepository accounts,
            PasswordHasher hasher,
            SessionTokenIssuer issuer,
            RateLimiter globalLimiter,
            Clock clock,
            int maxFailures,
            Duration lock) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.issuer = issuer;
        this.globalLimiter = globalLimiter;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lock = lock;
        this.decoyHash = hasher.hash("decoy-" + System.nanoTime());
    }

    @Override
    public AdminSession login(String password) {
        if (!globalLimiter.tryAcquire(GLOBAL_KEY)) {
            throw new AdminLoginRateLimitedException();
        }
        Optional<AdminAccount> found = accounts.find();
        if (found.isEmpty()) {
            hasher.verify(password, decoyHash);
            throw new AdminInvalidCredentialsException();
        }
        AdminAccount account = found.get();
        Instant now = clock.instant();
        if (account.isLockedAt(now)) {
            // Con la cuenta bloqueada se rechaza incluso la contraseña correcta, y sin gastar un hash.
            throw new AdminLoginLockedException(Math.max(1, Duration.between(now, account.lockedUntil()).toSeconds()));
        }
        if (!hasher.verify(password, account.passwordHash())) {
            accounts.recordFailure(maxFailures, lock);
            throw new AdminInvalidCredentialsException();
        }
        accounts.recordSuccess();
        if (hasher.needsRehash(account.passwordHash())) {
            accounts.replaceHash(account.passwordHash(), hasher.hash(password));
        }
        return issuer.issue(account.sessionVersion());
    }
}
