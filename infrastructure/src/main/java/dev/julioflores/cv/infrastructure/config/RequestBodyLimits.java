package dev.julioflores.cv.infrastructure.config;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Límite de cuerpo por ruta. Quarkus solo tiene uno global ({@code quarkus.http.limits.max-body-size}), que se sube a lo que
 * necesita la subida de imágenes (5 MB + multipart). Este filtro lo recorta a {@code cv.http.max-body-size} (128 KB) en
 * TODO lo demás: los endpoints públicos (HL7, contacto) y el resto del admin.
 *
 * <p>Corre justo después del control global de Quarkus (orden -2) y cubre los dos casos: con {@code Content-Length} rechaza
 * con 413 antes de leer nada; con {@code Transfer-Encoding: chunked} (sin longitud declarada) sustituye el tope que Quarkus
 * deja en el contexto ({@value #MAX_REQUEST_SIZE_KEY}) y que el lector del cuerpo aplica byte a byte. Esa clave es interna de
 * Quarkus: {@code Hl7ResourceTest} y {@code ContactMessagesResourceTest} prueban el caso chunked para detectar un cambio.
 */
@ApplicationScoped
public class RequestBodyLimits {

    /** Clave del contexto Vert.x donde Quarkus deja el tope de cuerpo que aplica al leerlo (valor {@code Long}). */
    static final String MAX_REQUEST_SIZE_KEY = "io.quarkus.max-request-size";

    /** La única ruta con el límite alto: la subida de imágenes (POST). */
    public static final String IMAGE_UPLOAD_PATH = "/api/v1/admin/images";

    private static final int AFTER_QUARKUS_GLOBAL_CHECK = -1;

    private final long limit;

    public RequestBodyLimits(@ConfigProperty(name = "cv.http.max-body-size") long limit) {
        this.limit = limit;
    }

    void register(@Observes Router router) {
        router.route().order(AFTER_QUARKUS_GLOBAL_CHECK).handler(this::enforce);
    }

    void enforce(RoutingContext context) {
        if (hasHighLimit(context)) {
            context.next();
            return;
        }
        long declared = declaredLength(context);
        if (declared > limit) {
            context.response().headers().add(HttpHeaders.CONNECTION, "close");
            context.response().setStatusCode(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.code()).end();
            return;
        }
        context.put(MAX_REQUEST_SIZE_KEY, limit);
        context.next();
    }

    private static boolean hasHighLimit(RoutingContext context) {
        String path = context.normalizedPath();
        boolean upload = path.equals(IMAGE_UPLOAD_PATH) || path.equals(IMAGE_UPLOAD_PATH + "/");
        return upload && "POST".equals(context.request().method().name());
    }

    /** El {@code Content-Length} declarado, o -1 si no hay o no es un número (el cuerpo se limita entonces al leerlo). */
    private static long declaredLength(RoutingContext context) {
        String header = context.request().getHeader(HttpHeaders.CONTENT_LENGTH);
        if (header == null) {
            return -1;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
