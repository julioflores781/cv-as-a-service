package dev.julioflores.cv.application.content;

import dev.julioflores.cv.domain.ImageKey;
import dev.julioflores.cv.domain.ProjectImage;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de un documento editable, sobre el valor en crudo (mapas, listas, textos y números). Porta las de
 * {@code scripts/make-seed.py} y añade formatos, límites de longitud y cantidad, y campos desconocidos.
 * Devuelve TODAS las violaciones, cada una con su ruta ({@code es.projects[1].client}); vacío = válido.
 */
public final class ContentValidator {

    public static final int MAX_SERVICES = 30;
    public static final int MAX_PROJECTS = 50;
    public static final int MAX_PORTS = 20;
    public static final int MAX_ADAPTERS = 20;
    public static final int MAX_COMMENT = 200;
    public static final int MAX_IMAGES = 10;
    private static final int MAX_IMAGE_SIDE = 20_000;

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int MAX_ID = 64;
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern MONTH = Pattern.compile("(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])");
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000");
    private static final Set<String> RELATIONS = Set.of("own", "employer");
    private static final Set<String> DISCLOSURES = Set.of("public", "anonymous");
    private static final Set<String> ADAPTER_STATUS = Set.of("in-this-api", "production", "experiment");

    private ContentValidator() {}

    /** @param root prefijo de las rutas: normalmente el idioma ({@code es}) */
    public static List<Violation> validate(ContentKind kind, String root, Object document) {
        Run run = new Run(root);
        run.document(kind, document);
        return run.violations;
    }

    private static final class Run {

        final List<Violation> violations = new ArrayList<>();
        private final String root;

        Run(String root) {
            this.root = root;
        }

        void add(String path, ViolationCode code) {
            violations.add(new Violation(path, code));
        }

        void document(ContentKind kind, Object document) {
            Map<?, ?> map = object(document, root);
            if (map == null) {
                return;
            }
            switch (kind) {
                case SERVICES -> {
                    unknown(map, root, "services");
                    services(map.get("services"), root + ".services");
                }
                case PROJECTS -> {
                    unknown(map, root, "projects");
                    projects(map.get("projects"), root + ".projects");
                }
                case ARCHITECTURE -> {
                    unknown(map, root, "core", "ports");
                    architecture(map);
                }
            }
        }

        // ── Servicios ──────────────────────────────────────────────────────────────

        void services(Object value, String path) {
            Set<String> ids = new HashSet<>();
            int i = 0;
            for (Object item : items(value, path, MAX_SERVICES)) {
                String at = path + "[" + i++ + "]";
                Map<?, ?> service = object(item, at);
                if (service == null) {
                    continue;
                }
                unknown(service, at, "id", "title", "summary", "price");
                id(service, at, ids);
                text(service, at, "title", true, 120);
                text(service, at, "summary", true, 600);
                price(service.get("price"), at + ".price");
            }
        }

        void price(Object value, String path) {
            if (value == null) {
                return;
            }
            Map<?, ?> price = value instanceof Map<?, ?> map ? map : null;
            if (price == null) {
                add(path, ViolationCode.INVALID_VALUE);
                return;
            }
            unknown(price, path, "from", "currency");
            BigDecimal from = number(price.get("from"));
            if (from == null || from.signum() < 0 || from.compareTo(MAX_PRICE) > 0) {
                add(path + ".from", ViolationCode.INVALID_VALUE);
            }
            if (!(price.get("currency") instanceof String currency) || !CURRENCY.matcher(currency).matches()) {
                add(path + ".currency", ViolationCode.INVALID_VALUE);
            }
        }

        // ── Proyectos ──────────────────────────────────────────────────────────────

        void projects(Object value, String path) {
            Set<String> ids = new HashSet<>();
            int i = 0;
            for (Object item : items(value, path, MAX_PROJECTS)) {
                String at = path + "[" + i++ + "]";
                Map<?, ?> project = object(item, at);
                if (project == null) {
                    continue;
                }
                unknown(project, at, "id", "title", "summary", "relation", "role", "disclosure", "client", "clientLabel",
                        "startDate", "endDate", "stack", "highlights", "links", "images");
                id(project, at, ids);
                text(project, at, "title", true, 160);
                text(project, at, "summary", true, 800);
                text(project, at, "role", false, 160);
                text(project, at, "clientLabel", false, 160);
                String relation = enumValue(project, at, "relation", RELATIONS);
                String disclosure = enumValue(project, at, "disclosure", DISCLOSURES);
                client(project, at, relation, disclosure);
                dates(project, at);
                strings(project.get("stack"), at + ".stack", 30, 60);
                strings(project.get("highlights"), at + ".highlights", 20, 400);
                links(project.get("links"), at + ".links");
                images(project.get("images"), at + ".images", relation, disclosure);
            }
        }

        /**
         * Solo un proyecto propio y público puede llevar imágenes (una captura de uno de empleador o anónimo podría desvelar al
         * cliente). La validación solo mira el formato de la clave: no consulta el almacenamiento (una clave que no existe da una
         * imagen rota en la web, no un error de guardado).
         */
        void images(Object value, String path, String relation, String disclosure) {
            List<?> images = items(value, path, MAX_IMAGES);
            if (images.isEmpty()) {
                return;
            }
            if (!"own".equals(relation) || !"public".equals(disclosure)) {
                add(path, ViolationCode.IMAGES_NOT_ALLOWED_FOR_PROJECT);
                return;
            }
            int i = 0;
            for (Object item : images) {
                String at = path + "[" + i++ + "]";
                Map<?, ?> image = object(item, at);
                if (image == null) {
                    continue;
                }
                unknown(image, at, "key", "alt", "width", "height");
                Object key = image.get("key");
                if (key == null) {
                    add(at + ".key", ViolationCode.REQUIRED);
                } else if (!(key instanceof String text) || !ImageKey.isValid(text)) {
                    add(at + ".key", ViolationCode.IMAGE_KEY_INVALID);
                }
                text(image, at, "alt", true, ProjectImage.MAX_ALT);
                side(image, at, "width");
                side(image, at, "height");
            }
        }

        /** Ancho o alto: un entero positivo (el JSON no distingue 1280 de 1280.0, pero un decimal con parte fraccionaria no vale). */
        void side(Map<?, ?> image, String at, String key) {
            Object value = image.get(key);
            String path = at + "." + key;
            if (value == null) {
                add(path, ViolationCode.REQUIRED);
                return;
            }
            BigDecimal number = number(value);
            boolean integral = number != null && number.stripTrailingZeros().scale() <= 0;
            if (!integral || number.signum() <= 0 || number.compareTo(BigDecimal.valueOf(MAX_IMAGE_SIDE)) > 0) {
                add(path, ViolationCode.INVALID_VALUE);
            }
        }

        /** El cliente de un anónimo no debe llegar a la base de datos; el de un proyecto público de empresa es obligatorio. */
        void client(Map<?, ?> project, String at, String relation, String disclosure) {
            Object client = project.get("client");
            boolean present = client != null && !(client instanceof String s && s.isBlank());
            if ("anonymous".equals(disclosure)) {
                if (present) {
                    add(at + ".client", ViolationCode.ANONYMOUS_CLIENT_FORBIDDEN);
                }
                return;
            }
            if ("public".equals(disclosure) && "employer".equals(relation) && !present) {
                add(at + ".client", ViolationCode.CLIENT_REQUIRED);
                return;
            }
            text(project, at, "client", false, 160);
        }

        void dates(Map<?, ?> project, String at) {
            YearMonth start = month(project.get("startDate"), at + ".startDate");
            YearMonth end = month(project.get("endDate"), at + ".endDate");
            boolean startGiven = project.get("startDate") != null;
            if (end != null && (!startGiven || (start != null && end.isBefore(start)))) {
                add(at + ".endDate", ViolationCode.INVALID_DATE);
            }
        }

        YearMonth month(Object value, String path) {
            if (value == null) {
                return null;
            }
            if (!(value instanceof String text) || !MONTH.matcher(text).matches()) {
                add(path, ViolationCode.INVALID_DATE);
                return null;
            }
            return YearMonth.parse(text);
        }

        void links(Object value, String path) {
            int i = 0;
            for (Object item : items(value, path, 10)) {
                String at = path + "[" + i++ + "]";
                Map<?, ?> link = object(item, at);
                if (link == null) {
                    continue;
                }
                unknown(link, at, "label", "url");
                text(link, at, "label", true, 80);
                Object url = link.get("url");
                if (url == null) {
                    add(at + ".url", ViolationCode.REQUIRED);
                } else if (!(url instanceof String text) || !httpsUrl(text)) {
                    add(at + ".url", ViolationCode.INVALID_URL);
                }
            }
        }

        /** Solo {@code https} con host, sin espacios y de hasta 300 caracteres. */
        static boolean httpsUrl(String text) {
            if (text.length() > 300 || text.chars().anyMatch(Character::isWhitespace)) {
                return false;
            }
            try {
                URI uri = new URI(text);
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
            } catch (URISyntaxException e) {
                return false;
            }
        }

        // ── Arquitectura ───────────────────────────────────────────────────────────

        void architecture(Map<?, ?> document) {
            String corePath = root + ".core";
            Object coreValue = document.get("core");
            if (coreValue == null) {
                add(corePath, ViolationCode.REQUIRED);
            } else {
                Map<?, ?> core = object(coreValue, corePath);
                if (core != null) {
                    unknown(core, corePath, "name", "description");
                    text(core, corePath, "name", true, 80);
                    text(core, corePath, "description", false, 300);
                }
            }
            String portsPath = root + ".ports";
            Set<String> ids = new HashSet<>();
            int i = 0;
            for (Object item : items(document.get("ports"), portsPath, MAX_PORTS)) {
                String at = portsPath + "[" + i++ + "]";
                Map<?, ?> port = object(item, at);
                if (port == null) {
                    continue;
                }
                unknown(port, at, "id", "name", "description", "adapters");
                id(port, at, ids);
                text(port, at, "name", true, 80);
                text(port, at, "description", false, 300);
                adapters(port.get("adapters"), at + ".adapters");
            }
        }

        void adapters(Object value, String path) {
            Set<String> ids = new HashSet<>();
            int i = 0;
            for (Object item : items(value, path, MAX_ADAPTERS)) {
                String at = path + "[" + i++ + "]";
                Map<?, ?> adapter = object(item, at);
                if (adapter == null) {
                    continue;
                }
                unknown(adapter, at, "id", "name", "status", "note");
                id(adapter, at, ids);
                text(adapter, at, "name", true, 80);
                enumValue(adapter, at, "status", ADAPTER_STATUS);
                text(adapter, at, "note", false, 200);
            }
        }

        // ── Piezas comunes ─────────────────────────────────────────────────────────

        /** El valor como objeto, o null tras anotar la violación (ausente = REQUIRED, otro tipo = INVALID_VALUE). */
        Map<?, ?> object(Object value, String path) {
            if (value instanceof Map<?, ?> map) {
                return map;
            }
            add(path, value == null ? ViolationCode.REQUIRED : ViolationCode.INVALID_VALUE);
            return null;
        }

        void unknown(Map<?, ?> map, String path, String... allowed) {
            Set<String> known = Set.of(allowed);
            for (Object key : map.keySet()) {
                if (!(key instanceof String name) || !known.contains(name)) {
                    add(path + "." + key, ViolationCode.UNKNOWN_FIELD);
                }
            }
        }

        /** Los elementos de una lista opcional (ausente o nula = vacía); si hay demasiados no se recorre ninguno. */
        List<?> items(Object value, String path, int max) {
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> list)) {
                add(path, ViolationCode.INVALID_VALUE);
                return List.of();
            }
            if (list.size() > max) {
                add(path, ViolationCode.TOO_MANY);
                return List.of();
            }
            return list;
        }

        void id(Map<?, ?> item, String at, Set<String> seen) {
            Object id = item.get("id");
            String path = at + ".id";
            if (id == null) {
                add(path, ViolationCode.REQUIRED);
            } else if (!(id instanceof String text) || text.length() > MAX_ID || !ID.matcher(text).matches()) {
                add(path, ViolationCode.INVALID_ID);
            } else if (!seen.add(text)) {
                add(path, ViolationCode.DUPLICATE_ID);
            }
        }

        /** Un texto: ausente o en blanco es REQUIRED si es obligatorio; más largo que {@code max} caracteres, TOO_LONG. */
        void text(Map<?, ?> item, String at, String key, boolean required, int max) {
            Object value = item.get(key);
            String path = at + "." + key;
            if (value == null) {
                if (required) {
                    add(path, ViolationCode.REQUIRED);
                }
            } else if (!(value instanceof String text)) {
                add(path, ViolationCode.INVALID_VALUE);
            } else if (text.isBlank() && required) {
                add(path, ViolationCode.REQUIRED);
            } else if (text.codePointCount(0, text.length()) > max) {
                add(path, ViolationCode.TOO_LONG);
            }
        }

        /** Un valor obligatorio de un conjunto cerrado; devuelve el valor si es válido, o null. */
        String enumValue(Map<?, ?> item, String at, String key, Set<String> allowed) {
            Object value = item.get(key);
            String path = at + "." + key;
            if (value == null || value instanceof String text && text.isBlank()) {
                add(path, ViolationCode.REQUIRED);
                return null;
            }
            if (value instanceof String text && allowed.contains(text)) {
                return text;
            }
            add(path, ViolationCode.INVALID_VALUE);
            return null;
        }

        void strings(Object value, String path, int maxItems, int maxLength) {
            int i = 0;
            for (Object item : items(value, path, maxItems)) {
                String at = path + "[" + i++ + "]";
                if (!(item instanceof String text) || text.isBlank()) {
                    add(at, ViolationCode.INVALID_VALUE);
                } else if (text.codePointCount(0, text.length()) > maxLength) {
                    add(at, ViolationCode.TOO_LONG);
                }
            }
        }

        /** Un número finito (el JSON no distingue enteros de decimales), o null si no lo es. */
        static BigDecimal number(Object value) {
            if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
                return null;
            }
            if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return null;
            }
            return value instanceof Number n ? new BigDecimal(n.toString()) : null;
        }
    }
}
