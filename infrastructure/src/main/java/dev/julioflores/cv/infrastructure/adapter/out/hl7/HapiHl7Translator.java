package dev.julioflores.cv.infrastructure.adapter.out.hl7;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.application.port.out.Hl7Translator;
import dev.julioflores.cv.application.service.InvalidHl7MessageException;
import dev.julioflores.cv.application.service.UnsupportedHl7MessageException;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Traduce mensajes HL7 v2.5.1 (ADT^A01 y ORU^R01) con HAPI. Primero lee la cabecera MSH a mano para
 * distinguir "no es HL7" de "es HL7 pero no lo traduzco"; solo entonces se lo da a HAPI.
 * Nunca se escribe en logs el contenido del mensaje.
 */
/** {@code @Startup}: HAPI tarda en cargar sus modelos; así lo paga el arranque y no la primera petición. */
@Startup
@ApplicationScoped
public class HapiHl7Translator implements Hl7Translator {

    private static final String SUPPORTED_VERSION = "2.5.1";
    private static final Set<String> SUPPORTED_TYPES = Set.of("ADT^A01", "ORU^R01");

    private static final Pattern NOT_SAFE = Pattern.compile("[^A-Za-z0-9^._-]");
    private static final int MAX_ECHO = 20;

    private final HapiContext hapi = new DefaultHapiContext();
    private final PipeParser parser;
    private final FhirContext fhir = FhirContext.forR4Cached();
    private final ObjectMapper json = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);

    public HapiHl7Translator() {
        // Los mensajes de prueba suelen traer fechas o códigos imperfectos: la demo es tolerante.
        hapi.setValidationContext(new NoValidation());
        parser = hapi.getPipeParser();
    }

    @PreDestroy
    public void close() throws IOException {
        hapi.close();
    }

    @Override
    public Hl7Translation translate(String message, OutputFormat format) {
        String normalized = normalizeSeparators(message.strip());
        checkHeader(normalized);
        ClinicalMessage clinical = parse(normalized);
        String body = switch (format) {
            case JSON -> toJson(clinical);
            case FHIR -> fhir.newJsonParser().setPrettyPrint(true)
                    .encodeResourceToString(FhirBundleMapper.toBundle(clinical));
        };
        return new Hl7Translation(format, body);
    }

    private ClinicalMessage parse(String message) {
        try {
            return Hl7Extractor.extract(parser.parse(message));
        } catch (HL7Exception e) {
            throw new InvalidHl7MessageException("HAPI could not parse it", e);
        }
    }

    /** HL7 separa segmentos con CR; los clientes suelen enviar LF o CRLF. */
    private static String normalizeSeparators(String message) {
        return message.indexOf('\n') < 0 ? message : message.replace("\r\n", "\r").replace('\n', '\r');
    }

    private String toJson(ClinicalMessage clinical) {
        try {
            return json.writeValueAsString(clinical);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the translated message", e);
        }
    }

    /** Valida lo mínimo de MSH (campos 9 y 12) y rechaza lo que no sea ADT^A01 / ORU^R01 en 2.5.1. */
    private static void checkHeader(String message) {
        if (!message.startsWith("MSH") || message.length() < 8) {
            throw new InvalidHl7MessageException("it does not start with an MSH segment");
        }
        int end = message.indexOf('\r');
        List<String> fields = split(end < 0 ? message : message.substring(0, end), message.charAt(3));
        if (fields.size() < 12 || fields.get(1).isEmpty()) {
            throw new InvalidHl7MessageException("MSH is missing its message type or version");
        }
        char componentSeparator = fields.get(1).charAt(0);
        List<String> type = split(fields.get(8), componentSeparator);
        String version = split(fields.get(11), componentSeparator).get(0);
        if (type.size() < 2 || type.get(0).isEmpty() || version.isEmpty()) {
            throw new InvalidHl7MessageException("MSH-9 or MSH-12 is empty");
        }
        String messageType = type.get(0) + "^" + type.get(1);
        if (!SUPPORTED_TYPES.contains(messageType) || !SUPPORTED_VERSION.equals(version)) {
            throw new UnsupportedHl7MessageException(echo(messageType) + " (v" + echo(version) + ")");
        }
    }

    /** Como {@code String.split(sep, -1)} pero sin expresiones regulares: conserva los campos vacíos del final. */
    private static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = text.indexOf(separator); i >= 0; i = text.indexOf(separator, start)) {
            parts.add(text.substring(start, i));
            start = i + 1;
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** Lo recibido se repite en la respuesta: se acota y se limpia para no devolver contenido arbitrario. */
    private static String echo(String value) {
        String safe = NOT_SAFE.matcher(value).replaceAll("");
        return safe.length() > MAX_ECHO ? safe.substring(0, MAX_ECHO) : safe;
    }
}
