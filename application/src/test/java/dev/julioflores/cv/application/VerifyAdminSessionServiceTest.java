package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.service.VerifyAdminSessionService;
import org.junit.jupiter.api.Test;

class VerifyAdminSessionServiceTest {

    @Test
    void anEqualSessionVersionIsValid() { // T16
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account("h", 4, 0, null));
        var verified = new VerifyAdminSessionService(accounts).verify(4);
        assertEquals(accounts.account.passwordChangedAt(), verified.orElseThrow());
    }

    @Test
    void aDifferentVersionOrNoAccountIsInvalid() { // T17
        assertTrue(new VerifyAdminSessionService(new FakeAccounts(FakeAccounts.account("h", 4, 0, null)))
                .verify(3).isEmpty());
        assertTrue(new VerifyAdminSessionService(new FakeAccounts(null)).verify(1).isEmpty());
    }
}
