package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.content.EditableContent;

public interface GetEditableContentUseCase {

    /** Lo activo en ES y EN y su paridad. Sin nada publicado: versión 0 y contenido nulo, no un error. */
    EditableContent get(String kind);
}
