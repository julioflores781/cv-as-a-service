package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.ContentFakes.FakeVersions;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.ContentNotFoundException;
import dev.julioflores.cv.application.service.ContentVersionNotFoundException;
import dev.julioflores.cv.application.service.GetContentVersionService;
import dev.julioflores.cv.application.service.ListContentVersionsService;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T47 (historial) y la lectura de una instantánea. */
class ListContentVersionsServiceTest {

    private FakeVersions versions;

    @BeforeEach
    void setUp() {
        versions = new FakeVersions();
        for (int v = 1; v <= 5; v++) {
            versions.seed(ContentKind.SERVICES, Language.ES, v, v == 4, services("es"));
        }
    }

    @Test
    void historyComesNewestFirstWithTheRightActiveFlagAndTheLimitIsBoundedAndPassedDown() { // T47
        var service = new ListContentVersionsService(versions);

        List<ContentVersionSummary> all = service.list("services", "es", 50);
        assertEquals(List.of(5, 4, 3, 2, 1), all.stream().map(ContentVersionSummary::version).toList());
        assertEquals(List.of(false, true, false, false, false), all.stream().map(ContentVersionSummary::active).toList());
        assertEquals("seed", all.get(0).source());

        assertEquals(List.of(5, 4), service.list("services", "es", 2).stream().map(ContentVersionSummary::version).toList());
        assertEquals(1, service.list("services", "es", 0).size()); // el límite se acota a 1..200
        assertEquals(1, service.list("services", "es", -3).size());
        assertEquals(5, service.list("services", "es", 100_000).size());
        assertEquals(List.of(), service.list("services", "en", 50));
    }

    @Test
    void anUnknownKindOrLanguageIsNotFound() {
        var service = new ListContentVersionsService(versions);
        assertThrows(ContentNotFoundException.class, () -> service.list("nope", "es", 10));
        assertThrows(ContentNotFoundException.class, () -> service.list("services", "xx", 10));
    }

    @Test
    void aSnapshotCarriesItsContentAndActiveFlagAndAMissingOneIsNotFound() {
        var service = new GetContentVersionService(versions);

        ContentVersion four = service.get("services", "es", 4);
        assertEquals(4, four.version());
        assertEquals(true, four.active());
        assertEquals(services("es"), four.content());

        var e = assertThrows(ContentVersionNotFoundException.class, () -> service.get("services", "es", 9));
        assertEquals(ErrorCode.CONTENT_VERSION_NOT_FOUND, e.code());
        assertThrows(ContentNotFoundException.class, () -> service.get("nope", "es", 1));
    }
}
