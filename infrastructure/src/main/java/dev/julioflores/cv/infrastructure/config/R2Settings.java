package dev.julioflores.cv.infrastructure.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/**
 * Cloudflare R2 (API compatible con S3). Todo llega por entorno ({@code R2_*}); sin él la API arranca igual, la subida de
 * imágenes responde 503 {@code IMAGE_STORAGE_UNAVAILABLE} y el resto (incluido guardar contenido) no se entera.
 */
@ConfigMapping(prefix = "cv.r2")
public interface R2Settings {

    /** {@code https://<ACCOUNT_ID>.r2.cloudflarestorage.com} */
    Optional<String> endpoint();

    Optional<String> accessKeyId();

    Optional<String> secretAccessKey();

    Optional<String> bucket();

    /** Dominio público del bucket (sin barra final), de donde el navegador descarga las imágenes. */
    Optional<String> publicUrl();

    @WithDefault("PT5S")
    Duration connectTimeout();

    /** Tiempo máximo de cada intento (lectura del socket y llamada completa). */
    @WithDefault("PT10S")
    Duration requestTimeout();

    /** Hay con qué hablar con el bucket (la URL pública es independiente: sirve para componer URLs sin subir nada). */
    default boolean configured() {
        return present(endpoint()) && present(accessKeyId()) && present(secretAccessKey()) && present(bucket());
    }

    private static boolean present(Optional<String> value) {
        return value.filter(v -> !v.isBlank()).isPresent();
    }
}
