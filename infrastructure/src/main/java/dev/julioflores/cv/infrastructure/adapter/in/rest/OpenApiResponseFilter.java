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
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Documenta lo que es común a todos los GET de /api/v1: las cabeceras de caché de los 200 y el 500.
 * Así no se repite en cada método (ver {@link ApiCacheHeaders} y las constantes que comparte).
 */
public class OpenApiResponseFilter implements OASFilter {

    private static final Map<String, Object> UNEXPECTED_EXAMPLE = unexpectedExample();

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        documentErrorCodes(openAPI);
        openAPI.getPaths().getPathItems().forEach((path, item) -> {
            if (!path.startsWith("/api/v1") || item.getGET() == null) {
                return;
            }
            APIResponses responses = item.getGET().getResponses();
            APIResponse ok = responses.getAPIResponse("200");
            if (ok != null) {
                ok.addHeader(HttpHeaders.CACHE_CONTROL,
                        header("Caché pública durante 5 minutos.", ApiCacheHeaders.CACHE_CONTROL_VALUE));
                ok.addHeader(HttpHeaders.VARY, header(
                        "La respuesta depende del idioma: se cachea por separado según `Accept-Language`.",
                        ApiCacheHeaders.VARY_VALUE));
            }
            responses.addAPIResponse("500", OASFactory.createAPIResponse()
                    .description("Error inesperado. El detalle no revela información interna y el error no se cachea.")
                    .content(OASFactory.createContent().addMediaType(ProblemResponse.MEDIA_TYPE,
                            OASFactory.createMediaType()
                                    .schema(OASFactory.createSchema().ref("#/components/schemas/Problem"))
                                    .example(UNEXPECTED_EXAMPLE))));
        });
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
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("type", "about:blank");
        example.put("title", "Error interno");
        example.put("status", 500);
        example.put("detail", "Ha ocurrido un error inesperado.");
        example.put("code", "UNEXPECTED");
        return example;
    }

    private static Header header(String description, String example) {
        return OASFactory.createHeader()
                .description(description)
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING).example(example));
    }
}
