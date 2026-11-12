package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.service.AdminUnauthorizedException;
import dev.julioflores.cv.infrastructure.config.AdminConfig;
import jakarta.json.JsonNumber;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Rutas de administración y lectura de la sesión del token, compartidas por el filtro, el guard y el recurso. */
final class AdminPaths {

    static final String ADMIN_ROOT = "/api/v1/admin";
    static final String ADMIN_PREFIX = ADMIN_ROOT + "/";
    static final String LOGIN_PATH = ADMIN_PREFIX + "auth/login";

    private AdminPaths() {}

    /** Ruta de la petición con barra inicial y sin barra final, para comparar prefijos sin sorpresas. */
    static String normalized(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        path = path.startsWith("/") ? path : "/" + path;
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    static boolean isAdmin(String normalizedPath) {
        return normalizedPath.equals(ADMIN_ROOT) || normalizedPath.startsWith(ADMIN_PREFIX);
    }

    /** La versión de sesión del token; un token sin {@code sv} numérico no puede ser de esta API. */
    static int sessionVersion(JsonWebToken jwt) {
        Object claim = jwt.getClaim(AdminConfig.SESSION_VERSION_CLAIM);
        if (claim instanceof JsonNumber number) {
            return number.intValue();
        }
        if (claim instanceof Number number) {
            return number.intValue();
        }
        throw new AdminUnauthorizedException();
    }
}
