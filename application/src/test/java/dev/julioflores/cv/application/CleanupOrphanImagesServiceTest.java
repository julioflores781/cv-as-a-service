package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.image.CleanupResult;
import dev.julioflores.cv.application.service.CleanupOrphanImagesService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanupOrphanImagesServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final ImageFakes.Storage storage = new ImageFakes.Storage();
    private final ImageFakes.References references = new ImageFakes.References();
    private final CleanupOrphanImagesService service =
            new CleanupOrphanImagesService(storage, references, Clock.fixed(NOW, ZoneOffset.UTC));

    private static String key(int n) {
        return String.format("site/projects/00000000-0000-4000-8000-%012d.png", n);
    }

    private void age(int n, long hours) {
        storage.add(key(n), NOW.minus(Duration.ofHours(hours)));
    }

    @Test // T58
    void onlyOldOrphansAreDeleted() {
        age(1, 48); // huérfana vieja: se borra
        age(2, 2); // huérfana reciente (gracia 24 h): se queda
        age(3, 100); // referenciada: se queda
        references.keys.add(key(3));

        CleanupResult result = service.cleanup(false, 24);

        assertEquals(List.of(key(1)), result.deleted());
        assertTrue(result.wouldDelete().isEmpty());
        assertEquals(2, result.kept());
        assertEquals(List.of(key(1)), storage.deleted);
        assertEquals(List.of(key(2), key(3)), List.copyOf(storage.objects.keySet()));
    }

    @Test // T59
    void aDryRunReportsWhatItWouldDeleteAndDeletesNothing() {
        age(1, 48);
        age(2, 72);

        CleanupResult result = service.cleanup(true, 24);

        assertTrue(result.dryRun());
        assertTrue(result.deleted().isEmpty());
        assertEquals(List.of(key(1), key(2)), result.wouldDelete());
        assertTrue(storage.deleted.isEmpty());
        assertEquals(2, storage.objects.size());
    }

    @Test // T60
    void deletionsGoInBatchesOfAtMostAThousand() {
        for (int i = 0; i < 2500; i++) {
            age(i, 48);
        }
        CleanupResult result = service.cleanup(false, 24);
        assertEquals(2500, result.deleted().size());
        assertEquals(List.of(1000, 1000, 500), storage.batchSizes);
        assertTrue(storage.objects.isEmpty());
    }

    @Test // T61
    void aGraceBelowTheMinimumIsRaisedToOneHour() {
        age(1, 2); // 2 h: más vieja que el mínimo de 1 h
        age(2, 0); // recién subida
        // Pedir 0 o un número negativo de horas aplica el mínimo (1 h), no «borrar todo»
        CleanupResult result = service.cleanup(true, 0);
        assertEquals(List.of(key(1)), result.wouldDelete());
        assertEquals(1, result.kept());
        assertEquals(List.of(key(1)), service.cleanup(true, -5).wouldDelete());
        // 30 minutos de antigüedad: dentro del mínimo aunque se pida menos
        storage.add(key(3), NOW.minus(Duration.ofMinutes(30)));
        assertEquals(List.of(key(1)), service.cleanup(true, 0).wouldDelete());
    }

    @Test
    void objectsThatAreNotProjectImagesAreNeverTouched() {
        storage.add("site/projects/notes.txt", NOW.minus(Duration.ofDays(30)));
        age(1, 48);
        assertEquals(List.of(key(1)), service.cleanup(false, 24).deleted());
        assertTrue(storage.objects.containsKey("site/projects/notes.txt"));
    }
}
