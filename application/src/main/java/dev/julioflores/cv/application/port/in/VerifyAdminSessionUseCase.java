package dev.julioflores.cv.application.port.in;

import java.time.Instant;
import java.util.Optional;

public interface VerifyAdminSessionUseCase {

    /** Si {@code sessionVersion} es la vigente devuelve cuándo cambió la contraseña; si no (o sin cuenta), vacío. */
    Optional<Instant> verify(int sessionVersion);
}
