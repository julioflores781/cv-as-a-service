package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.image.CleanupResult;

public interface CleanupOrphanImagesUseCase {

    /** Borra (o, con {@code dryRun}, solo informa) las imágenes sin referencias y más antiguas que la gracia (mínimo 1 hora). */
    CleanupResult cleanup(boolean dryRun, int olderThanHours);
}
