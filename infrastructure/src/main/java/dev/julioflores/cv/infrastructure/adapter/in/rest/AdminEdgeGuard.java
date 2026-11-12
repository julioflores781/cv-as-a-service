package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.infrastructure.config.AdminConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Defensa en profundidad para {@code /api/v1/admin}.
 * <ul>
 *   <li>Con {@code CV_ADMIN_EDGE_BLOCK=true}, responde 404 a toda petición con cabeceras de Cloudflare ({@code CF-Ray} o
 *       {@code CF-Connecting-IP}): lo que llega por el túnel las lleva, la llamada interna de cv-web no.</li>
 *   <li>Sin un secreto JWT válido, el admin está deshabilitado y sus rutas responden 404.</li>
 * </ul>
 * No sustituye a la regla de Cloudflare que bloquea la ruta (ver el plan, §5.4).
 */
public class AdminEdgeGuard {

    static final String CF_RAY = "CF-Ray";
    static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private final AdminConfig config;

    public AdminEdgeGuard(AdminConfig config) {
        this.config = config;
    }

    @ServerRequestFilter(preMatching = true)
    public void guard(ContainerRequestContext request) {
        if (!AdminPaths.isAdmin(AdminPaths.normalized(request))) {
            return;
        }
        boolean viaCloudflare =
                request.getHeaderString(CF_RAY) != null || request.getHeaderString(CF_CONNECTING_IP) != null;
        if (!config.enabled() || (config.edgeBlock() && viaCloudflare)) {
            throw new NotFoundException();
        }
    }
}
