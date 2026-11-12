package dev.julioflores.cv.application.port.in;

public interface LogoutAllSessionsUseCase {

    /** Invalida todas las sesiones, la propia incluida. */
    void logoutAll();
}
