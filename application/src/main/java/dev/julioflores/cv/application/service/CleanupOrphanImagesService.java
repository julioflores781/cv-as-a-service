package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.image.CleanupResult;
import dev.julioflores.cv.application.image.StoredObject;
import dev.julioflores.cv.application.port.in.CleanupOrphanImagesUseCase;
import dev.julioflores.cv.application.port.out.FileStoragePort;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import dev.julioflores.cv.domain.ImageKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CleanupOrphanImagesService implements CleanupOrphanImagesUseCase {

    /** La gracia mínima: una imagen recién subida aún no está guardada en ningún proyecto. */
    public static final int MIN_GRACE_HOURS = 1;

    private final FileStoragePort storage;
    private final ImageReferenceRepository references;
    private final Clock clock;

    public CleanupOrphanImagesService(FileStoragePort storage, ImageReferenceRepository references, Clock clock) {
        this.storage = storage;
        this.references = references;
        this.clock = clock;
    }

    @Override
    public CleanupResult cleanup(boolean dryRun, int olderThanHours) {
        Instant limit = clock.instant().minus(Duration.ofHours(Math.max(MIN_GRACE_HOURS, olderThanHours)));
        List<StoredObject> objects = Storage.call(() -> storage.list(ImageKey.PREFIX));
        Set<String> referenced = references.referencedKeys();
        List<String> orphans = new ArrayList<>();
        int kept = 0;
        for (StoredObject object : objects) {
            if (!ImageKey.isValid(object.key())) {
                continue; // lo que no es una imagen de proyecto de esta API no se toca
            }
            if (referenced.contains(object.key()) || object.lastModified().isAfter(limit)) {
                kept++;
            } else {
                orphans.add(object.key());
            }
        }
        if (dryRun) {
            return new CleanupResult(true, List.of(), List.copyOf(orphans), kept);
        }
        for (int from = 0; from < orphans.size(); from += FileStoragePort.MAX_DELETE_BATCH) {
            List<String> batch = orphans.subList(from, Math.min(orphans.size(), from + FileStoragePort.MAX_DELETE_BATCH));
            Storage.run(() -> storage.deleteAll(List.copyOf(batch)));
        }
        return new CleanupResult(false, List.copyOf(orphans), List.of(), kept);
    }
}
