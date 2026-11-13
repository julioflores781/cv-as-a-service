package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.infrastructure.config.AdminRoutes;
import dev.julioflores.cv.application.port.in.VerifyAdminSessionUseCase;
import dev.julioflores.cv.application.service.AdminUnauthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.security.Principal;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Tras validar la firma del JWT compara su versión de sesión ({@code sv}) con la de la base de datos, en cada petición y
 * sin caché: así cambiar la contraseña o cerrar sesiones invalida los tokens al instante. Hace JDBC bloqueante, por eso
 * no es {@code nonBlocking}: Quarkus REST lo ejecuta fuera del hilo de eventos.
 */
public class AdminSessionFilter {

    private final VerifyAdminSessionUseCase verifySession;

    public AdminSessionFilter(VerifyAdminSessionUseCase verifySession) {
        this.verifySession = verifySession;
    }

    @ServerRequestFilter
    public void check(ContainerRequestContext request) {
        String path = AdminPaths.normalized(request);
        if (!AdminRoutes.isAdmin(path) || path.equals(AdminPaths.LOGIN_PATH)) {
            return;
        }
        Principal principal = request.getSecurityContext().getUserPrincipal();
        if (!(principal instanceof JsonWebToken jwt) || verifySession.verify(AdminPaths.sessionVersion(jwt)).isEmpty()) {
            throw new AdminUnauthorizedException();
        }
    }
}
