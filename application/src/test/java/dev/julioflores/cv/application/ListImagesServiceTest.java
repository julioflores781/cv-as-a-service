package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.image.ImageEntry;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import dev.julioflores.cv.application.service.ListImagesService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Extra fuera del plan: el listado (lo que cubre T139 por HTTP, aquí con dobles). */
class ListImagesServiceTest {

    private final ImageFakes.Storage storage = new ImageFakes.Storage();
    private final ImageFakes.References references = new ImageFakes.References();
    private final ListImagesService service = new ListImagesService(storage, references);

    @Test
    void listsNewestFirstWithTheReferencedFlagAndSkipsForeignObjects() {
        String a = "site/projects/00000000-0000-4000-8000-000000000001.png";
        String b = "site/projects/00000000-0000-4000-8000-000000000002.jpg";
        storage.add(a, Instant.parse("2026-11-01T00:00:00Z"));
        storage.add(b, Instant.parse("2026-11-10T00:00:00Z"));
        storage.add("site/projects/readme.txt", Instant.parse("2026-11-11T00:00:00Z"));
        references.keys.add(a);

        List<ImageEntry> entries = service.list();

        assertEquals(List.of(b, a), entries.stream().map(ImageEntry::key).toList());
        assertEquals(List.of(false, true), entries.stream().map(ImageEntry::referenced).toList());
        assertEquals("https://media.example.test/" + b, entries.get(0).url());
    }

    @Test
    void aFailingStorageIsUnavailable() {
        storage.failure = new IllegalStateException("boom");
        assertThrows(ImageStorageUnavailableException.class, service::list);
    }
}
