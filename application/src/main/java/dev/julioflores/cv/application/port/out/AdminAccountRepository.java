package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.AdminAccount;
import java.time.Duration;
import java.util.Optional;

/** Persistencia de la cuenta única. Cada operación de escritura es una sola sentencia atómica. */
public interface AdminAccountRepository {

    Optional<AdminAccount> find();

    /** Suma un fallo y, si llega a {@code maxFailures}, bloquea la cuenta durante {@code lock}. No hace nada sin cuenta. */
    void recordFailure(int maxFailures, Duration lock);

    /** Pone a cero los fallos, quita el bloqueo y anota el último acceso. */
    void recordSuccess();

    /** Sustituye {@code oldHash} por {@code newHash} (rehash por coste) solo si nadie cambió la contraseña entretanto. */
    void replaceHash(String oldHash, String newHash);

    /** Cambia el hash, sube la versión de sesión, limpia fallos y bloqueo; devuelve la versión nueva. */
    int changePassword(String newHash);

    /** Sube la versión de sesión (invalida los tokens vigentes) y devuelve la nueva. */
    int invalidateSessions();
}
