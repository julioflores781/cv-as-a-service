package dev.julioflores.cv.infrastructure;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/** Configura la API contra el MinIO de pruebas (un R2 de mentira): solo las clases que lo piden lo ven; el resto tiene R2 sin configurar. */
public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        return Map.of(
                "cv.r2.endpoint", MinioSupport.endpoint(),
                "cv.r2.access-key-id", MinioSupport.user(),
                "cv.r2.secret-access-key", MinioSupport.password(),
                "cv.r2.bucket", MinioSupport.BUCKET,
                "cv.r2.public-url", MinioSupport.PUBLIC_URL);
    }

    @Override
    public void stop() {
        // El contenedor es de la JVM entera (MinioSupport) y lo recoge Ryuk al terminar.
    }
}
