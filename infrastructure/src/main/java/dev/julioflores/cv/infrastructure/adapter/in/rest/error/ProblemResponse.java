package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "Problem",
        description = "Error según RFC 7807 (`application/problem+json`) con un código estable. "
                + "`title` y `detail` van en el idioma pedido (`?lang` o `Accept-Language`; español por defecto).")
public record ProblemResponse(
        @Schema(required = true, description = "Tipo de problema. Siempre `about:blank`: el código y el estado bastan.",
                example = "about:blank") String type,
        @Schema(required = true, description = "Resumen corto del error, en el idioma pedido.",
                example = "Experiencia no encontrada") String title,
        @Schema(required = true, description = "Código de estado HTTP.", example = "404") int status,
        @Schema(required = true, description = "Explicación concreta de este error, en el idioma pedido.",
                example = "No existe ninguna experiencia con el id «nope».") String detail,
        @Schema(required = true, description = "Identificador estable del error. Úsalo para decidir en tu cliente: "
                + "no cambia con el idioma.", example = "EXPERIENCE_NOT_FOUND") String code,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Solo en `CONTENT_INVALID` y `CONTENT_PARITY_BROKEN`: cada problema del contenido, con su "
                + "ruta (`es.projects[1].client`) y su código (`ANONYMOUS_CLIENT_FORBIDDEN`, `TOO_LONG`, `PARITY_MISMATCH`…).")
                List<ProblemError> errors) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public ProblemResponse(String type, String title, int status, String detail, String code) {
        this(type, title, status, detail, code, null);
    }

    @Schema(name = "ProblemError", description = "Un problema concreto del contenido enviado.")
    public record ProblemError(
            @Schema(required = true, description = "Dónde está el problema: idioma y ruta dentro del documento.",
                    example = "es.projects[1].client") String path,
            @Schema(required = true, description = "Qué problema es. Valores: `REQUIRED`, `TOO_LONG`, `TOO_MANY`, `UNKNOWN_FIELD`, "
                    + "`DUPLICATE_ID`, `INVALID_ID`, `INVALID_VALUE`, `INVALID_DATE`, `INVALID_URL`, "
                    + "`ANONYMOUS_CLIENT_FORBIDDEN`, `CLIENT_REQUIRED`, `PARITY_MISMATCH`.",
                    example = "ANONYMOUS_CLIENT_FORBIDDEN") String code) {}
}
