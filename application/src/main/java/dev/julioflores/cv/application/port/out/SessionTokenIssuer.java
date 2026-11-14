package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.admin.AdminSession;

public interface SessionTokenIssuer {

    /** Emite un token firmado que lleva la versión de sesión dada. */
    AdminSession issue(int sessionVersion);
}
