package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.admin.AdminSession;

public interface ChangeAdminPasswordUseCase {

    /** Cambia la contraseña, invalida todas las sesiones anteriores y devuelve una sesión nueva. */
    AdminSession change(String currentPassword, String newPassword);
}
