package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.service.LogoutAllSessionsService;
import org.junit.jupiter.api.Test;

class LogoutAllSessionsServiceTest {

    @Test
    void raisesTheSessionVersion() { // T21
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account("h", 2, 0, null));
        new LogoutAllSessionsService(accounts).logoutAll();
        assertEquals(1, accounts.invalidations);
        assertEquals(3, accounts.account.sessionVersion());
    }
}
