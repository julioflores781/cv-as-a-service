package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.DeleteImageService;
import dev.julioflores.cv.application.service.ImageInUseException;
import dev.julioflores.cv.application.service.ImageNotFoundException;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeleteImageServiceTest {

    private static final String ID = "9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa";
    private static final String KEY = "site/projects/" + ID + ".webp";

    private final ImageFakes.Storage storage = new ImageFakes.Storage();
    private final ImageFakes.References references = new ImageFakes.References();
    private final DeleteImageService service = new DeleteImageService(storage, references);

    @Test // T56
    void anImageReferencedByAnActiveOrHistoricVersionIsInUseAndTheStorageIsNotTouched() {
        storage.add(KEY, Instant.EPOCH);
        references.keys.add(KEY); // el repositorio cuenta todas las versiones; para el servicio basta con que la referencie alguna
        ImageInUseException error = assertThrows(ImageInUseException.class, () -> service.delete(ID));
        assertEquals(ErrorCode.IMAGE_IN_USE, error.code());
        assertEquals(java.util.List.of(KEY), error.args());
        assertTrue(storage.deleted.isEmpty());
        assertTrue(storage.objects.containsKey(KEY));
    }

    @Test // T57
    void anUnreferencedImageIsDeletedAndAMissingOneIsNotFound() {
        storage.add(KEY, Instant.EPOCH);
        service.delete(ID);
        assertEquals(java.util.List.of(KEY), storage.deleted);

        assertEquals(ErrorCode.IMAGE_NOT_FOUND, assertThrows(ImageNotFoundException.class, () -> service.delete(ID)).code());
    }

    // ── extras fuera del plan ──────────────────────────────────────────────────────

    @Test
    void anIdThatIsNotALowercaseUuidIsNotFoundWithoutAskingTheStorage() {
        for (String bad : new String[] {"../x", "9F1C2D7E-1111-4A2B-8C3D-AAAAAAAAAAAA", "abc", "", null, ID + ".webp", "site/projects/" + ID}) {
            assertThrows(ImageNotFoundException.class, () -> service.delete(bad));
        }
        assertEquals(0, storage.calls);
    }

    @Test
    void aFailingStorageIsUnavailable() {
        storage.add(KEY, Instant.EPOCH);
        storage.failure = new IllegalStateException("boom");
        assertThrows(ImageStorageUnavailableException.class, () -> service.delete(ID));
    }
}
