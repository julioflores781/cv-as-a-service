package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.infrastructure.adapter.out.ratelimit.InMemoryRateLimiter;
import io.quarkus.test.junit.QuarkusTest;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** Es @QuarkusTest solo para que JaCoCo mida la clase (ver CLAUDE.md); construye el limitador con un reloj propio. */
@QuarkusTest
class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong();

    private InMemoryRateLimiter limiter(int max, Duration window) throws Exception {
        Constructor<InMemoryRateLimiter> constructor =
                InMemoryRateLimiter.class.getDeclaredConstructor(int.class, Duration.class, LongSupplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance(max, window, (LongSupplier) now::get);
    }

    @Test
    void allowsUpToTheMaximumThenRejects() throws Exception {
        InMemoryRateLimiter limiter = limiter(2, Duration.ofHours(1));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() throws Exception {
        InMemoryRateLimiter limiter = limiter(1, Duration.ofHours(1));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void theQuotaComesBackWhenTheWindowSlidesPast() throws Exception {
        InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(10));
        assertTrue(limiter.tryAcquire("a"));
        now.set(Duration.ofMinutes(9).toNanos());
        assertFalse(limiter.tryAcquire("a"));
        now.set(Duration.ofMinutes(10).toNanos());
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void rejectedAttemptsDoNotExtendTheWindow() throws Exception {
        InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(10));
        assertTrue(limiter.tryAcquire("a"));
        now.set(Duration.ofMinutes(5).toNanos());
        assertFalse(limiter.tryAcquire("a"));
        now.set(Duration.ofMinutes(10).toNanos());
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void expiredKeysArePurgedWhenTooManyAreTracked() throws Exception {
        InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(1));
        for (int i = 0; i < 10_050; i++) {
            assertTrue(limiter.tryAcquire("k" + i));
        }
        now.set(Duration.ofMinutes(2).toNanos());
        assertTrue(limiter.tryAcquire("late"));
        assertTrue(limiter.tryAcquire("k0"));
    }
}
