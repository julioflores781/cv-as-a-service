package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ApiError;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Documenta lo que es común a /api/v1: las cabeceras de caché de los 200 de los GET y el 500 de cada operación.
 * Así no se repite en cada método (ver {@link ApiCacheHeaders} y las constantes que comparte).
 */
public class OpenApiResponseFilter implements OASFilter {

    private static final Map<String, Object> UNEXPECTED_EXAMPLE = unexpectedExample();
    private static final Map<String, Object> UNAUTHORIZED_EXAMPLE = problemExample(
            "Sesión no válida", 401,
            "Falta la sesión, ha caducado o ha sido revocada. Inicia sesión de nuevo.", "ADMIN_UNAUTHORIZED");

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        documentErrorCodes(openAPI);
        openAPI.getPaths().getPathItems().forEach((path, item) -> {
            if (!path.startsWith("/api/v1")) {
                return;
            }
            if (path.startsWith("/api/v1/admin")) {
                documentAdmin(item);
                return;
            }
            if (item.getGET() != null) {
                APIResponse ok = item.getGET().getResponses().getAPIResponse("200");
                if (ok != null) {
                    ok.addHeader(HttpHeaders.CACHE_CONTROL,
                            header("Caché pública durante 5 minutos.", ApiCacheHeaders.CACHE_CONTROL_VALUE));
                    ok.addHeader(HttpHeaders.VARY, header(
                            "La respuesta depende del idioma: se cachea por separado según `Accept-Language`.",
                            ApiCacheHeaders.VARY_VALUE));
                }
                addUnexpected(item.getGET().getResponses());
            }
            if (item.getPOST() != null) {
                addUnexpected(item.getPOST().getResponses());
            }
        });
    }

    /** Admin: nunca se cachea, y las operaciones con `bearerAuth` pueden dar 401. */
    private static void documentAdmin(PathItem item) {
        item.getOperations().values().forEach(operation -> {
            APIResponse ok = operation.getResponses().getAPIResponse("200");
            if (ok != null) {
                ok.addHeader(HttpHeaders.CACHE_CONTROL,
                        header("Las respuestas de administración nunca se cachean.", ApiCacheHeaders.ADMIN_CACHE_CONTROL_VALUE));
            }
            if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                operation.getResponses().addAPIResponse("401", OASFactory.createAPIResponse()
                        .description("Sin sesión, token inválido o caducado, o sesión revocada (`ADMIN_UNAUTHORIZED`).")
                        .addHeader(HttpHeaders.WWW_AUTHENTICATE, header("Desafío de autenticación.", "Bearer"))
                        .content(OASFactory.createContent().addMediaType(ProblemResponse.MEDIA_TYPE,
                                OASFactory.createMediaType()
                                        .schema(OASFactory.createSchema().ref("#/components/schemas/Problem"))
                                        .example(UNAUTHORIZED_EXAMPLE))));
            }
            addUnexpected(operation.getResponses());
        });
    }

    private static void addUnexpected(APIResponses responses) {
        responses.addAPIResponse("500", OASFactory.createAPIResponse()
                .description("Error inesperado. El detalle no revela información interna y el error no se cachea.")
                .content(OASFactory.createContent().addMediaType(ProblemResponse.MEDIA_TYPE,
                        OASFactory.createMediaType()
                                .schema(OASFactory.createSchema().ref("#/components/schemas/Problem"))
                                .example(UNEXPECTED_EXAMPLE))));
    }

    /** Los códigos del esquema Problem salen del enum ApiError, así la documentación no se desincroniza. */
    private static void documentErrorCodes(OpenAPI openAPI) {
        Schema code = openAPI.getComponents().getSchemas().get("Problem").getProperties().get("code");
        code.description(Arrays.stream(ApiError.values())
                        .map(e -> "`" + e.name() + "` (" + e.status() + ")")
                        .collect(Collectors.joining(", ",
                                "Identificador estable del error. Úsalo para decidir en tu cliente: "
                                        + "no cambia con el idioma. Valores: ", ".")))
                .enumeration(Arrays.stream(ApiError.values()).<Object>map(Enum::name).toList());
    }

    private static Map<String, Object> unexpectedExample() {
        return problemExample("Error interno", 500, "Ha ocurrido un error inesperado.", "UNEXPECTED");
    }

    private static Map<String, Object> problemExample(String title, int status, String detail, String code) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("type", "about:blank");
        example.put("title", title);
        example.put("status", status);
        example.put("detail", detail);
        example.put("code", code);
        return example;
    }

    private static Header header(String description, String example) {
        return OASFactory.createHeader()
                .description(description)
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING).example(example));
    }
}
