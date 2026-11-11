package dev.julioflores.cv.application.port.out;

public interface RateLimiter {

    /** Consume un intento de {@code key}; {@code false} si ya agotó su cupo en la ventana actual. */
    boolean tryAcquire(String key);
}
