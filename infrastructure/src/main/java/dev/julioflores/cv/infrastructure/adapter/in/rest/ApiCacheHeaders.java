package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.infrastructure.config.AdminRoutes;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/** El contenido casi no cambia: caché pública corta, variando por idioma. La zona de administración nunca se cachea. */
public class ApiCacheHeaders {

    static final String CACHE_CONTROL_VALUE = "public, max-age=300";
    static final String VARY_VALUE = "Accept-Language";

    static final String ADMIN_CACHE_CONTROL_VALUE = "no-store";

    @ServerResponseFilter
    public void addHeaders(ContainerRequestContext request, ContainerResponseContext response) {
        if (AdminRoutes.isAdmin(AdminPaths.normalized(request))) {
            // Sesiones y contenido de administración: nada de caché, ni compartida ni del navegador, sea cual sea el estado.
            response.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, ADMIN_CACHE_CONTROL_VALUE);
            return;
        }
        if ("GET".equals(request.getMethod())
                && response.getStatus() == 200
                && request.getUriInfo().getPath().contains("api/v1")) {
            response.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
            response.getHeaders().putSingle(HttpHeaders.VARY, VARY_VALUE);
        }
    }
}
