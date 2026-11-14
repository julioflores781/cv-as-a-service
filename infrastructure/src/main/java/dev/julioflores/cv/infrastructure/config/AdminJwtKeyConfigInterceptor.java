package dev.julioflores.cv.infrastructure.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SmallRye JWT no acepta el secreto HS256 en bruto: pide un JWK ({@code {"kty":"oct",...}}). Para que el operador solo
 * tenga que poner {@code CV_ADMIN_JWT_SECRET} (una cadena), este interceptor sirve {@code smallrye.jwt.verify.secretkey}
 * como el JWK de ese mismo secreto. La firma usa los mismos bytes (UTF-8), así que ambas mitades coinciden.
 * Sin secreto no devuelve nada: no hay clave y ningún token se puede verificar.
 */
/* Por encima de los interceptores de perfil y expresiones: así `proceed` ya devuelve el secreto resuelto. */
@Priority(5000)
public class AdminJwtKeyConfigInterceptor implements ConfigSourceInterceptor {

    static final String VERIFY_KEY = "smallrye.jwt.verify.secretkey";
    static final String SECRET = "cv.admin.jwt.secret";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        if (!VERIFY_KEY.equals(name)) {
            return context.proceed(name);
        }
        ConfigValue secret = context.proceed(SECRET);
        if (secret == null || secret.getValue() == null || secret.getValue().isBlank()) {
            return null;
        }
        return secret.withName(name).withValue(jwk(secret.getValue()));
    }

    static String jwk(String secret) {
        String k = Base64.getUrlEncoder().withoutPadding().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        return "{\"kty\":\"oct\",\"alg\":\"HS256\",\"k\":\"" + k + "\"}";
    }
}
