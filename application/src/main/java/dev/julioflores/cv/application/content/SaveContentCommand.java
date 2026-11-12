package dev.julioflores.cv.application.content;

import java.util.Map;

/**
 * Un guardado bilingüe. Los documentos van en crudo (mapas y listas tal como llegaron): el dominio descarta {@code client}
 * en los anónimos, y para RECHAZARlo hay que ver el valor original. Un documento ausente es {@code null}.
 */
public record SaveContentCommand(
        int baseVersionEs, int baseVersionEn, String comment, Map<String, Object> es, Map<String, Object> en) {}
