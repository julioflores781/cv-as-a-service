package dev.julioflores.cv.application.service;

import java.util.function.Supplier;

/** Traduce cualquier fallo inesperado del almacenamiento a {@link ImageStorageUnavailableException}. */
final class Storage {

    private Storage() {}

    static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ImageStorageUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ImageStorageUnavailableException(e.getClass().getSimpleName(), e);
        }
    }

    static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
