package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.infrastructure.adapter.out.ratelimit.InMemoryRateLimiter;
import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.infrastructure.config.RateLimiterConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** Es @QuarkusTest solo para que JaCoCo mida la clase (ver CLAUDE.md); construye el limitador con un reloj propio. */
@QuarkusTest
class InMemoryRateLimiterTest {

    @Inject
    @Named(RateLimiterConfig.CONTACT)
    RateLimiter contact;

    @Inject
    @Named(RateLimiterConfig.ADMIN_LOGIN)
    RateLimiter adminLogin;

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

    @Test
    void theLoginLimitIsTenPerMinuteAndIndependentFromTheContactOne() throws Exception { // T90 (parte limitador)
        InMemoryRateLimiter login = limiter(10, Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            assertTrue(login.tryAcquire("admin-login"));
        }
        assertFalse(login.tryAcquire("admin-login"));
        now.set(Duration.ofMinutes(1).toNanos());
        assertTrue(login.tryAcquire("admin-login"));

        // Los dos beans del contenedor son instancias distintas: agotar uno no toca al otro.
        assertTrue(contact != adminLogin);
        String key = "independencia-" + System.nanoTime();
        int contactMax = 3; // %test.cv.contact.rate-limit.max
        for (int i = 0; i < contactMax; i++) {
            assertTrue(contact.tryAcquire(key));
        }
        assertFalse(contact.tryAcquire(key));
        assertTrue(adminLogin.tryAcquire(key), "el cupo de contacto no debe consumir el del login");
    }
}
