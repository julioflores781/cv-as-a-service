package dev.julioflores.cv.infrastructure.config;

import java.util.regex.Pattern;

/**
 * El único criterio de «ruta de administración» ({@code /api/v1/admin} y lo que cuelga de ella). Lo usan el handler de contexto
 * de log, {@code AdminEdgeGuard}, el filtro de sesión y la caché, para que ninguno discrepe: una variante que JAX-RS enruta a un
 * recurso admin (parámetros de matriz {@code ;x=1}, barra final) también cuenta aquí. La ruta debe llegar ya normalizada de
 * {@code //}, {@code /./} y porcentajes (Vert.x {@code normalizedPath()} o {@code UriInfo.getPath()}).
 */
public final class AdminRoutes {

    public static final String ROOT = "/api/v1/admin";
    public static final String PREFIX = ROOT + "/";

    private static final Pattern MATRIX_PARAMS = Pattern.compile(";[^/]*");

    private AdminRoutes() {}

    public static boolean isAdmin(String path) {
        String clean = MATRIX_PARAMS.matcher(path).replaceAll("");
        return clean.equals(ROOT) || clean.startsWith(PREFIX);
    }
}
