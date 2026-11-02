package dev.julioflores.cv.infrastructure.adapter.in.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/** El contenido casi no cambia: caché pública corta, variando por idioma. */
public class ApiCacheHeaders {

    static final String CACHE_CONTROL_VALUE = "public, max-age=300";
    static final String VARY_VALUE = "Accept-Language";

    @ServerResponseFilter
    public void addHeaders(ContainerRequestContext request, ContainerResponseContext response) {
        if ("GET".equals(request.getMethod())
                && response.getStatus() == 200
                && request.getUriInfo().getPath().contains("api/v1")) {
            response.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
            response.getHeaders().putSingle(HttpHeaders.VARY, VARY_VALUE);
        }
    }
}
