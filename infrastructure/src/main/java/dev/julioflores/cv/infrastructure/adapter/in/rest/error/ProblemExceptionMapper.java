package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import dev.julioflores.cv.application.error.ApplicationException;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.adapter.in.rest.LanguageParams;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
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
        return problem(ApiError.of(e.code()), language(uri, headers), e.args());
    }

    /** Rutas inexistentes (404), métodos no permitidos (405), cabeceras no soportadas (406/415)… */
    @ServerExceptionMapper
    public Response transport(WebApplicationException e, UriInfo uri, HttpHeaders headers) {
        return problem(ApiError.fromStatus(e.getResponse().getStatus()), language(uri, headers), List.of());
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
        return Response.status(error.status())
                .type(ProblemResponse.MEDIA_TYPE)
                .entity(new ProblemResponse(
                        "about:blank",
                        ProblemMessages.title(error, language),
                        error.status(),
                        ProblemMessages.detail(error, language, args),
                        error.name()))
                .build();
    }
}
