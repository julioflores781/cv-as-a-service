package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.content.EditableContent;

public interface RollbackContentUseCase {

    /** Reactiva una versión existente de un idioma (no crea una nueva) y devuelve lo activo después. */
    EditableContent rollback(String kind, String language, int baseVersion, int targetVersion);
}
