package dev.julioflores.cv.application.content;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Regla de paridad ES/EN (R-10): mismos ids y mismo orden en cada nivel, y mismos campos neutros (precio, relación,
 * visibilidad, cliente, fechas, estado de los adaptadores). Todo lo demás (títulos, resúmenes, etiquetas…) es traducible
 * y no cuenta. Trabaja sobre documentos ya validados; lo que no encaja con la forma esperada se trata como ausente.
 * Las rutas no llevan prefijo de idioma ({@code projects[1].relation}).
 */
public final class ContentParity {

    private ContentParity() {}

    /** Diferencias entre dos documentos; vacío = paridad. Si solo hay uno de los dos, la diferencia es el idioma que falta. */
    public static List<Violation> compare(ContentKind kind, Map<String, Object> es, Map<String, Object> en) {
        if (es == null || en == null) {
            if (es == null && en == null) {
                return List.of();
            }
            return List.of(new Violation(es == null ? "es" : "en", ViolationCode.PARITY_MISMATCH));
        }
        List<Violation> differences = new ArrayList<>();
        switch (kind) {
            case SERVICES -> pairs(differences, "services", list(es.get("services")), list(en.get("services")),
                    (path, pair) -> same(differences, path + ".price", price(pair.es()), price(pair.en())));
            case PROJECTS -> pairs(differences, "projects", list(es.get("projects")), list(en.get("projects")),
                    (path, pair) -> {
                        for (String field : List.of("relation", "disclosure", "client", "startDate", "endDate")) {
                            same(differences, path + "." + field, text(pair.es().get(field)), text(pair.en().get(field)));
                        }
                        images(differences, path + ".images", list(pair.es().get("images")), list(pair.en().get("images")));
                    });
            case ARCHITECTURE -> pairs(differences, "ports", list(es.get("ports")), list(en.get("ports")),
                    (path, pair) -> pairs(differences, path + ".adapters", list(pair.es().get("adapters")),
                            list(pair.en().get("adapters")),
                            (adapterPath, adapters) -> same(differences, adapterPath + ".status",
                                    text(adapters.es().get("status")), text(adapters.en().get("status")))));
        }
        return differences;
    }

    private record Pair(Map<?, ?> es, Map<?, ?> en) {}

    /** Recorre las dos listas por posición: un id distinto es una diferencia; si coincide, se comparan sus campos neutros. */
    private static void pairs(
            List<Violation> differences, String path, List<Map<?, ?>> es, List<Map<?, ?>> en, BiConsumer<String, Pair> neutral) {
        for (int i = 0; i < Math.max(es.size(), en.size()); i++) {
            String at = path + "[" + i + "]";
            if (i >= es.size() || i >= en.size()) {
                differences.add(new Violation(at, ViolationCode.PARITY_MISMATCH));
            } else if (!Objects.equals(es.get(i).get("id"), en.get(i).get("id"))) {
                differences.add(new Violation(at + ".id", ViolationCode.PARITY_MISMATCH));
            } else {
                neutral.accept(at, new Pair(es.get(i), en.get(i)));
            }
        }
    }

    /** Las imágenes se comparan por posición: misma cantidad y misma {@code key}, {@code width} y {@code height} (el {@code alt} se traduce). */
    private static void images(List<Violation> differences, String path, List<Map<?, ?>> es, List<Map<?, ?>> en) {
        for (int i = 0; i < Math.max(es.size(), en.size()); i++) {
            String at = path + "[" + i + "]";
            if (i >= es.size() || i >= en.size()) {
                differences.add(new Violation(at, ViolationCode.PARITY_MISMATCH));
                continue;
            }
            for (String field : List.of("key", "width", "height")) {
                same(differences, at + "." + field, number(es.get(i).get(field)), number(en.get(i).get(field)));
            }
        }
    }

    private static Object number(Object value) {
        return value instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString() : text(value);
    }

    private static void same(List<Violation> differences, String path, Object es, Object en) {
        if (!Objects.equals(es, en)) {
            differences.add(new Violation(path, ViolationCode.PARITY_MISMATCH));
        }
    }

    private static List<Map<?, ?>> list(Object value) {
        return value instanceof List<?> items
                ? items.stream().filter(Map.class::isInstance).<Map<?, ?>>map(Map.class::cast).toList()
                : List.of();
    }

    /** Un texto neutro: ausente, nulo y en blanco son lo mismo. */
    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /** {@code "from currency"} con el importe normalizado (1500 y 1500.0 son iguales), o null si no hay precio. */
    private static String price(Map<?, ?> service) {
        if (!(service.get("price") instanceof Map<?, ?> price)) {
            return null;
        }
        String amount = price.get("from") instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString() : null;
        return amount + " " + price.get("currency");
    }
}
