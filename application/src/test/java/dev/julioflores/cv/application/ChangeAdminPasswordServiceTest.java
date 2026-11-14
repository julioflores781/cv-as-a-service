package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.AdminFakes.CLOCK;
import static dev.julioflores.cv.application.AdminFakes.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.AdminFakes.FakeHasher;
import dev.julioflores.cv.application.AdminFakes.FakeIssuer;
import dev.julioflores.cv.application.AdminFakes.RecordingAuditLog;
import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.AdminCurrentPasswordWrongException;
import dev.julioflores.cv.application.service.AdminLoginLockedException;
import dev.julioflores.cv.application.service.AdminUnauthorizedException;
import dev.julioflores.cv.application.service.AdminWeakPasswordException;
import dev.julioflores.cv.application.service.ChangeAdminPasswordService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChangeAdminPasswordServiceTest {

    private static final String CURRENT = "la contraseña actual larga";
    private static final String NEXT = "otra frase larga y secreta";

    private final FakeHasher hasher = new FakeHasher();
    private final RecordingAuditLog audit = new RecordingAuditLog();

    private ChangeAdminPasswordService service(FakeAccounts accounts) {
        return new ChangeAdminPasswordService(accounts, hasher, new FakeIssuer(), audit, CLOCK, 5, Duration.ofMinutes(15));
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

    private void assertNoSecrets() {
        String logged = audit.everything();
        for (String secret : new String[] {CURRENT, NEXT, "mala", "fake:", "token-sv"}) {
            assertFalse(logged.contains(secret), "el evento no debe llevar " + secret);
        }
    }

    @Test
    void aSuccessfulChangeRecordsPasswordChanged() { // T36 (éxito)
        service(accounts()).change(CURRENT, NEXT);
        assertEquals(RecordingAuditLog.success(AuditAction.PASSWORD_CHANGED, Map.of()), audit.only());
        assertNoSecrets();
    }

    @Test
    void aWrongCurrentPasswordRecordsWrongCurrent() { // T36
        assertThrows(AdminCurrentPasswordWrongException.class, () -> service(accounts()).change("mala", NEXT));
        assertEquals(RecordingAuditLog.failure(AuditAction.PASSWORD_CHANGE_FAILED, Map.of("reason", "wrong_current")), audit.only());
        assertNoSecrets();
    }

    @Test
    void aLockedAccountRecordsLockedAndAWeakPasswordRecordsWeak() { // T36
        FakeAccounts locked = new FakeAccounts(FakeAccounts.account(hasher.hash(CURRENT), 3, 5, NOW.plusSeconds(60)));
        assertThrows(AdminLoginLockedException.class, () -> service(locked).change(CURRENT, NEXT));
        assertEquals(RecordingAuditLog.failure(AuditAction.PASSWORD_CHANGE_FAILED, Map.of("reason", "locked")), audit.only());
        audit.events.clear();

        assertThrows(AdminWeakPasswordException.class, () -> service(accounts()).change(CURRENT, "corta"));
        assertEquals(RecordingAuditLog.failure(AuditAction.PASSWORD_CHANGE_FAILED, Map.of("reason", "weak")), audit.only());
        assertNoSecrets();
    }

    @Test
    void withoutAnAccountNothingIsRecordedBecauseItIsNotInTheEventTable() { // extra fuera del plan
        assertThrows(AdminUnauthorizedException.class, () -> service(new FakeAccounts(null)).change(CURRENT, NEXT));
        assertEquals(0, audit.events.size());
    }

    @Test
    void ifStoringTheNewPasswordOrIssuingTheTokenFailsNoPasswordChangedIsRecorded() { // R2: el éxito se emite DESPUÉS
        FakeAccounts failing = new FakeAccounts(FakeAccounts.account(hasher.hash(CURRENT), 3, 0, null)) {
            @Override
            public int changePassword(String newHash) {
                throw new IllegalStateException("db down");
            }
        };
        assertThrows(IllegalStateException.class, () -> service(failing).change(CURRENT, NEXT));

        var noToken = new ChangeAdminPasswordService(accounts(), hasher, sv -> {
            throw new IllegalStateException("signing failed");
        }, audit, CLOCK, 5, Duration.ofMinutes(15));
        assertThrows(IllegalStateException.class, () -> noToken.change(CURRENT, NEXT));

        assertEquals(0, audit.events.size());
    }
}
