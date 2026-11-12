package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.AdminFakes.CLOCK;
import static dev.julioflores.cv.application.AdminFakes.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.AdminFakes.FakeHasher;
import dev.julioflores.cv.application.AdminFakes.FakeIssuer;
import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.AdminInvalidCredentialsException;
import dev.julioflores.cv.application.service.AdminLoginLockedException;
import dev.julioflores.cv.application.service.AdminLoginRateLimitedException;
import dev.julioflores.cv.application.service.LoginAdminService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginAdminServiceTest {

    private static final String PASSWORD = "una frase larga y secreta";

    private final FakeHasher hasher = new FakeHasher();
    private boolean allowed = true;
    private int limiterCalls;

    private LoginAdminService service(FakeAccounts accounts) {
        return new LoginAdminService(accounts, hasher, new FakeIssuer(), key -> {
            limiterCalls++;
            return allowed;
        }, CLOCK, 5, Duration.ofMinutes(15));
    }

    @Test
    void correctPasswordIssuesATokenWithTheSessionVersionAndResetsFailures() { // T9
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(hasher.hash(PASSWORD), 7, 3, null));
        AdminSession session = service(accounts).login(PASSWORD);
        assertEquals("token-sv7", session.accessToken());
        assertEquals(1, accounts.successes);
        assertEquals(0, accounts.failures);
    }

    @Test
    void wrongPasswordThrowsInvalidCredentialsAndCountsAFailure() { // T10
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(hasher.hash(PASSWORD), 1, 0, null));
        var e = assertThrows(AdminInvalidCredentialsException.class, () -> service(accounts).login("incorrecta"));
        assertEquals(ErrorCode.ADMIN_INVALID_CREDENTIALS, e.code());
        assertEquals(1, accounts.failures);
        assertEquals(5, accounts.maxFailuresSeen);
        assertEquals(Duration.ofMinutes(15), accounts.lockSeen);
        assertEquals(0, accounts.successes);
    }

    @Test
    void missingAccountThrowsTheSameErrorAndStillVerifiesAgainstADecoyHash() { // T11
        FakeAccounts accounts = new FakeAccounts(null);
        LoginAdminService service = service(accounts);
        hasher.verified.clear();
        var e = assertThrows(AdminInvalidCredentialsException.class, () -> service.login(PASSWORD));
        assertEquals(ErrorCode.ADMIN_INVALID_CREDENTIALS, e.code());
        assertEquals(1, hasher.verified.size(), "debe verificar contra el hash falso");
        assertEquals(0, accounts.failures);
    }

    @Test
    void lockedAccountRejectsEvenTheCorrectPasswordWithoutVerifyingIt() { // T12
        FakeAccounts accounts = new FakeAccounts(
                FakeAccounts.account(hasher.hash(PASSWORD), 1, 5, NOW.plusSeconds(600)));
        LoginAdminService service = service(accounts);
        hasher.verified.clear();
        var e = assertThrows(AdminLoginLockedException.class, () -> service.login(PASSWORD));
        assertEquals(ErrorCode.ADMIN_LOGIN_LOCKED, e.code());
        assertEquals(600, e.retryAfterSeconds());
        assertEquals(java.util.List.of(600L), e.args());
        assertTrue(hasher.verified.isEmpty(), "no debe verificar el hash");
        assertEquals(0, accounts.failures);
    }

    @Test
    void anExpiredLockLetsTheCorrectPasswordInAndClearsTheLock() { // T13
        FakeAccounts accounts = new FakeAccounts(
                FakeAccounts.account(hasher.hash(PASSWORD), 1, 5, NOW.minusSeconds(1)));
        assertEquals("token-sv1", service(accounts).login(PASSWORD).accessToken());
        assertEquals(1, accounts.successes);
    }

    @Test
    void anExhaustedGlobalLimitFailsWithoutTouchingTheDatabase() { // T14
        allowed = false;
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(hasher.hash(PASSWORD), 1, 0, null)) {
            @Override
            public java.util.Optional<dev.julioflores.cv.domain.AdminAccount> find() {
                throw new AssertionError("no debe consultar la cuenta");
            }
        };
        var e = assertThrows(AdminLoginRateLimitedException.class, () -> service(accounts).login(PASSWORD));
        assertEquals(ErrorCode.ADMIN_LOGIN_RATE_LIMITED, e.code());
        assertEquals(1, limiterCalls);
    }

    @Test
    void anOldCostHashIsReplacedAfterACorrectLogin() { // T15
        hasher.currentCost = 1;
        String oldHash = hasher.hash(PASSWORD);
        hasher.currentCost = 2;
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(oldHash, 1, 0, null));
        service(accounts).login(PASSWORD);
        assertEquals("fake:2:" + PASSWORD, accounts.replacedWith);
    }

    @Test
    void aCurrentCostHashIsNotRewritten() { // extra fuera del plan: el negativo de T15
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(hasher.hash(PASSWORD), 1, 0, null));
        service(accounts).login(PASSWORD);
        assertNull(accounts.replacedWith);
    }
}
