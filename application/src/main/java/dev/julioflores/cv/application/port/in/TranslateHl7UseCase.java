package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;

public interface TranslateHl7UseCase {

    /**
     * Traduce un mensaje HL7 v2 al formato pedido.
     *
     * @param format valor del parámetro {@code format}; {@code null} o vacío significa {@link OutputFormat#JSON}
     */
    Hl7Translation translate(String message, String format);
}
