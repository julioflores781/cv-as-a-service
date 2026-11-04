package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.application.port.out.Hl7Translator;
import dev.julioflores.cv.application.service.EmptyHl7BodyException;
import dev.julioflores.cv.application.service.Hl7PayloadTooLargeException;
import dev.julioflores.cv.application.service.InvalidHl7MessageException;
import dev.julioflores.cv.application.service.TranslateHl7Service;
import dev.julioflores.cv.application.service.UnsupportedHl7FormatException;
import dev.julioflores.cv.application.service.UnsupportedHl7MessageException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslateHl7ServiceTest {

    /** Traductor de mentira: anota lo que recibe y devuelve el mensaje tal cual. */
    private static final class RecordingTranslator implements Hl7Translator {
        final List<OutputFormat> formats = new ArrayList<>();
        final List<String> messages = new ArrayList<>();

        @Override
        public Hl7Translation translate(String message, OutputFormat format) {
            messages.add(message);
            formats.add(format);
            return new Hl7Translation(format, "{\"echo\":true}");
        }
    }

    private final RecordingTranslator translator = new RecordingTranslator();
    private final TranslateHl7Service service = new TranslateHl7Service(translator);

    @Test
    void delegatesToTheTranslatorWithTheRequestedFormat() {
        Hl7Translation result = service.translate("MSH|x", "fhir");
        assertEquals(OutputFormat.FHIR, result.format());
        assertEquals(List.of(OutputFormat.FHIR), translator.formats);
        assertEquals(List.of("MSH|x"), translator.messages);
    }

    @Test
    void formatIsCaseInsensitiveAndDefaultsToJson() {
        service.translate("MSH|x", " FHIR ");
        service.translate("MSH|x", null);
        service.translate("MSH|x", "");
        assertEquals(List.of(OutputFormat.FHIR, OutputFormat.JSON, OutputFormat.JSON), translator.formats);
    }

    @Test
    void blankOrMissingBodyIsRejectedBeforeTranslating() {
        for (String body : new String[] {null, "", "  \r\n\t"}) {
            var e = assertThrows(EmptyHl7BodyException.class, () -> service.translate(body, "json"));
            assertEquals(ErrorCode.HL7_EMPTY_BODY, e.code());
            assertEquals(List.of(), e.args());
        }
        assertEquals(List.of(), translator.messages);
    }

    @Test
    void unknownFormatCarriesItsValueAndIsCheckedFirst() {
        var e = assertThrows(UnsupportedHl7FormatException.class, () -> service.translate("", "xml"));
        assertEquals(ErrorCode.HL7_UNSUPPORTED_FORMAT, e.code());
        assertEquals(List.of("xml"), e.args());
    }

    @Test
    void limitIsMeasuredInUtf8BytesNotCharacters() {
        String atLimit = "a".repeat(TranslateHl7Service.MAX_BYTES);
        assertSame(OutputFormat.JSON, service.translate(atLimit, "json").format());

        // 'é' ocupa 2 bytes: 32.769 caracteres ya son 65.538 bytes.
        String overInBytes = "é".repeat(TranslateHl7Service.MAX_BYTES / 2 + 1);
        var e = assertThrows(Hl7PayloadTooLargeException.class, () -> service.translate(overInBytes, "json"));
        assertEquals(ErrorCode.HL7_PAYLOAD_TOO_LARGE, e.code());
        assertEquals(List.of(64), e.args());
        assertEquals(1, translator.messages.size(), "el mensaje demasiado grande no debe llegar al traductor");
    }

    @Test
    void translationErrorsCarryTheirCodes() {
        var invalid = new InvalidHl7MessageException("no MSH");
        assertEquals(ErrorCode.HL7_INVALID_MESSAGE, invalid.code());
        assertEquals("Invalid HL7 message: no MSH", invalid.getMessage());
        assertEquals(List.of(), invalid.args());

        var cause = new IllegalStateException("boom");
        assertSame(cause, new InvalidHl7MessageException("x", cause).getCause());

        var unsupported = new UnsupportedHl7MessageException("ADT^A08 (v2.5.1)");
        assertEquals(ErrorCode.HL7_UNSUPPORTED_MESSAGE, unsupported.code());
        assertEquals(List.of("ADT^A08 (v2.5.1)"), unsupported.args());
    }
}
