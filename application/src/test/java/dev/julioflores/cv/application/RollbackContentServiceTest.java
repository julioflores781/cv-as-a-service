package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.projects;
import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.ContentFakes.FakeVersions;
import dev.julioflores.cv.application.ContentFakes.RecordingInvalidator;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.ContentInvalidException;
import dev.julioflores.cv.application.service.ContentNotFoundException;
import dev.julioflores.cv.application.service.ContentVersionConflictException;
import dev.julioflores.cv.application.service.ContentVersionNotFoundException;
import dev.julioflores.cv.application.service.RollbackContentService;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T43-T46. Historial ES: 1, 2, 3 y 5 (la 5 activa; la 4 se borró), EN: 1 activa. */
class RollbackContentServiceTest {

    private FakeVersions versions;
    private RecordingInvalidator cache;
    private RollbackContentService service;

    @BeforeEach
    void setUp() {
        versions = new FakeVersions();
        for (int v : new int[] {1, 2, 3}) {
            versions.seed(ContentKind.SERVICES, Language.ES, v, false, services("es"));
        }
        versions.seed(ContentKind.SERVICES, Language.ES, 5, true, services("es"));
        versions.seed(ContentKind.SERVICES, Language.EN, 1, true, services("en"));
        cache = new RecordingInvalidator();
        service = new RollbackContentService(versions, cache);
    }

    @Test
    void rollingBackReactivatesTheTargetInvalidatesTheCacheAndReturnsTheParity() { // T43
        EditableContent result = service.rollback("services", "es", 5, 2);

        assertEquals(2, result.es().version());
        assertTrue(result.es().active());
        assertFalse(versions.find(ContentKind.SERVICES, Language.ES, 5).orElseThrow().active());
        assertEquals(4, versions.rows.stream().filter(r -> r.language == Language.ES).count()); // no crea filas
        assertEquals(List.of("services/es"), cache.calls);
        assertEquals(1, result.en().version());
        assertTrue(result.parityOk());
    }

    @Test
    void aSaveAfterARollbackWouldTakeTheMaximumPlusOneBecauseNothingIsDeleted() {
        service.rollback("services", "es", 5, 1);

        assertEquals(5, versions.rows.stream().filter(r -> r.language == Language.ES).mapToInt(r -> r.version).max().orElseThrow());
        assertEquals(1, versions.active(ContentKind.SERVICES, Language.ES).orElseThrow().version());
    }

    @Test
    void aBaseThatIsNotTheActiveVersionIsAConflict() { // T44
        var e = assertThrows(ContentVersionConflictException.class, () -> service.rollback("services", "es", 3, 2));

        assertEquals(ErrorCode.CONTENT_VERSION_CONFLICT, e.code());
        assertEquals(List.of("es", "5"), e.args());
        assertEquals(0, versions.activateCalls);
        assertEquals(List.of(), cache.calls);
    }

    @Test
    void aMissingTargetVersionIsNotFound() { // T45
        var e = assertThrows(ContentVersionNotFoundException.class, () -> service.rollback("services", "es", 5, 4));

        assertEquals(ErrorCode.CONTENT_VERSION_NOT_FOUND, e.code());
        assertEquals(List.of("es", "4"), e.args());
        assertEquals(0, versions.activateCalls);
    }

    @Test
    void aTargetThatNoLongerPassesTodaysValidationIsNotReactivated() { // T46
        Map<String, Object> old = services("es");
        ((Map<String, Object>) ((List<Object>) old.get("services")).get(0)).put("price", Map.of("from", -5, "currency", "EUR"));
        versions.seed(ContentKind.SERVICES, Language.ES, 6, false, old);

        var e = assertThrows(ContentInvalidException.class, () -> service.rollback("services", "es", 5, 6));

        assertEquals(List.of(new Violation("es.services[0].price.from", ViolationCode.INVALID_VALUE)), e.violations());
        assertEquals(5, versions.active(ContentKind.SERVICES, Language.ES).orElseThrow().version());
        assertEquals(0, versions.activateCalls);
        assertEquals(List.of(), cache.calls);
    }

    @Test
    void withNothingPublishedOnlyBaseZeroMatchesAndThenTheTargetDoesNotExist() {
        assertThrows(ContentVersionConflictException.class, () -> service.rollback("projects", "es", 1, 1));
        assertThrows(ContentVersionNotFoundException.class, () -> service.rollback("projects", "es", 0, 1));
    }

    @Test
    void aFailingInvalidationDoesNotUndoTheRollback() {
        cache.failWith = new IllegalStateException("cache boom");

        EditableContent result = service.rollback("services", "es", 5, 3);

        assertEquals(3, result.es().version());
    }

    @Test
    void anUnknownKindOrLanguageIsNotFound() {
        assertThrows(ContentNotFoundException.class, () -> service.rollback("resume", "es", 5, 2));
        assertThrows(ContentNotFoundException.class, () -> service.rollback("services", "fr", 5, 2));
        assertThrows(ContentNotFoundException.class, () -> service.rollback("services", "ES", 5, 2));
        assertTrue(projects("es").containsKey("projects"));
    }
}
