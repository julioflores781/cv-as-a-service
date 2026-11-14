package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.domain.AdminAccount;
import dev.julioflores.cv.infrastructure.AdminTestSupport;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresAdminAccountRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PostgresAdminAccountRepositoryTest {

    @Inject
    PostgresAdminAccountRepository repository;

    @Inject
    AdminTestSupport support;

    private void failConcurrently(ExecutorService pool, int times) throws Exception {
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(pool.submit(() -> repository.recordFailure(5, Duration.ofMinutes(15))));
        }
        for (Future<?> task : tasks) {
            task.get();
        }
    }

    @Test
    void concurrentFailuresAreCountedExactlyAndLockOnceTheMaximumIsReached() throws Exception { // T69
        support.resetAccount();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            failConcurrently(pool, 4);
            AdminAccount four = repository.find().orElseThrow();
            assertEquals(4, four.failedAttempts());
            assertNull(four.lockedUntil(), "con 4 fallos aún no hay bloqueo");

            failConcurrently(pool, 20);
        } finally {
            pool.shutdown();
        }
        AdminAccount locked = repository.find().orElseThrow();
        assertEquals(24, locked.failedAttempts(), "ningún fallo se pierde");
        assertTrue(locked.isLockedAt(Instant.now()));
        assertTrue(locked.lockedUntil().isBefore(Instant.now().plus(Duration.ofMinutes(16))));
    }

    @Test
    void aSuccessAfterFailuresResetsThemAndStampsTheLastLogin() { // T70
        support.resetAccount();
        for (int i = 0; i < 5; i++) {
            repository.recordFailure(5, Duration.ofMinutes(15));
        }
        assertNotNull(repository.find().orElseThrow().lockedUntil());
        assertNull(repository.find().orElseThrow().lastLoginAt());

        repository.recordSuccess();

        AdminAccount account = repository.find().orElseThrow();
        assertEquals(0, account.failedAttempts());
        assertNull(account.lockedUntil());
        assertNotNull(account.lastLoginAt());
    }

    @Test
    void changingThePasswordRaisesTheSessionVersionAndStampsTheChangeInOneStatement() { // T71
        support.resetAccount();
        repository.recordFailure(1, Duration.ofMinutes(15));
        AdminAccount before = repository.find().orElseThrow();
        assertEquals(1, before.sessionVersion());

        int version = repository.changePassword("$argon2id$nuevo");

        AdminAccount after = repository.find().orElseThrow();
        assertEquals(2, version);
        assertEquals(2, after.sessionVersion());
        assertEquals("$argon2id$nuevo", after.passwordHash());
        assertFalse(after.passwordChangedAt().isBefore(before.passwordChangedAt()));
        assertEquals(0, after.failedAttempts());
        assertNull(after.lockedUntil());
    }

    @Test
    void invalidatingSessionsRaisesTheVersionOnly() { // extra fuera del plan
        support.resetAccount();
        String hash = repository.find().orElseThrow().passwordHash();
        assertEquals(2, repository.invalidateSessions());
        assertEquals(3, repository.invalidateSessions());
        assertEquals(hash, repository.find().orElseThrow().passwordHash());
    }

    @Test
    void replaceHashOnlyWorksWhileTheStoredHashIsTheExpectedOne() { // extra fuera del plan
        support.resetAccount();
        String stored = repository.find().orElseThrow().passwordHash();
        repository.replaceHash("$argon2id$otro", "$argon2id$intruso");
        assertEquals(stored, repository.find().orElseThrow().passwordHash());
        repository.replaceHash(stored, "$argon2id$rehecho");
        assertEquals("$argon2id$rehecho", repository.find().orElseThrow().passwordHash());
        assertEquals(1, repository.find().orElseThrow().sessionVersion());
    }

    @Test
    void withoutAnAccountReadsAreEmptyAndChangesFail() { // extra fuera del plan
        support.clear();
        assertTrue(repository.find().isEmpty());
        repository.recordFailure(5, Duration.ofMinutes(1));
        repository.recordSuccess();
        assertThrows(IllegalStateException.class, () -> repository.changePassword("$argon2id$x"));
        assertThrows(IllegalStateException.class, repository::invalidateSessions);
    }
}
