package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.AdminFakes.CLOCK;
import static dev.julioflores.cv.application.AdminFakes.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.AdminFakes.FakeHasher;
import dev.julioflores.cv.application.AdminFakes.FakeIssuer;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.AdminCurrentPasswordWrongException;
import dev.julioflores.cv.application.service.AdminLoginLockedException;
import dev.julioflores.cv.application.service.AdminUnauthorizedException;
import dev.julioflores.cv.application.service.AdminWeakPasswordException;
import dev.julioflores.cv.application.service.ChangeAdminPasswordService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChangeAdminPasswordServiceTest {

    private static final String CURRENT = "la contraseña actual larga";
    private static final String NEXT = "otra frase larga y secreta";

    private final FakeHasher hasher = new FakeHasher();

    private ChangeAdminPasswordService service(FakeAccounts accounts) {
        return new ChangeAdminPasswordService(accounts, hasher, new FakeIssuer(), CLOCK, 5, Duration.ofMinutes(15));
    }

    private FakeAccounts accounts() {
        return new FakeAccounts(FakeAccounts.account(hasher.hash(CURRENT), 3, 0, null));
    }

    @Test
    void correctCurrentPasswordStoresTheNewHashRaisesTheVersionAndReturnsAToken() { // T18
        FakeAccounts accounts = accounts();
        var session = service(accounts).change(CURRENT, NEXT);
        assertEquals("token-sv4", session.accessToken());
        assertEquals(hasher.hash(NEXT), accounts.account.passwordHash());
        assertEquals(4, accounts.account.sessionVersion());
    }

    @Test
    void wrongCurrentPasswordThrowsCountsAFailureAndChangesNothing() { // T19
        FakeAccounts accounts = accounts();
        var e = assertThrows(AdminCurrentPasswordWrongException.class, () -> service(accounts).change("mala", NEXT));
        assertEquals(ErrorCode.ADMIN_CURRENT_PASSWORD_WRONG, e.code());
        assertEquals(1, accounts.failures);
        assertEquals(hasher.hash(CURRENT), accounts.account.passwordHash());
        assertEquals(3, accounts.account.sessionVersion());
    }

    @Test
    void aWeakOrRepeatedNewPasswordThrowsWeakPassword() { // T20
        FakeAccounts accounts = accounts();
        ChangeAdminPasswordService service = service(accounts);
        for (String weak : new String[] {"corta", CURRENT, "            "}) {
            var e = assertThrows(AdminWeakPasswordException.class, () -> service.change(CURRENT, weak));
            assertEquals(ErrorCode.ADMIN_WEAK_PASSWORD, e.code());
        }
        assertEquals(3, accounts.account.sessionVersion());
        assertEquals(0, accounts.failures);
    }

    @Test
    void aLockedAccountCannotChangeThePassword() { // extra fuera del plan (decisión: un token robado no adivina la actual)
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account(hasher.hash(CURRENT), 3, 5, NOW.plusSeconds(60)));
        assertThrows(AdminLoginLockedException.class, () -> service(accounts).change(CURRENT, NEXT));
        assertEquals(3, accounts.account.sessionVersion());
    }

    @Test
    void withoutAnAccountTheSessionIsUnauthorized() { // extra fuera del plan
        assertThrows(AdminUnauthorizedException.class, () -> service(new FakeAccounts(null)).change(CURRENT, NEXT));
    }
}
