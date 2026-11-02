package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import dev.julioflores.cv.domain.Language;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/** Textos de error traducidos: problems_es.properties y problems_en.properties (claves CODIGO.title / CODIGO.detail). */
final class ProblemMessages {

    private ProblemMessages() {}

    static String title(ApiError error, Language language) {
        return bundle(language).getString(error.name() + ".title");
    }

    /** Rellena los marcadores {0}, {1}… del detalle. Recuerda duplicar los apóstrofos (`''`) en los .properties. */
    static String detail(ApiError error, Language language, List<?> args) {
        String pattern = bundle(language).getString(error.name() + ".detail");
        return new MessageFormat(pattern, Locale.forLanguageTag(language.tag())).format(args.toArray());
    }

    /** Cada idioma es un bundle propio, así no entra en juego el locale por defecto de la máquina. */
    static ResourceBundle bundle(Language language) {
        return ResourceBundle.getBundle("problems_" + language.tag(), Locale.ROOT);
    }
}
