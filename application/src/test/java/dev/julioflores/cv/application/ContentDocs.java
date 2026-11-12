package dev.julioflores.cv.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Documentos de ejemplo (CV ficticio de Ada Ejemplo) en crudo, tal como llegan del JSON. */
final class ContentDocs {

    private ContentDocs() {}

    /** Un mapa mutable con los pares dados, en orden. */
    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    static List<Object> list(Object... items) {
        return new ArrayList<>(List.of(items));
    }

    static Map<String, Object> service(String id, String title, Object price) {
        return map("id", id, "title", title, "summary", "Un servicio ficticio", "price", price);
    }

    static Map<String, Object> services(String lang) {
        boolean es = lang.equals("es");
        return map("services", list(
                service("servicio-ejemplo", es ? "Servicio de ejemplo" : "Sample service", map("from", 1500, "currency", "EUR")),
                service("servicio-a-consultar", es ? "Servicio a consultar" : "Service on request", null)));
    }

    static Map<String, Object> publicProject(String lang) {
        boolean es = lang.equals("es");
        return map("id", "proyecto-publico", "title", es ? "Proyecto público" : "Public project",
                "summary", es ? "Un producto propio" : "A product of my own", "relation", "own", "role", "Backend",
                "disclosure", "public", "client", "Ejemplo S.A.", "clientLabel", es ? "empresa ficticia" : "made-up company",
                "startDate", "2022-01", "stack", list("Java", "Quarkus"), "highlights", list("Un logro"),
                "links", list(map("label", es ? "Ver" : "See", "url", "https://example.com/proyecto")));
    }

    static Map<String, Object> anonymousProject(String lang) {
        boolean es = lang.equals("es");
        return map("id", "proyecto-anonimo", "title", es ? "Proyecto anónimo" : "Anonymous project",
                "summary", es ? "Para una empresa sin nombre" : "For an unnamed company", "relation", "employer",
                "disclosure", "anonymous", "clientLabel", es ? "prepaga de ejemplo" : "sample health insurer",
                "startDate", "2019-03", "endDate", "2020-11", "stack", list("Java"), "highlights", list(), "links", list());
    }

    static Map<String, Object> projects(String lang) {
        return map("projects", list(publicProject(lang), anonymousProject(lang)));
    }

    static Map<String, Object> adapter(String id, String status) {
        return map("id", id, "name", id, "status", status);
    }

    static Map<String, Object> architecture(String lang) {
        boolean es = lang.equals("es");
        return map("core", map("name", es ? "Dominio" : "Domain", "description", es ? "Reglas" : "Rules"),
                "ports", list(
                        map("id", "database", "name", es ? "Base de datos" : "Database",
                                "adapters", list(adapter("postgresql", "in-this-api"), adapter("mongodb", "production"))),
                        map("id", "queue", "name", es ? "Colas" : "Queues", "adapters", list(adapter("kafka", "experiment")))));
    }
}
