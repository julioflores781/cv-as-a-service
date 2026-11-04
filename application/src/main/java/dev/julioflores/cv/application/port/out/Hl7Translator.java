package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;

public interface Hl7Translator {

    /**
     * @param message mensaje HL7 v2 con cualquier separador de segmentos (CR, LF o CRLF)
     * @throws dev.julioflores.cv.application.service.InvalidHl7MessageException si no se puede interpretar
     * @throws dev.julioflores.cv.application.service.UnsupportedHl7MessageException si es de un tipo o versión no soportados
     */
    Hl7Translation translate(String message, OutputFormat format);
}
