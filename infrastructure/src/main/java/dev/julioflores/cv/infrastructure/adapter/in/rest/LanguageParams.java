package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.domain.Language;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

/** `?lang=es|en` tiene prioridad; si falta, se usa Accept-Language; por defecto, español. */
public class LanguageParams {

    @QueryParam("lang")
    @Parameter(
            name = "lang",
            in = ParameterIn.QUERY,
            description = "Idioma del contenido y de los mensajes de error. Tiene prioridad sobre "
                    + "`Accept-Language`. Un valor desconocido o vacío se trata como `es`.",
            schema = @Schema(enumeration = {"es", "en"}, defaultValue = "es"),
            example = "en")
    String lang;

    @HeaderParam("Accept-Language")
    @Parameter(
            name = "Accept-Language",
            in = ParameterIn.HEADER,
            description = "Idioma preferido cuando no se envía `lang`. Se usa el primer idioma de la cabecera: "
                    + "`en-US,en;q=0.9` da inglés; cualquier otro, español.",
            example = "en-US,en;q=0.9")
    String acceptLanguage;

    Language language() {
        return resolve(lang, acceptLanguage);
    }

    /** Misma regla para los endpoints y para los errores: `lang` gana a `Accept-Language`; por defecto, español. */
    public static Language resolve(String lang, String acceptLanguage) {
        return Language.fromTag(lang != null && !lang.isBlank() ? lang : acceptLanguage);
    }
}
