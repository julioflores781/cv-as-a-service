package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.julioflores.cv.application.AdminFakes.FakeAccounts;
import dev.julioflores.cv.application.AdminFakes.RecordingAuditLog;
import dev.julioflores.cv.application.audit.AuditAction;
import java.util.Map;
import dev.julioflores.cv.application.service.LogoutAllSessionsService;
import org.junit.jupiter.api.Test;

class LogoutAllSessionsServiceTest {

    @Test
    void raisesTheSessionVersion() { // T21
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account("h", 2, 0, null));
        new LogoutAllSessionsService(accounts, new RecordingAuditLog()).logoutAll();
        assertEquals(1, accounts.invalidations);
        assertEquals(3, accounts.account.sessionVersion());
    }

    @Test
    void recordsSessionsRevokedAfterRaisingTheVersion() { // T37
        FakeAccounts accounts = new FakeAccounts(FakeAccounts.account("h", 2, 0, null));
        RecordingAuditLog audit = new RecordingAuditLog();
        new LogoutAllSessionsService(accounts, audit).logoutAll();
        assertEquals(RecordingAuditLog.success(AuditAction.SESSIONS_REVOKED, Map.of()), audit.only());
        assertEquals(1, accounts.invalidations);
    }
}
