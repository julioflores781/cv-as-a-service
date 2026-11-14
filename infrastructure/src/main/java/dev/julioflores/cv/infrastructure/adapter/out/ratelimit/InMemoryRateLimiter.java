package dev.julioflores.cv.infrastructure.adapter.out.ratelimit;

import dev.julioflores.cv.application.port.out.RateLimiter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Ventana deslizante por clave, en memoria. Basta mientras la API corra en una sola instancia (es el caso del NAS);
 * con varias réplicas habría que moverla a Redis o a la base de datos. Se reinicia con la aplicación.
 * No es un bean: {@code RateLimiterConfig} crea una instancia por uso (contacto y login), cada una con su ventana.
 */
public class InMemoryRateLimiter implements RateLimiter {

    /** Tope de claves recordadas: pasado este punto se descartan las que ya caducaron. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final int max;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public InMemoryRateLimiter(int max, Duration window) {
        this(max, window, System::nanoTime);
    }

    InMemoryRateLimiter(int max, Duration window, LongSupplier nanoClock) {
        this.max = max;
        this.windowNanos = window.toNanos();
        this.nanoClock = nanoClock;
    }

    @Override
    public synchronized boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        if (hits.size() > PURGE_THRESHOLD) {
            hits.values().removeIf(times -> expired(times.peekLast(), now));
        }
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && expired(times.peekFirst(), now)) {
            times.pollFirst();
        }
        if (times.size() >= max) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    private boolean expired(Long time, long now) {
        return time == null || now - time >= windowNanos;
    }
}
