package dev.julioflores.cv.infrastructure.config;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.jboss.logging.MDC;

/**
 * Contexto de log de cada petición, en un handler Vert.x muy temprano (como {@link RequestBodyLimits}: un filtro JAX-RS no vería los
 * 404 de enrutado ni el 413 temprano).
 * <ul>
 *   <li>{@code X-Request-Id}: acepta el del cliente solo si cumple el patrón ({@link RequestIds}); si no, genera uno. Siempre sale
 *       en la respuesta, también en los 404 y en el 413 de {@code RequestBodyLimits}, y va al MDC como {@code request_id}.</li>
 *   <li>{@code client_ip} (MDC): solo en rutas {@code /api/v1/admin}, de {@code CF-Connecting-IP} y, si falta o no es válida, de
 *       {@code X-Client-IP}. En las rutas públicas no se escribe jamás.</li>
 * </ul>
 * Quarkus guarda el MDC en el contexto duplicado de Vert.x, así que llega a los hilos de trabajo de esa petición y al access log.
 */
@ApplicationScoped
public class RequestContextHandler {

    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_CLIENT_IP = "client_ip";
    public static final String MDC_ERROR_CODE = "error_code";

    static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    static final String X_CLIENT_IP = "X-Client-IP";

    /** Antes que {@link RequestBodyLimits} (orden -1) y que cualquier otro handler propio. */
    private static final int BEFORE_BODY_LIMITS = -3;

    void register(@Observes Router router) {
        router.route().order(BEFORE_BODY_LIMITS).handler(this::handle);
    }

    void handle(RoutingContext context) {
        String requestId = RequestIds.accept(context.request().getHeader(RequestIds.HEADER)).orElseGet(RequestIds::generate);
        context.response().headers().set(RequestIds.HEADER, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
        if (AdminRoutes.isAdmin(context.normalizedPath())) {
            Optional<String> ip = ClientIp.firstValid(
                    context.request().getHeader(CF_CONNECTING_IP), context.request().getHeader(X_CLIENT_IP));
            ip.ifPresent(value -> MDC.put(MDC_CLIENT_IP, value));
        }
        context.next();
    }
}
