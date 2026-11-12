package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.application.service.AdminLoginLockedException;
import dev.julioflores.cv.application.service.ContentViolationsException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.adapter.in.rest.LanguageParams;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.List;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Único punto donde un error se convierte en respuesta HTTP: RFC 7807 (application/problem+json),
 * con código estable y texto en el idioma de la petición.
 */
public class ProblemExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ProblemExceptionMapper.class);

    @ServerExceptionMapper
    public Response business(ApplicationException e, UriInfo uri, HttpHeaders headers) {
        List<ProblemResponse.ProblemError> errors = e instanceof ContentViolationsException content
                ? content.violations().stream()
                        .map(v -> new ProblemResponse.ProblemError(v.path(), v.code().name())).toList()
                : null;
        Response.ResponseBuilder response = builder(ApiError.of(e.code()), language(uri, headers), e.args(), errors);
        if (e instanceof AdminLoginLockedException locked) {
            response.header(HttpHeaders.RETRY_AFTER, locked.retryAfterSeconds());
        }
        return response.build();
    }

    /** Sin sesión, con un token inválido o revocado: siempre el mismo 401 documentado, nunca un 500. */
    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthorized(UriInfo uri, HttpHeaders headers) {
        return problem(ApiError.ADMIN_UNAUTHORIZED, language(uri, headers), List.of());
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e, UriInfo uri, HttpHeaders headers) {
        return problem(ApiError.FORBIDDEN, language(uri, headers), List.of());
    }

    /** Cuerpo JSON ilegible o con un tipo equivocado: siempre el 400 genérico, sin el cuerpo por defecto de Quarkus. */
    @ServerExceptionMapper({MismatchedInputException.class, JsonProcessingException.class})
    public Response unreadableBody(UriInfo uri, HttpHeaders headers) {
        return problem(ApiError.BAD_REQUEST, language(uri, headers), List.of());
    }

    /** Rutas inexistentes (404), métodos no permitidos (405), cabeceras no soportadas (406/415)… */
    @ServerExceptionMapper
    public Response transport(WebApplicationException e, UriInfo uri, HttpHeaders headers) {
        return problem(ApiError.fromStatus(e.getResponse().getStatus()), language(uri, headers), List.of());
    }

    /**
     * El cuerpo supera el tope duro del servidor mientras se lee (típico de una subida {@code chunked} de más de 6 MB, que no
     * declaró su longitud). En la subida de imágenes es el mismo {@code IMAGE_TOO_LARGE} de siempre; en el resto de rutas, un 413 sin cuerpo (como el corte por longitud declarada); cualquier otro fallo de E/S sigue siendo inesperado.
     */
    @ServerExceptionMapper
    public Response ioFailure(IOException e, UriInfo uri, HttpHeaders headers) {
        if ("Request too large".equals(e.getMessage())) {
            if (uri.getPath().startsWith("/api/v1/admin/images")) {
                return problem(ApiError.IMAGE_TOO_LARGE, language(uri, headers), List.of("6"));
            }
            // El mismo 413 sin cuerpo que da el servidor cuando la longitud se declara de antemano.
            return Response.status(413).header("Connection", "close").build();
        }
        return unexpected(e, uri, headers);
    }

    /** Cualquier fallo inesperado: se registra con su traza y el cliente solo recibe un mensaje genérico. */
    @ServerExceptionMapper
    public Response unexpected(Exception e, UriInfo uri, HttpHeaders headers) {
        LOG.error("Unexpected error", e);
        return problem(ApiError.UNEXPECTED, language(uri, headers), List.of());
    }

    private static Language language(UriInfo uri, HttpHeaders headers) {
        return LanguageParams.resolve(
                uri.getQueryParameters().getFirst("lang"), headers.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE));
    }

    private static Response problem(ApiError error, Language language, List<?> args) {
        return builder(error, language, args, null).build();
    }

    /** Un 401 siempre lleva el desafío {@code WWW-Authenticate: Bearer}. */
    private static Response.ResponseBuilder builder(
            ApiError error, Language language, List<?> args, List<ProblemResponse.ProblemError> errors) {
        Response.ResponseBuilder response = Response.status(error.status())
                .type(ProblemResponse.MEDIA_TYPE)
                .entity(new ProblemResponse(
                        "about:blank",
                        ProblemMessages.title(error, language),
                        error.status(),
                        ProblemMessages.detail(error, language, args),
                        error.name(),
                        errors));
        return error.status() == 401 ? response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer") : response;
    }
}