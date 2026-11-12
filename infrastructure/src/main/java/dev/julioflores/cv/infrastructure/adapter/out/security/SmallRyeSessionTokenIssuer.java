package dev.julioflores.cv.infrastructure.adapter.out.security;

import dev.julioflores.cv.application.admin.AdminSession;
import dev.julioflores.cv.application.port.out.SessionTokenIssuer;
import dev.julioflores.cv.infrastructure.config.AdminConfig;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

/** Emite el JWT de sesión (HS256). Lleva {@code sv}, la versión de sesión que la API compara con la base de datos. */
@ApplicationScoped
public class SmallRyeSessionTokenIssuer implements SessionTokenIssuer {

    public static final String ISSUER = "cv-api";
    public static final String AUDIENCE = "cv-admin";

    private final AdminConfig config;
    private final Clock clock;

    SmallRyeSessionTokenIssuer(AdminConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    public AdminSession issue(int sessionVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.sessionLifespan());
        String token = Jwt.claims()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("admin")
                .groups(Set.of("admin"))
                .claim(AdminConfig.SESSION_VERSION_CLAIM, sessionVersion)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .jws()
                .algorithm(SignatureAlgorithm.HS256)
                .sign(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return new AdminSession(token, expiresAt);
    }
}
