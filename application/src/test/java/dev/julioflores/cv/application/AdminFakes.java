package dev.julioflores.cv.application;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.out.AdminAccountRepository;
import dev.julioflores.cv.application.port.out.PasswordHasher;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import dev.julioflores.cv.domain.AdminAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fakes en memoria de los puertos de administración. */
final class AdminFakes {

    static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private AdminFakes() {}

    /** Hash de mentira: «fake:{coste}:{contraseña}». */
    static final class FakeHasher implements PasswordHasher {
        int currentCost = 2;
        final List<String> verified = new ArrayList<>();

        @Override
        public String hash(String password) {
            return "fake:" + currentCost + ":" + password;
        }

        @Override
        public boolean verify(String password, String hash) {
            verified.add(hash);
            return hash.equals("fake:" + hash.split(":")[1] + ":" + password);
        }

        @Override
        public boolean needsRehash(String hash) {
            return Integer.parseInt(hash.split(":")[1]) < currentCost;
        }
    }

    static final class FakeIssuer implements SessionTokenIssuer {
        @Override
        public AdminSession issue(int sessionVersion) {
            return new AdminSession("token-sv" + sessionVersion, NOW.plusSeconds(3600));
        }
    }

    static class FakeAccounts implements AdminAccountRepository {
        AdminAccount account;
        int failures;
        int successes;
        int maxFailuresSeen;
        Duration lockSeen;
        String replacedWith;
        int invalidations;

        FakeAccounts(AdminAccount account) {
            this.account = account;
        }

        static AdminAccount account(String hash, int sv, int failed, Instant lockedUntil) {
            return new AdminAccount(hash, sv, failed, lockedUntil, NOW.minusSeconds(86400), null);
        }

        @Override
        public Optional<AdminAccount> find() {
            return Optional.ofNullable(account);
        }

        @Override
        public void recordFailure(int maxFailures, Duration lock) {
            failures++;
            maxFailuresSeen = maxFailures;
            lockSeen = lock;
        }

        @Override
        public void recordSuccess() {
            successes++;
        }

        @Override
        public void replaceHash(String oldHash, String newHash) {
            replacedWith = newHash;
        }

        @Override
        public int changePassword(String newHash) {
            account = new AdminAccount(newHash, account.sessionVersion() + 1, 0, null, NOW, null);
            return account.sessionVersion();
        }

        @Override
        public int invalidateSessions() {
            invalidations++;
            account = new AdminAccount(account.passwordHash(), account.sessionVersion() + 1, 0, null,
                    account.passwordChangedAt(), null);
            return account.sessionVersion();
        }
    }
}
