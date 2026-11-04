package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.application.port.in.TranslateHl7UseCase;
import dev.julioflores.cv.application.port.out.Hl7Translator;
import java.nio.charset.StandardCharsets;

public class TranslateHl7Service implements TranslateHl7UseCase {

    /** Tope del cuerpo en bytes UTF-8. Un mensaje HL7 real de mensajería clínica cabe de sobra. */
    public static final int MAX_BYTES = 64 * 1024;

    private final Hl7Translator translator;

    public TranslateHl7Service(Hl7Translator translator) {
        this.translator = translator;
    }

    @Override
    public Hl7Translation translate(String message, String format) {
        OutputFormat outputFormat = OutputFormat.parse(format);
        if (message == null || message.isBlank()) {
            throw new EmptyHl7BodyException();
        }
        if (tooLarge(message)) {
            throw new Hl7PayloadTooLargeException(MAX_BYTES);
        }
        return translator.translate(message, outputFormat);
    }

    /** Un carácter ocupa entre 1 y 3 bytes en UTF-8: solo se codifica el cuerpo cuando el recuento no basta. */
    private static boolean tooLarge(String message) {
        return message.length() > MAX_BYTES
                || (message.length() * 3 > MAX_BYTES && message.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES);
    }
}
