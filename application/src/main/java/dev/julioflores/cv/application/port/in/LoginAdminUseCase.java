package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.admin.AdminSession;

public interface LoginAdminUseCase {

    /**
     * Comprueba la contraseña y abre una sesión. Falla con el mismo error si la contraseña es mala o no hay cuenta,
     * y no verifica nada mientras la cuenta esté bloqueada.
     */
    AdminSession login(String password);
}
