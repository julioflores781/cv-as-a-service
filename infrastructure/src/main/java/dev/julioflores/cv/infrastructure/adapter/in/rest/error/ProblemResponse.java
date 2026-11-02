package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

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
                + "no cambia con el idioma.", example = "EXPERIENCE_NOT_FOUND") String code) {

    public static final String MEDIA_TYPE = "application/problem+json";
}
