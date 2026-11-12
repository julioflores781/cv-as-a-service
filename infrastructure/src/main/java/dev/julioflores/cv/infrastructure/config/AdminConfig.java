package dev.julioflores.cv.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Configuración de la administración. Sin un secreto JWT válido (por entorno, de al menos {@value #MIN_SECRET_LENGTH}
 * caracteres) el admin queda deshabilitado: la API pública arranca y sigue funcionando.
 */
@ApplicationScoped
public class AdminConfig {

    /** Claim del JWT con la versión de sesión: lo escribe el emisor y lo lee el filtro de sesión. */
    public static final String SESSION_VERSION_CLAIM = "sv";

    static final int MIN_SECRET_LENGTH = 32;
    private static final Logger LOG = Logger.getLogger(AdminConfig.class);

    private final String secret;
    private final Duration sessionLifespan;
    private final int maxFailures;
    private final Duration lock;
    private final boolean edgeBlock;

    AdminConfig(
            @ConfigProperty(name = "cv.admin.jwt.secret") Optional<String> secret,
            @ConfigProperty(name = "cv.admin.session.lifespan") Duration sessionLifespan,
            @ConfigProperty(name = "cv.admin.login.max-failures") int maxFailures,
            @ConfigProperty(name = "cv.admin.login.lock") Duration lock,
            @ConfigProperty(name = "cv.admin.edge-block") boolean edgeBlock) {
        this.secret = secret.orElse("");
        this.sessionLifespan = sessionLifespan;
        this.maxFailures = maxFailures;
        this.lock = lock;
        this.edgeBlock = edgeBlock;
    }

    void warnIfDisabled(@Observes StartupEvent event) {
        if (!enabled()) {
            LOG.warnf("Admin disabled: CV_ADMIN_JWT_SECRET is missing or shorter than %d characters", MIN_SECRET_LENGTH);
        }
    }

    public boolean enabled() {
        return secret.length() >= MIN_SECRET_LENGTH;
    }

    public String secret() {
        return secret;
    }

    public Duration sessionLifespan() {
        return sessionLifespan;
    }

    public int maxFailures() {
        return maxFailures;
    }

    public Duration lock() {
        return lock;
    }

    public boolean edgeBlock() {
        return edgeBlock;
    }
}
