package dev.julioflores.cv.application;

import static dev.julioflores.cv.application.ContentDocs.projects;
import static dev.julioflores.cv.application.ContentDocs.services;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.ContentFakes.FakeVersions;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.ContentNotFoundException;
import dev.julioflores.cv.application.service.GetEditableContentService;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T48 y T49. */
class GetEditableContentServiceTest {

    @Test
    void aKindWithNothingPublishedIsVersionZeroWithNullContentNotAnError() { // T48
        var service = new GetEditableContentService(new FakeVersions());

        EditableContent result = service.get("projects");

        assertEquals(ContentKind.PROJECTS, result.kind());
        assertEquals(0, result.es().version());
        assertNull(result.es().content());
        assertFalse(result.es().active());
        assertEquals(0, result.en().version());
        assertTrue(result.parityOk());
    }

    @Test
    void anUnknownKindIsANotFoundError() { // T49
        var service = new GetEditableContentService(new FakeVersions());

        var e = assertThrows(ContentNotFoundException.class, () -> service.get("resume"));

        assertEquals(ErrorCode.NOT_FOUND, e.code());
        assertThrows(ContentNotFoundException.class, () -> service.get(null));
        assertThrows(ContentNotFoundException.class, () -> service.get("Services"));
    }

    @Test
    void bothLanguagesComeWithTheirVersionAndTheirParity() {
        var versions = new FakeVersions()
                .seed(ContentKind.PROJECTS, Language.ES, 2, true, projects("es"))
                .seed(ContentKind.PROJECTS, Language.EN, 7, true, projects("en"));

        EditableContent result = new GetEditableContentService(versions).get("projects");

        assertEquals(2, result.es().version());
        assertEquals(7, result.en().version());
        assertTrue(result.parityOk());
    }

    @Test
    void aBrokenParityIsReportedNotRejected() {
        var versions = new FakeVersions()
                .seed(ContentKind.SERVICES, Language.ES, 1, true, services("es"))
                .seed(ContentKind.SERVICES, Language.EN, 1, true, ContentDocs.map("services", ContentDocs.list()));

        EditableContent result = new GetEditableContentService(versions).get("services");

        assertFalse(result.parityOk());
        assertEquals(List.of(new Violation("services[0]", ViolationCode.PARITY_MISMATCH),
                new Violation("services[1]", ViolationCode.PARITY_MISMATCH)), result.parity());
    }
}
