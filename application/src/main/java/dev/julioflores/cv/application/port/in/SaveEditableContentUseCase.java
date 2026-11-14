package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.SaveContentCommand;

public interface SaveEditableContentUseCase {

    /** Valida y publica ES y EN a la vez (todo o nada) y devuelve lo activo después del guardado. */
    EditableContent save(String kind, SaveContentCommand command);
}
