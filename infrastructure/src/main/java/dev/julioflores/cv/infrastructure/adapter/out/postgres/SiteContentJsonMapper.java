package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import static dev.julioflores.cv.infrastructure.adapter.out.postgres.ResumeJsonMapper.list;
import static dev.julioflores.cv.infrastructure.adapter.out.postgres.ResumeJsonMapper.strings;
import static dev.julioflores.cv.infrastructure.adapter.out.postgres.ResumeJsonMapper.text;

import com.fasterxml.jackson.databind.JsonNode;
import dev.julioflores.cv.domain.AdapterStatus;
import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.ArchitecturePort;
import dev.julioflores.cv.domain.Disclosure;
import dev.julioflores.cv.domain.Period;
import dev.julioflores.cv.domain.PortAdapter;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ProjectImage;
import dev.julioflores.cv.domain.ProjectLink;
import dev.julioflores.cv.domain.ProjectRelation;
import dev.julioflores.cv.domain.ServiceOffer;
import dev.julioflores.cv.domain.ServicePrice;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Traduce los documentos JSON de `content_version` (columna `content`) al dominio. Un valor desconocido en un enum lanza.
 * Las imágenes de un proyecto se guardan por su clave; la URL pública se compone al servirlas, no aquí.
 */
final class SiteContentJsonMapper {

    private SiteContentJsonMapper() {}

    static List<ServiceOffer> services(JsonNode root) {
        return list(root.path("services"), n -> new ServiceOffer(
                text(n, "id"), text(n, "title"), text(n, "summary"), price(n.path("price"))));
    }

    private static ServicePrice price(JsonNode node) {
        return node.isMissingNode() || node.isNull()
                ? null
                : new ServicePrice(new BigDecimal(text(node, "from")), text(node, "currency"));
    }

    static List<Project> projects(JsonNode root) {
        return list(root.path("projects"), n -> new Project(
                text(n, "id"),
                text(n, "title"),
                text(n, "summary"),
                ProjectRelation.fromValue(text(n, "relation")),
                text(n, "role"),
                Disclosure.fromValue(text(n, "disclosure")),
                text(n, "client"),
                text(n, "clientLabel"),
                period(n),
                strings(n.path("stack")),
                strings(n.path("highlights")),
                list(n.path("links"), l -> new ProjectLink(text(l, "label"), text(l, "url"))),
                list(n.path("images"), i -> new ProjectImage(
                        text(i, "key"), text(i, "alt"), i.path("width").asInt(), i.path("height").asInt()))));
    }

    private static Period period(JsonNode project) {
        String start = text(project, "startDate");
        String end = text(project, "endDate");
        return start == null ? null : new Period(YearMonth.parse(start), end == null ? null : YearMonth.parse(end));
    }

    static Architecture architecture(JsonNode root) {
        JsonNode core = root.path("core");
        return new Architecture(
                text(core, "name"),
                text(core, "description"),
                list(root.path("ports"), p -> new ArchitecturePort(
                        text(p, "id"),
                        text(p, "name"),
                        text(p, "description"),
                        list(p.path("adapters"), a -> new PortAdapter(
                                text(a, "id"), text(a, "name"), AdapterStatus.fromValue(text(a, "status")), text(a, "note"))))));
    }
}
