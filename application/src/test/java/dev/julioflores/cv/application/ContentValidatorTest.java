package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.architecture;
import static dev.julioflores.cv.application.ContentDocs.list;
import static dev.julioflores.cv.application.ContentDocs.map;
import static dev.julioflores.cv.application.ContentDocs.projects;
import static dev.julioflores.cv.application.ContentDocs.service;
import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentValidator;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T28-T39 (T37 y T38: las reglas de imágenes). */
class ContentValidatorTest {

    private static List<Violation> validate(ContentKind kind, Object document) {
        return ContentValidator.validate(kind, "es", document);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> document, String list, int index) {
        return (Map<String, Object>) ((List<Object>) document.get(list)).get(index);
    }

    private static Violation v(String path, ViolationCode code) {
        return new Violation(path, code);
    }

    @Test
    void theSampleDocumentsOfEveryKindAreValid() {
        assertEquals(List.of(), validate(ContentKind.SERVICES, services("es")));
        assertEquals(List.of(), validate(ContentKind.PROJECTS, projects("es")));
        assertEquals(List.of(), validate(ContentKind.ARCHITECTURE, architecture("es")));
    }

    @Test
    void anAnonymousProjectWithAClientIsRejectedWithItsPath() { // T28
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 1).put("client", "Nombre Real S.A.");

        assertEquals(List.of(v("es.projects[1].client", ViolationCode.ANONYMOUS_CLIENT_FORBIDDEN)),
                validate(ContentKind.PROJECTS, doc));
    }

    @Test
    void anAnonymousProjectWithABlankOrNullClientIsNotAnError() { // coherente con make-seed.py: solo cuenta un cliente con valor
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 1).put("client", "  ");
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));
        item(doc, "projects", 1).put("client", null);
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));
    }

    @Test
    void aPublicEmployerProjectWithoutClientIsRejected() { // T29
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 0).put("relation", "employer");
        item(doc, "projects", 0).remove("client");

        assertEquals(List.of(v("es.projects[0].client", ViolationCode.CLIENT_REQUIRED)), validate(ContentKind.PROJECTS, doc));
    }

    @Test
    void repeatedMalformedOrEmptyIdsAreRejected() { // T30
        Map<String, Object> doc = services("es");
        item(doc, "services", 1).put("id", "servicio-ejemplo");
        assertEquals(List.of(v("es.services[1].id", ViolationCode.DUPLICATE_ID)), validate(ContentKind.SERVICES, doc));

        for (String bad : List.of("Servicio", "", "con espacio", "-guion", "doble--guion", "ñandú", "a".repeat(65))) {
            Map<String, Object> other = services("es");
            item(other, "services", 0).put("id", bad);
            assertEquals(List.of(v("es.services[0].id", ViolationCode.INVALID_ID)), validate(ContentKind.SERVICES, other), bad);
        }
        Map<String, Object> missing = services("es");
        item(missing, "services", 0).remove("id");
        assertEquals(List.of(v("es.services[0].id", ViolationCode.REQUIRED)), validate(ContentKind.SERVICES, missing));
    }

    @Test
    void duplicateIdsAreCheckedInPortsAndWithinEachPort() {
        Map<String, Object> doc = architecture("es");
        item(doc, "ports", 1).put("id", "database");
        assertEquals(List.of(v("es.ports[1].id", ViolationCode.DUPLICATE_ID)), validate(ContentKind.ARCHITECTURE, doc));

        Map<String, Object> adapters = architecture("es");
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) ((List<Object>) item(adapters, "ports", 0).get("adapters")).get(1);
        second.put("id", "postgresql");
        assertEquals(List.of(v("es.ports[0].adapters[1].id", ViolationCode.DUPLICATE_ID)),
                validate(ContentKind.ARCHITECTURE, adapters));
    }

    @Test
    void onlyHttpsLinksWithAHostAreAccepted() { // T31
        for (String url : List.of("javascript:alert(1)", "http://example.com/p", "example.com/p", "//example.com", "https://",
                "https://exa mple.com", "ftp://example.com", "https://example.com/" + "a".repeat(300))) {
            Map<String, Object> doc = projects("es");
            @SuppressWarnings("unchecked")
            Map<String, Object> link = (Map<String, Object>) ((List<Object>) item(doc, "projects", 0).get("links")).get(0);
            link.put("url", url);
            assertEquals(List.of(v("es.projects[0].links[0].url", ViolationCode.INVALID_URL)), validate(ContentKind.PROJECTS, doc), url);
        }
        Map<String, Object> missing = projects("es");
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) ((List<Object>) item(missing, "projects", 0).get("links")).get(0);
        link.remove("url");
        assertEquals(List.of(v("es.projects[0].links[0].url", ViolationCode.REQUIRED)), validate(ContentKind.PROJECTS, missing));
    }

    @Test
    void invalidDatesAreRejected() { // T32
        for (String date : List.of("2022-13", "2022-00", "enero 2022", "2022", "2022-1", "1850-01")) {
            Map<String, Object> doc = projects("es");
            item(doc, "projects", 0).put("startDate", date);
            assertEquals(List.of(v("es.projects[0].startDate", ViolationCode.INVALID_DATE)), validate(ContentKind.PROJECTS, doc), date);
        }
        Map<String, Object> notText = projects("es");
        item(notText, "projects", 0).put("startDate", 2022);
        assertEquals(List.of(v("es.projects[0].startDate", ViolationCode.INVALID_DATE)), validate(ContentKind.PROJECTS, notText));

        Map<String, Object> endBeforeStart = projects("es");
        item(endBeforeStart, "projects", 1).put("endDate", "2019-02");
        assertEquals(List.of(v("es.projects[1].endDate", ViolationCode.INVALID_DATE)), validate(ContentKind.PROJECTS, endBeforeStart));

        Map<String, Object> endWithoutStart = projects("es");
        item(endWithoutStart, "projects", 1).remove("startDate");
        assertEquals(List.of(v("es.projects[1].endDate", ViolationCode.INVALID_DATE)), validate(ContentKind.PROJECTS, endWithoutStart));

        Map<String, Object> sameMonth = projects("es");
        item(sameMonth, "projects", 1).put("endDate", "2019-03");
        assertEquals(List.of(), validate(ContentKind.PROJECTS, sameMonth));
    }

    @Test
    void aNegativeMissingOrNonNumericPriceIsRejected() { // T33
        List<Object> prices = list(
                map("from", -1, "currency", "EUR"),
                map("from", "mil", "currency", "EUR"),
                map("currency", "EUR"),
                map("from", 100),
                map("from", 100, "currency", "euro"),
                map("from", 100, "currency", ""),
                map("from", Double.NaN, "currency", "EUR"),
                map("from", 2_000_000_000, "currency", "EUR"));
        for (Object price : prices) {
            Map<String, Object> doc = services("es");
            item(doc, "services", 0).put("price", price);
            List<Violation> result = validate(ContentKind.SERVICES, doc);
            assertEquals(1, result.size(), String.valueOf(price));
            assertEquals(ViolationCode.INVALID_VALUE, result.get(0).code(), String.valueOf(price));
            assertTrue(result.get(0).path().startsWith("es.services[0].price."), result.get(0).path());
        }
        Map<String, Object> notAnObject = services("es");
        item(notAnObject, "services", 0).put("price", "gratis");
        assertEquals(List.of(v("es.services[0].price", ViolationCode.INVALID_VALUE)), validate(ContentKind.SERVICES, notAnObject));

        Map<String, Object> zeroAndDecimal = services("es");
        item(zeroAndDecimal, "services", 0).put("price", map("from", 0, "currency", "EUR"));
        item(zeroAndDecimal, "services", 1).put("price", map("from", 99.5, "currency", "USD"));
        assertEquals(List.of(), validate(ContentKind.SERVICES, zeroAndDecimal));
    }

    @Test
    void anAdapterStatusOutsideTheClosedSetIsRejected() { // T34
        for (String status : List.of("beta", "PRODUCTION", "")) {
            Map<String, Object> doc = architecture("es");
            @SuppressWarnings("unchecked")
            Map<String, Object> adapter = (Map<String, Object>) ((List<Object>) item(doc, "ports", 0).get("adapters")).get(0);
            adapter.put("status", status);
            ViolationCode expected = status.isEmpty() ? ViolationCode.REQUIRED : ViolationCode.INVALID_VALUE;
            assertEquals(List.of(v("es.ports[0].adapters[0].status", expected)), validate(ContentKind.ARCHITECTURE, doc), status);
        }
        Map<String, Object> badRelation = projects("es");
        item(badRelation, "projects", 0).put("relation", "freelance");
        item(badRelation, "projects", 0).put("disclosure", "secret");
        assertEquals(List.of(v("es.projects[0].relation", ViolationCode.INVALID_VALUE),
                v("es.projects[0].disclosure", ViolationCode.INVALID_VALUE)), validate(ContentKind.PROJECTS, badRelation));
    }

    @Test
    void emptyListsAreValid() { // T35
        assertEquals(List.of(), validate(ContentKind.SERVICES, map("services", list())));
        assertEquals(List.of(), validate(ContentKind.SERVICES, map()));
        assertEquals(List.of(), validate(ContentKind.PROJECTS, map("projects", list())));
        assertEquals(List.of(), validate(ContentKind.ARCHITECTURE, map("core", map("name", "Dominio"), "ports", list())));
        Map<String, Object> noAdapters = architecture("es");
        item(noAdapters, "ports", 1).put("adapters", list());
        assertEquals(List.of(), validate(ContentKind.ARCHITECTURE, noAdapters));
    }

    @Test
    void textsAndListsAboveTheirLimitsAreRejected() { // T36
        Map<String, Object> longTitle = services("es");
        item(longTitle, "services", 0).put("title", "x".repeat(121));
        assertEquals(List.of(v("es.services[0].title", ViolationCode.TOO_LONG)), validate(ContentKind.SERVICES, longTitle));

        Map<String, Object> exactlyAtTheLimit = services("es");
        item(exactlyAtTheLimit, "services", 0).put("title", "x".repeat(120));
        assertEquals(List.of(), validate(ContentKind.SERVICES, exactlyAtTheLimit));

        Map<String, Object> astral = services("es");
        // 120 caracteres fuera del plano básico: 240 unidades UTF-16, pero 120 code points.
        item(astral, "services", 0).put("title", "😀".repeat(120));
        assertEquals(List.of(), validate(ContentKind.SERVICES, astral));

        Map<String, Object> tooMany = map("services", list());
        for (int i = 0; i <= ContentValidator.MAX_SERVICES; i++) {
            ((List<Object>) tooMany.get("services")).add(service("servicio-" + i, "S", null));
        }
        assertEquals(List.of(v("es.services", ViolationCode.TOO_MANY)), validate(ContentKind.SERVICES, tooMany));

        Map<String, Object> project = projects("es");
        item(project, "projects", 0).put("stack", java.util.Collections.nCopies(31, "Java"));
        item(project, "projects", 0).put("highlights", list("h".repeat(401)));
        assertEquals(List.of(v("es.projects[0].stack", ViolationCode.TOO_MANY),
                v("es.projects[0].highlights[0]", ViolationCode.TOO_LONG)), validate(ContentKind.PROJECTS, project));

        Map<String, Object> ports = architecture("es");
        item(ports, "ports", 0).put("adapters", java.util.stream.IntStream.range(0, ContentValidator.MAX_ADAPTERS + 1)
                .mapToObj(i -> (Object) ContentDocs.adapter("a" + i, "production")).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of(v("es.ports[0].adapters", ViolationCode.TOO_MANY)), validate(ContentKind.ARCHITECTURE, ports));
    }

    @Test
    void absentOptionalFieldsAreValid() { // T39
        Map<String, Object> doc = projects("es");
        Map<String, Object> bare = map("id", "solo-lo-justo", "title", "T", "summary", "S", "relation", "own", "disclosure", "anonymous");
        ((List<Object>) doc.get("projects")).add(bare);
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));

        Map<String, Object> noPrice = map("services", list(map("id", "a", "title", "T", "summary", "S")));
        assertEquals(List.of(), validate(ContentKind.SERVICES, noPrice));
    }

    private static final String KEY = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp";

    private static Map<String, Object> image(Object key) {
        return map("key", key, "alt", "Panel de reservas", "width", 1280, "height", 720);
    }

    @Test
    void validImagesOfAnOwnPublicProjectAreAccepted() {
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 0).put("images", list(image(KEY),
                map("key", "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png", "alt", "Detalle", "width", 800.0, "height", 600)));
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));
        item(doc, "projects", 0).put("images", list());
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));
        item(doc, "projects", 0).put("images", null);
        assertEquals(List.of(), validate(ContentKind.PROJECTS, doc));
    }

    @Test
    void employerOrAnonymousProjectsCannotCarryImages() { // T37
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 1).put("images", list(image(KEY))); // anónimo (y de empleador)
        assertEquals(List.of(v("es.projects[1].images", ViolationCode.IMAGES_NOT_ALLOWED_FOR_PROJECT)),
                validate(ContentKind.PROJECTS, doc));

        Map<String, Object> employerPublic = item(doc, "projects", 0);
        employerPublic.put("relation", "employer");
        employerPublic.put("images", list(image(KEY)));
        item(doc, "projects", 1).remove("images");
        assertEquals(List.of(v("es.projects[0].images", ViolationCode.IMAGES_NOT_ALLOWED_FOR_PROJECT)),
                validate(ContentKind.PROJECTS, doc));

        Map<String, Object> ownAnonymous = projects("es");
        item(ownAnonymous, "projects", 0).put("disclosure", "anonymous");
        item(ownAnonymous, "projects", 0).remove("client");
        item(ownAnonymous, "projects", 0).put("images", list(image(KEY)));
        assertEquals(List.of(v("es.projects[0].images", ViolationCode.IMAGES_NOT_ALLOWED_FOR_PROJECT)),
                validate(ContentKind.PROJECTS, ownAnonymous));
    }

    @Test
    void anImageKeyWithDotsOutsideThePrefixWrongExtensionOrNotAUuidIsRejected() { // T38
        for (String bad : List.of("site/projects/../x.png", "other/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png",
                "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.svg", "site/projects/no-es-un-uuid.png",
                "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png/../..", "https://x.test/a.png", "")) {
            Map<String, Object> doc = projects("es");
            item(doc, "projects", 0).put("images", list(image(bad)));
            assertEquals(List.of(v("es.projects[0].images[0].key", ViolationCode.IMAGE_KEY_INVALID)),
                    validate(ContentKind.PROJECTS, doc), bad);
        }
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 0).put("images", list(image(42)));
        assertEquals(List.of(v("es.projects[0].images[0].key", ViolationCode.IMAGE_KEY_INVALID)), validate(ContentKind.PROJECTS, doc));
    }

    @Test
    void imageFieldsAreCheckedForShapeSizeAndQuantity() {
        Map<String, Object> doc = projects("es");
        item(doc, "projects", 0).put("images", list(
                map("key", KEY, "width", 0, "height", 1.5, "caption", "x"),
                map("key", KEY, "alt", "a".repeat(201), "width", "1280", "height", 30000),
                "no es un objeto", map("alt", "sin clave", "width", 1, "height", 1)));
        assertEquals(List.of(
                v("es.projects[0].images[0].caption", ViolationCode.UNKNOWN_FIELD),
                v("es.projects[0].images[0].alt", ViolationCode.REQUIRED),
                v("es.projects[0].images[0].width", ViolationCode.INVALID_VALUE),
                v("es.projects[0].images[0].height", ViolationCode.INVALID_VALUE),
                v("es.projects[0].images[1].alt", ViolationCode.TOO_LONG),
                v("es.projects[0].images[1].width", ViolationCode.INVALID_VALUE),
                v("es.projects[0].images[1].height", ViolationCode.INVALID_VALUE),
                v("es.projects[0].images[2]", ViolationCode.INVALID_VALUE),
                v("es.projects[0].images[3].key", ViolationCode.REQUIRED)), validate(ContentKind.PROJECTS, doc));

        Map<String, Object> missing = projects("es");
        item(missing, "projects", 0).put("images", list(map("key", KEY, "alt", "x")));
        assertEquals(List.of(v("es.projects[0].images[0].width", ViolationCode.REQUIRED),
                v("es.projects[0].images[0].height", ViolationCode.REQUIRED)), validate(ContentKind.PROJECTS, missing));

        Map<String, Object> many = projects("es");
        item(many, "projects", 0).put("images", java.util.Collections.nCopies(11, image(KEY)));
        assertEquals(List.of(v("es.projects[0].images", ViolationCode.TOO_MANY)), validate(ContentKind.PROJECTS, many));

        Map<String, Object> notAList = projects("es");
        item(notAList, "projects", 0).put("images", "x");
        assertEquals(List.of(v("es.projects[0].images", ViolationCode.INVALID_VALUE)), validate(ContentKind.PROJECTS, notAList));
    }

    @Test
    void unknownFieldsAreRejectedAtEveryLevel() {
        Map<String, Object> doc = projects("es");
        doc.put("extra", 1);
        item(doc, "projects", 0).put("gallery", list());
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) ((List<Object>) item(doc, "projects", 0).get("links")).get(0);
        link.put("target", "_blank");
        assertEquals(List.of(v("es.extra", ViolationCode.UNKNOWN_FIELD), v("es.projects[0].gallery", ViolationCode.UNKNOWN_FIELD),
                v("es.projects[0].links[0].target", ViolationCode.UNKNOWN_FIELD)), validate(ContentKind.PROJECTS, doc));

        Map<String, Object> price = services("es");
        ((Map<String, Object>) item(price, "services", 0).get("price")).put("discount", 5);
        assertEquals(List.of(v("es.services[0].price.discount", ViolationCode.UNKNOWN_FIELD)), validate(ContentKind.SERVICES, price));

        Map<String, Object> arch = architecture("es");
        ((Map<String, Object>) arch.get("core")).put("logo", "x");
        item(arch, "ports", 0).put("color", "red");
        assertEquals(List.of(v("es.core.logo", ViolationCode.UNKNOWN_FIELD), v("es.ports[0].color", ViolationCode.UNKNOWN_FIELD)),
                validate(ContentKind.ARCHITECTURE, arch));
    }

    @Test
    void wrongShapesAreReportedInsteadOfThrowing() {
        assertEquals(List.of(v("es", ViolationCode.REQUIRED)), validate(ContentKind.SERVICES, null));
        assertEquals(List.of(v("es", ViolationCode.INVALID_VALUE)), validate(ContentKind.SERVICES, "texto"));
        assertEquals(List.of(v("es.services", ViolationCode.INVALID_VALUE)), validate(ContentKind.SERVICES, map("services", "no")));
        assertEquals(List.of(v("es.services[0]", ViolationCode.INVALID_VALUE)),
                validate(ContentKind.SERVICES, map("services", list("no"))));
        assertEquals(List.of(v("es.core", ViolationCode.REQUIRED)), validate(ContentKind.ARCHITECTURE, map()));
        assertEquals(List.of(v("es.core", ViolationCode.INVALID_VALUE)), validate(ContentKind.ARCHITECTURE, map("core", "x")));

        Map<String, Object> doc = services("es");
        item(doc, "services", 0).put("title", 5);
        item(doc, "services", 1).put("summary", "   ");
        assertEquals(List.of(v("es.services[0].title", ViolationCode.INVALID_VALUE), v("es.services[1].summary", ViolationCode.REQUIRED)),
                validate(ContentKind.SERVICES, doc));

        Map<String, Object> badStack = projects("es");
        item(badStack, "projects", 0).put("stack", list("Java", 7, " "));
        assertEquals(List.of(v("es.projects[0].stack[1]", ViolationCode.INVALID_VALUE),
                v("es.projects[0].stack[2]", ViolationCode.INVALID_VALUE)), validate(ContentKind.PROJECTS, badStack));
    }

    @Test
    void allViolationsAreReportedTogetherUnderTheGivenRoot() {
        Map<String, Object> doc = projects("en");
        item(doc, "projects", 1).put("client", "Real Inc.");
        item(doc, "projects", 0).put("title", "");

        List<Violation> result = ContentValidator.validate(ContentKind.PROJECTS, "en", doc);

        assertEquals(List.of(v("en.projects[0].title", ViolationCode.REQUIRED),
                v("en.projects[1].client", ViolationCode.ANONYMOUS_CLIENT_FORBIDDEN)), result);
    }
}
