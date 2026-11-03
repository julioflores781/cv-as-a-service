package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.hl7.Hl7Translation;
import dev.julioflores.cv.application.hl7.OutputFormat;
import dev.julioflores.cv.application.port.in.TranslateHl7UseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/hl7/translate")
@Tag(name = "HL7")
public class Hl7Resource {

    private static final String FHIR_JSON = "application/fhir+json";

    private final TranslateHl7UseCase translateHl7;

    public Hl7Resource(TranslateHl7UseCase translateHl7) {
        this.translateHl7 = translateHl7;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_JSON, FHIR_JSON})
    @Operation(
            operationId = "translateHl7",
            summary = "Traduce un mensaje HL7 v2 a JSON o a un Bundle FHIR R4",
            description = "Demo de interoperabilidad sanitaria. Envía en el cuerpo un mensaje **HL7 v2.5.1** en "
                    + "texto plano (`ADT^A01` u `ORU^R01`; los segmentos pueden ir separados por salto de línea) y "
                    + "recibe el paciente, la visita y las observaciones en el formato pedido.\n\n"
                    + "- `format=json` (por defecto): resumen sencillo del mensaje.\n"
                    + "- `format=fhir`: Bundle FHIR R4 de tipo `collection` con `Patient`, `Encounter` (ADT) y "
                    + "`Observation` (ORU).\n\n"
                    + "**Uso responsable:** envía solo mensajes sintéticos. El cuerpo se procesa en memoria, no se "
                    + "guarda ni se escribe en ningún log. Máximo 64 KB.")
    @RequestBody(
            required = true,
            description = "Mensaje HL7 v2.5.1, `ADT^A01` u `ORU^R01`.",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(type = SchemaType.STRING, maxLength = 65536),
                    examples = {
                        @ExampleObject(name = "admission", summary = "Ingreso (ADT^A01)", value = Hl7Examples.ADT_A01),
                        @ExampleObject(name = "labResult", summary = "Resultado de laboratorio (ORU^R01)", value = Hl7Examples.ORU_R01)
                    }))
    @Parameter(
            name = "format",
            in = ParameterIn.QUERY,
            description = "Formato de salida: `json` (resumen sencillo) o `fhir` (Bundle FHIR R4).",
            schema = @Schema(enumeration = {"json", "fhir"}, defaultValue = "json"),
            example = "fhir")
    @APIResponse(
            responseCode = "200",
            description = "El mensaje traducido. Con `format=fhir` el tipo de contenido es `application/fhir+json`.",
            content = {
                @Content(
                        mediaType = MediaType.APPLICATION_JSON,
                        schema = @Schema(type = SchemaType.OBJECT),
                        examples = {
                            @ExampleObject(name = "admission", summary = "Ingreso (ADT^A01)", value = Hl7Examples.ADT_A01_JSON),
                            @ExampleObject(name = "labResult", summary = "Resultado de laboratorio (ORU^R01)", value = Hl7Examples.ORU_R01_JSON)
                        }),
                @Content(
                        mediaType = FHIR_JSON,
                        schema = @Schema(type = SchemaType.OBJECT),
                        examples = {
                            @ExampleObject(name = "admission", summary = "Ingreso (ADT^A01)", value = Hl7Examples.ADT_A01_FHIR),
                            @ExampleObject(name = "labResult", summary = "Resultado de laboratorio (ORU^R01)", value = Hl7Examples.ORU_R01_FHIR)
                        })
            })
    @APIResponse(
            responseCode = "400",
            description = "Cuerpo vacío (`HL7_EMPTY_BODY`) o `format` desconocido (`HL7_UNSUPPORTED_FORMAT`).",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "emptyBody", summary = "Cuerpo vacío", value = Hl7Examples.PROBLEM_EMPTY_BODY),
                        @ExampleObject(name = "unknownFormat", summary = "Formato desconocido (`?format=xml`)", value = Hl7Examples.PROBLEM_UNSUPPORTED_FORMAT)
                    }))
    @APIResponse(
            responseCode = "413",
            description = "El mensaje supera los 64 KB (`HL7_PAYLOAD_TOO_LARGE`). Por encima de 128 KB el servidor corta la petición "
                    + "antes de leerla y responde `413` sin cuerpo.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "tooLarge", summary = "Más de 64 KB", value = Hl7Examples.PROBLEM_PAYLOAD_TOO_LARGE)
                    }))
    @APIResponse(
            responseCode = "422",
            description = "No es un mensaje HL7 legible (`HL7_INVALID_MESSAGE`) o es de un tipo o versión que la "
                    + "demo no traduce (`HL7_UNSUPPORTED_MESSAGE`).",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "notHl7", summary = "No es un mensaje HL7", value = Hl7Examples.PROBLEM_INVALID_MESSAGE),
                        @ExampleObject(name = "unsupportedType", summary = "Tipo o versión no soportados", value = Hl7Examples.PROBLEM_UNSUPPORTED_MESSAGE)
                    }))
    public Response translate(String message, @QueryParam("format") @DefaultValue("json") String format,
            @BeanParam LanguageParams docsOnly) {
        // `docsOnly` solo publica `lang` y `Accept-Language` en Swagger: el idioma de los errores lo resuelve el mapper.
        Hl7Translation result = translateHl7.translate(message, format);
        String type = result.format() == OutputFormat.FHIR ? FHIR_JSON : MediaType.APPLICATION_JSON;
        return Response.ok(result.body(), type).build();
    }
}
