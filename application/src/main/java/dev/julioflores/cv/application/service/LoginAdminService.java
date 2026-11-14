package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.port.in.LoginAdminUseCase;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.AuditLog;
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
    private final AuditLog audit;
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
            AuditLog audit,
            Clock clock,
            int maxFailures,
            Duration lock) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.issuer = issuer;
        this.globalLimiter = globalLimiter;
        this.audit = audit;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lock = lock;
        this.decoyHash = hasher.hash("decoy-" + System.nanoTime());
    }

    @Override
    public AdminSession login(String password) {
        if (!globalLimiter.tryAcquire(GLOBAL_KEY)) {
            audit.record(AuditEvent.failure(AuditAction.LOGIN_RATE_LIMITED));
            throw new AdminLoginRateLimitedException();
        }
        Optional<AdminAccount> found = accounts.find();
        if (found.isEmpty()) {
            hasher.verify(password, decoyHash);
            audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILED, "reason", "no_account"));
            throw new AdminInvalidCredentialsException();
        }
        AdminAccount account = found.get();
        Instant now = clock.instant();
        if (account.isLockedAt(now)) {
            // Con la cuenta bloqueada se rechaza incluso la contraseña correcta, y sin gastar un hash.
            long retryAfter = Math.max(1, Duration.between(now, account.lockedUntil()).toSeconds());
            audit.record(AuditEvent.failure(AuditAction.LOGIN_LOCKED, "retry_after_s", retryAfter));
            throw new AdminLoginLockedException(retryAfter);
        }
        if (!hasher.verify(password, account.passwordHash())) {
            accounts.recordFailure(maxFailures, lock);
            audit.record(AuditEvent.failure(AuditAction.LOGIN_FAILED, "reason", "bad_credentials"));
            throw new AdminInvalidCredentialsException();
        }
        accounts.recordSuccess();
        if (hasher.needsRehash(account.passwordHash())) {
            accounts.replaceHash(account.passwordHash(), hasher.hash(password));
        }
        AdminSession session = issuer.issue(account.sessionVersion());
        audit.record(AuditEvent.success(AuditAction.LOGIN_SUCCESS));
        return session;
    }
}
