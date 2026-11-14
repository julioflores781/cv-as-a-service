package dev.julioflores.cv.infrastructure.config;

import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.infrastructure.adapter.out.ratelimit.InMemoryRateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Dos limitadores independientes (cada uno con su cupo y su ventana): los mensajes de contacto y el login del admin. */
@ApplicationScoped
public class RateLimiterConfig {

    public static final String CONTACT = "contactRateLimiter";
    public static final String ADMIN_LOGIN = "adminLoginRateLimiter";

    @Produces
    @ApplicationScoped
    @Named(CONTACT)
    RateLimiter contact(
            @ConfigProperty(name = "cv.contact.rate-limit.max") int max,
            @ConfigProperty(name = "cv.contact.rate-limit.window") Duration window) {
        return new InMemoryRateLimiter(max, window);
    }

    @Produces
    @ApplicationScoped
    @Named(ADMIN_LOGIN)
    RateLimiter adminLogin(
            @ConfigProperty(name = "cv.admin.login.rate-limit.max") int max,
            @ConfigProperty(name = "cv.admin.login.rate-limit.window") Duration window) {
        return new InMemoryRateLimiter(max, window);
    }
}
