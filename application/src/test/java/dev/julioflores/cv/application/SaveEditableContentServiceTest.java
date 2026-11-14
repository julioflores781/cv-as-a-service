package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.map;
import static dev.julioflores.cv.application.ContentDocs.projects;
import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.AdminFakes.RecordingAuditLog;
import dev.julioflores.cv.application.audit.AuditAction;

import dev.julioflores.cv.application.ContentFakes.FakeVersions;
import dev.julioflores.cv.application.ContentFakes.RecordingInvalidator;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.SaveContentCommand;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.ContentInvalidException;
import dev.julioflores.cv.application.service.ContentNotFoundException;
import dev.julioflores.cv.application.service.ContentParityBrokenException;
import dev.julioflores.cv.application.service.ContentVersionConflictException;
import dev.julioflores.cv.application.service.SaveEditableContentService;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T22-T27. */
class SaveEditableContentServiceTest {

    private FakeVersions versions;
    private RecordingInvalidator cache;
    private SaveEditableContentService service;
    private final RecordingAuditLog audit = new RecordingAuditLog();

    @BeforeEach
    void setUp() {
        versions = new FakeVersions()
                .seed(ContentKind.SERVICES, Language.ES, 3, true, services("es"))
                .seed(ContentKind.SERVICES, Language.EN, 3, true, services("en"));
        cache = new RecordingInvalidator();
        cache.watching = versions;
        service = new SaveEditableContentService(versions, cache, audit);
    }

    private static SaveContentCommand command(int baseEs, int baseEn, Map<String, Object> es, Map<String, Object> en) {
        return new SaveContentCommand(baseEs, baseEn, "Cambio de precio", es, en);
    }

    private Map<String, Object> changed(String lang, String title) {
        Map<String, Object> doc = services(lang);
        ((Map<String, Object>) ((List<Object>) doc.get("services")).get(0)).put("title", title);
        return doc;
    }

    @Test
    void aValidBilingualSavePublishesBothAndInvalidatesTheCacheAfterwards() { // T22
        EditableContent result = service.save("services", command(3, 3, changed("es", "Nuevo"), changed("en", "New")));

        assertEquals(4, result.es().version());
        assertEquals(4, result.en().version());
        assertTrue(result.es().active() && result.en().active());
        assertEquals("admin", result.es().source());
        assertEquals("Cambio de precio", result.es().comment());
        assertTrue(result.parityOk());
        assertEquals(List.of("services/es", "services/en"), cache.calls);
        // 2 filas sembradas + 2 publicadas: la invalidación ocurrió con las dos versiones ya escritas.
        assertEquals(List.of(4, 4), cache.rowsAtCall);
        assertEquals(1, versions.rows.stream().filter(r -> r.language == Language.ES && r.active).count());
    }

    @Test
    void aStaleBaseVersionInEitherLanguageIsAConflictThatPublishesAndInvalidatesNothing() { // T23
        var onEs = assertThrows(ContentVersionConflictException.class,
                () -> service.save("services", command(2, 3, services("es"), services("en"))));
        assertEquals(ErrorCode.CONTENT_VERSION_CONFLICT, onEs.code());
        assertEquals(List.of("es", "3"), onEs.args());

        var onEn = assertThrows(ContentVersionConflictException.class,
                () -> service.save("services", command(3, 1, services("es"), services("en"))));
        assertEquals(List.of("en", "3"), onEn.args());

        assertEquals(2, versions.rows.size());
        assertEquals(List.of(), cache.calls);
    }

    @Test
    void invalidOrNonParallelContentIsRejectedBeforeTouchingTheRepository() { // T24
        Map<String, Object> bad = services("es");
        ((Map<String, Object>) ((List<Object>) bad.get("services")).get(0)).put("id", "NO VALE");
        var invalid = assertThrows(ContentInvalidException.class, () -> service.save("services", command(3, 3, bad, services("en"))));
        assertEquals(ErrorCode.CONTENT_INVALID, invalid.code());
        assertEquals(List.of(new Violation("es.services[0].id", ViolationCode.INVALID_ID)), invalid.violations());
        assertEquals(List.of("1"), invalid.args());

        Map<String, Object> missingEnglish = map("services", ContentDocs.list());
        var bothSides = assertThrows(ContentInvalidException.class,
                () -> service.save("services", new SaveContentCommand(3, 3, null, bad, null)));
        assertEquals(List.of(new Violation("es.services[0].id", ViolationCode.INVALID_ID),
                new Violation("en", ViolationCode.REQUIRED)), bothSides.violations());

        Map<String, Object> shorter = services("en");
        ((List<Object>) shorter.get("services")).remove(1);
        var parity = assertThrows(ContentParityBrokenException.class,
                () -> service.save("services", command(3, 3, services("es"), shorter)));
        assertEquals(ErrorCode.CONTENT_PARITY_BROKEN, parity.code());
        assertEquals(List.of(new Violation("services[1]", ViolationCode.PARITY_MISMATCH)), parity.violations());

        assertEquals(0, versions.publishCalls);
        assertEquals(List.of(), cache.calls);
        assertTrue(missingEnglish.get("services") instanceof List<?>);
    }

    @Test
    void aTooLongCommentIsAViolation() {
        var e = assertThrows(ContentInvalidException.class, () -> service.save("services",
                new SaveContentCommand(3, 3, "c".repeat(201), services("es"), services("en"))));
        assertEquals(List.of(new Violation("comment", ViolationCode.TOO_LONG)), e.violations());
        assertEquals(0, versions.publishCalls);
    }

    @Test
    void aBlankCommentIsStoredAsNothingAndOthersAreTrimmed() {
        service.save("services", new SaveContentCommand(3, 3, "   ", services("es"), services("en")));
        assertEquals(null, versions.lastComment);
        service.save("services", new SaveContentCommand(4, 4, "  nota  ", services("es"), services("en")));
        assertEquals("nota", versions.lastComment);
    }

    @Test
    void whenTheRepositoryFailsTheErrorPropagatesAndTheCacheIsNotInvalidated() { // T25
        versions.failOnPublish = new IllegalStateException("db down");

        var e = assertThrows(IllegalStateException.class,
                () -> service.save("services", command(3, 3, services("es"), services("en"))));

        assertEquals("db down", e.getMessage());
        assertEquals(List.of(), cache.calls);
    }

    @Test
    void aFailingInvalidationAfterPublishingDoesNotUndoOrPropagate() { // T26
        cache.failWith = new IllegalStateException("cache boom");

        EditableContent result = service.save("services", command(3, 3, changed("es", "Nuevo"), changed("en", "New")));

        assertEquals(4, result.es().version());
        assertEquals(List.of("services/es", "services/en"), cache.calls); // lo intentó con ambos idiomas
        assertEquals(4, versions.rows.size());
    }

    @Test
    void theFirstSaveOfAKindWithNothingPublishedCreatesVersionOne() { // T27
        EditableContent result = service.save("projects", command(0, 0, projects("es"), projects("en")));

        assertEquals(1, result.es().version());
        assertEquals(1, result.en().version());
        assertEquals(List.of("projects/es", "projects/en"), cache.calls);
        assertFalse(versions.rows.stream().anyMatch(r -> r.kind == ContentKind.PROJECTS && r.version != 1));
    }

    @Test
    void aSaveWithBaseZeroOverAnExistingVersionIsAConflict() {
        var e = assertThrows(ContentVersionConflictException.class,
                () -> service.save("services", command(0, 0, services("es"), services("en"))));
        assertEquals(List.of("es", "3"), e.args());
    }

    @Test
    void anUnknownKindIsNotFoundBeforeAnythingElse() {
        var e = assertThrows(ContentNotFoundException.class,
                () -> service.save("resume", command(1, 1, services("es"), services("en"))));
        assertEquals(ErrorCode.NOT_FOUND, e.code());
    }

    @Test
    void aValidSaveRecordsTheVersionsAndOnlyTheCommentLengthNeverTheTextOrTheContent() { // T38
        service.save("services", command(3, 3, changed("es", "TituloUnicoXyz"), changed("en", "UniqueTitleXyz")));

        assertEquals(RecordingAuditLog.success(AuditAction.CONTENT_SAVED, Map.of("kind", "services", "es_version", "4",
                "en_version", "4", "comment_present", "true", "comment_length", "16")), audit.only());
        String logged = audit.everything();
        assertFalse(logged.contains("Cambio de precio"), "el comentario no se registra");
        assertFalse(logged.contains("TituloUnicoXyz") || logged.contains("UniqueTitleXyz"), "el contenido no se registra");
    }

    @Test
    void invalidParityConflictAndRepositoryFailuresRecordNothing() { // T38 (negativos)
        Map<String, Object> bad = services("es");
        ((Map<String, Object>) ((List<Object>) bad.get("services")).get(0)).put("id", "NO VALE");
        assertThrows(ContentInvalidException.class, () -> service.save("services", command(3, 3, bad, services("en"))));

        Map<String, Object> shorter = services("en");
        ((List<Object>) shorter.get("services")).remove(1);
        assertThrows(ContentParityBrokenException.class, () -> service.save("services", command(3, 3, services("es"), shorter)));

        assertThrows(ContentVersionConflictException.class,
                () -> service.save("services", command(2, 3, services("es"), services("en"))));
        assertThrows(ContentNotFoundException.class,
                () -> service.save("resume", command(1, 1, services("es"), services("en"))));

        versions.failOnPublish = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> service.save("services", command(3, 3, services("es"), services("en"))));

        assertEquals(List.of(), audit.events);
    }

    @Test
    void aSaveWithoutCommentRecordsItAsAbsentWithLengthZeroAndCountsCodePoints() { // extra fuera del plan
        service.save("services", new SaveContentCommand(3, 3, "   ", services("es"), services("en")));
        assertEquals(Map.of("kind", "services", "es_version", "4", "en_version", "4", "comment_present", "false",
                "comment_length", "0"), audit.only().details());
        audit.events.clear();
        service.save("services", new SaveContentCommand(4, 4, "  \uD83D\uDE00ab  ", services("es"), services("en")));
        assertEquals("3", audit.only().details().get("comment_length"), "cuenta puntos de código, no unidades UTF-16");
    }

    @Test
    void aFailingReadAfterPublishingStillRecordsTheSaveWithoutVersionsAndRethrows() { // R1 de la ronda 1
        versions.failActiveAfter = 0;

        var e = assertThrows(IllegalStateException.class,
                () -> service.save("services", command(3, 3, changed("es", "Nuevo"), changed("en", "New"))));

        assertEquals("read failed", e.getMessage());
        assertEquals(4, versions.rows.size(), "el guardado sí se publicó");
        assertEquals(RecordingAuditLog.success(AuditAction.CONTENT_SAVED,
                Map.of("kind", "services", "comment_present", "true", "comment_length", "16")), audit.only());
    }
}
