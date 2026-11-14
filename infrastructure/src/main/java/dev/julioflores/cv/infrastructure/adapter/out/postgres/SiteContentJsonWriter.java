package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.ArchitecturePort;
import dev.julioflores.cv.domain.PortAdapter;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ServiceOffer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

/**
 * El inverso exacto de {@link SiteContentJsonMapper}: del dominio a la forma almacenada (la de {@code db/sample/*.json}).
 * Lo que no tiene valor no se escribe (ni {@code null} ni claves vacías); las listas sí se escriben siempre.
 * {@link #canonical} pasa un documento ya validado por el lector y de vuelta: lo que se guarda es, por construcción,
 * algo que la API pública sabe leer, y con una forma única (sin campos desconocidos, sin nulos).
 */
public final class SiteContentJsonWriter {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private SiteContentJsonWriter() {}

    /** La forma almacenada de un documento en crudo ya validado. */
    public static ObjectNode canonical(ContentKind kind, JsonNode raw) {
        return switch (kind) {
            case SERVICES -> services(SiteContentJsonMapper.services(raw));
            case PROJECTS -> projects(SiteContentJsonMapper.projects(raw));
            case ARCHITECTURE -> architecture(SiteContentJsonMapper.architecture(raw));
        };
    }

    static ObjectNode services(List<ServiceOffer> services) {
        ObjectNode root = JSON.objectNode();
        root.set("services", array(services, s -> {
            ObjectNode node = JSON.objectNode();
            node.put("id", s.id());
            node.put("title", s.title());
            node.put("summary", s.summary());
            if (s.price() != null) {
                ObjectNode price = node.putObject("price");
                price.set("from", number(s.price().from()));
                price.put("currency", s.price().currency());
            }
            return node;
        }));
        return root;
    }

    static ObjectNode projects(List<Project> projects) {
        ObjectNode root = JSON.objectNode();
        root.set("projects", array(projects, p -> {
            ObjectNode node = JSON.objectNode();
            node.put("id", p.id());
            node.put("title", p.title());
            node.put("summary", p.summary());
            node.put("relation", p.relation().value());
            put(node, "role", p.role());
            node.put("disclosure", p.disclosure().value());
            put(node, "client", p.client());
            put(node, "clientLabel", p.clientLabel());
            if (p.period() != null) {
                node.put("startDate", p.period().start().toString());
                if (p.period().end() != null) {
                    node.put("endDate", p.period().end().toString());
                }
            }
            node.set("stack", array(p.stack(), JSON::textNode));
            node.set("highlights", array(p.highlights(), JSON::textNode));
            node.set("links", array(p.links(), l -> {
                ObjectNode link = JSON.objectNode();
                link.put("label", l.label());
                link.put("url", l.url());
                return link;
            }));
            if (!p.images().isEmpty()) {
                node.set("images", array(p.images(), i -> {
                    ObjectNode image = JSON.objectNode();
                    image.put("key", i.key());
                    image.put("alt", i.alt());
                    image.put("width", i.width());
                    image.put("height", i.height());
                    return image;
                }));
            }
            return node;
        }));
        return root;
    }

    static ObjectNode architecture(Architecture architecture) {
        ObjectNode root = JSON.objectNode();
        ObjectNode core = root.putObject("core");
        core.put("name", architecture.coreName());
        put(core, "description", architecture.coreDescription());
        root.set("ports", array(architecture.ports(), SiteContentJsonWriter::port));
        return root;
    }

    private static ObjectNode port(ArchitecturePort port) {
        ObjectNode node = JSON.objectNode();
        node.put("id", port.id());
        node.put("name", port.name());
        put(node, "description", port.description());
        node.set("adapters", array(port.adapters(), SiteContentJsonWriter::adapter));
        return node;
    }

    private static ObjectNode adapter(PortAdapter adapter) {
        ObjectNode node = JSON.objectNode();
        node.put("id", adapter.id());
        node.put("name", adapter.name());
        node.put("status", adapter.status().value());
        put(node, "note", adapter.note());
        return node;
    }

    private static <T> ArrayNode array(List<T> items, Function<T, JsonNode> write) {
        ArrayNode array = JSON.arrayNode();
        items.forEach(item -> array.add(write.apply(item)));
        return array;
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    /** Un entero se escribe como entero (1500, no 1500.0), con el nodo que usaría Jackson al leerlo. */
    private static JsonNode number(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 0) {
            return JSON.numberNode(normalized);
        }
        BigInteger integer = normalized.toBigIntegerExact();
        if (integer.bitLength() < Integer.SIZE) {
            return JSON.numberNode(integer.intValueExact());
        }
        return integer.bitLength() < Long.SIZE ? JSON.numberNode(integer.longValueExact()) : JSON.numberNode(integer);
    }
}
