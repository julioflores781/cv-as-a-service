package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.architecture;
import static dev.julioflores.cv.application.ContentDocs.list;
import static dev.julioflores.cv.application.ContentDocs.map;
import static dev.julioflores.cv.application.ContentDocs.projects;
import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentParity;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T40-T42: la regla R-10 (mismos ids y orden, mismos campos neutros; lo demás es traducible). */
class ContentParityTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> document, String list, int index) {
        return (Map<String, Object>) ((List<Object>) document.get(list)).get(index);
    }

    private static Violation mismatch(String path) {
        return new Violation(path, ViolationCode.PARITY_MISMATCH);
    }

    @Test
    void sameIdsAndNeutralFieldsMeanParity() { // T40
        assertEquals(List.of(), ContentParity.compare(ContentKind.SERVICES, services("es"), services("en")));
        assertEquals(List.of(), ContentParity.compare(ContentKind.PROJECTS, projects("es"), projects("en")));
        assertEquals(List.of(), ContentParity.compare(ContentKind.ARCHITECTURE, architecture("es"), architecture("en")));
    }

    @Test
    void aMissingIdADifferentOrderOrADifferentNeutralFieldIsListedWithItsPath() { // T41
        Map<String, Object> missing = services("en");
        ((List<Object>) missing.get("services")).remove(1);
        assertEquals(List.of(mismatch("services[1]")), ContentParity.compare(ContentKind.SERVICES, services("es"), missing));
        assertEquals(List.of(mismatch("services[1]")), ContentParity.compare(ContentKind.SERVICES, missing, services("es")));

        Map<String, Object> reordered = services("en");
        Collections.reverse((List<Object>) reordered.get("services"));
        assertEquals(List.of(mismatch("services[0].id"), mismatch("services[1].id")),
                ContentParity.compare(ContentKind.SERVICES, services("es"), reordered));

        Map<String, Object> otherPrice = services("en");
        item(otherPrice, "services", 0).put("price", map("from", 1600, "currency", "EUR"));
        item(otherPrice, "services", 1).put("price", map("from", 10, "currency", "EUR"));
        assertEquals(List.of(mismatch("services[0].price"), mismatch("services[1].price")),
                ContentParity.compare(ContentKind.SERVICES, services("es"), otherPrice));

        Map<String, Object> otherCurrency = services("en");
        item(otherCurrency, "services", 0).put("price", map("from", 1500, "currency", "USD"));
        assertEquals(List.of(mismatch("services[0].price")), ContentParity.compare(ContentKind.SERVICES, services("es"), otherCurrency));

        Map<String, Object> neutralProject = projects("en");
        item(neutralProject, "projects", 0).put("relation", "employer");
        item(neutralProject, "projects", 0).put("disclosure", "anonymous");
        item(neutralProject, "projects", 0).put("client", "Otra S.A.");
        item(neutralProject, "projects", 0).put("startDate", "2021-01");
        item(neutralProject, "projects", 1).put("endDate", "2020-12");
        assertEquals(List.of(mismatch("projects[0].relation"), mismatch("projects[0].disclosure"), mismatch("projects[0].client"),
                        mismatch("projects[0].startDate"), mismatch("projects[1].endDate")),
                ContentParity.compare(ContentKind.PROJECTS, projects("es"), neutralProject));

        Map<String, Object> arch = architecture("en");
        ((Map<String, Object>) ((List<Object>) item(arch, "ports", 0).get("adapters")).get(0)).put("status", "experiment");
        ((List<Object>) item(arch, "ports", 0).get("adapters")).remove(1);
        item(arch, "ports", 1).put("id", "events");
        assertEquals(List.of(mismatch("ports[0].adapters[0].status"), mismatch("ports[0].adapters[1]"), mismatch("ports[1].id")),
                ContentParity.compare(ContentKind.ARCHITECTURE, architecture("es"), arch));
    }

    @Test
    void translatableTextsDoNotCountAsDifferences() { // T42
        Map<String, Object> en = projects("en");
        item(en, "projects", 0).put("title", "Something completely different");
        item(en, "projects", 0).put("summary", "Another summary");
        item(en, "projects", 0).put("role", "Another role");
        item(en, "projects", 0).put("clientLabel", "another label");
        item(en, "projects", 0).put("stack", list("Rust"));
        item(en, "projects", 0).put("highlights", list("uno", "dos", "tres"));
        item(en, "projects", 0).put("links", list());
        assertEquals(List.of(), ContentParity.compare(ContentKind.PROJECTS, projects("es"), en));

        Map<String, Object> arch = architecture("en");
        ((Map<String, Object>) arch.get("core")).put("name", "Other");
        item(arch, "ports", 0).put("name", "Other");
        ((Map<String, Object>) ((List<Object>) item(arch, "ports", 0).get("adapters")).get(0)).put("name", "Other");
        assertEquals(List.of(), ContentParity.compare(ContentKind.ARCHITECTURE, architecture("es"), arch));
    }

    @Test
    void numericallyEqualPricesAndBlankVersusAbsentTextsAreTheSame() {
        Map<String, Object> en = services("en");
        item(en, "services", 0).put("price", map("from", 1500.0, "currency", "EUR"));
        assertEquals(List.of(), ContentParity.compare(ContentKind.SERVICES, services("es"), en));

        Map<String, Object> projects = projects("en");
        item(projects, "projects", 1).put("client", "");
        assertEquals(List.of(), ContentParity.compare(ContentKind.PROJECTS, projects("es"), projects));
    }

    @Test
    void aMissingLanguageIsOneDifferenceAndTwoMissingAreNone() {
        assertEquals(List.of(), ContentParity.compare(ContentKind.SERVICES, null, null));
        assertEquals(List.of(mismatch("en")), ContentParity.compare(ContentKind.SERVICES, services("es"), null));
        assertEquals(List.of(mismatch("es")), ContentParity.compare(ContentKind.SERVICES, null, services("en")));
    }

    @Test
    void shapesThatDoNotFitAreTreatedAsAbsent() {
        Map<String, Object> broken = map("services", "no es una lista");
        assertEquals(List.of(mismatch("services[0]"), mismatch("services[1]")),
                ContentParity.compare(ContentKind.SERVICES, services("es"), broken));
    }

    // ── extra fuera del plan: las imágenes cuentan por key, width y height, no por alt ──

    @Test
    void imagesAreComparedByKeyWidthAndHeightButNotByAlt() {
        String k1 = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp";
        String k2 = "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png";
        Map<String, Object> es = projects("es");
        Map<String, Object> en = projects("en");
        item(es, "projects", 0).put("images", list(map("key", k1, "alt", "Portada", "width", 1280, "height", 720)));
        item(en, "projects", 0).put("images", list(map("key", k1, "alt", "Cover", "width", 1280.0, "height", 720)));
        assertEquals(List.of(), ContentParity.compare(ContentKind.PROJECTS, es, en));

        item(en, "projects", 0).put("images", list(map("key", k2, "alt", "Cover", "width", 1000, "height", 720)));
        assertEquals(List.of(mismatch("projects[0].images[0].key"), mismatch("projects[0].images[0].width")),
                ContentParity.compare(ContentKind.PROJECTS, es, en));

        item(en, "projects", 0).put("images", list());
        assertEquals(List.of(mismatch("projects[0].images[0]")), ContentParity.compare(ContentKind.PROJECTS, es, en));
        assertEquals(List.of(mismatch("projects[0].images[0]")), ContentParity.compare(ContentKind.PROJECTS, en, es));
    }
}
