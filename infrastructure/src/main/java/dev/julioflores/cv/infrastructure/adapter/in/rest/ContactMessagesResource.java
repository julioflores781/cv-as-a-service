package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.contact.ContactSubmission;
import dev.julioflores.cv.application.port.in.SubmitContactMessageUseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/contact/messages")
@Tag(name = "Contact")
public class ContactMessagesResource {

    private static final String CLOUDFLARE_IP_HEADER = "CF-Connecting-IP";

    private static final String PROBLEM_INVALID = """
            {
              "type": "about:blank",
              "title": "Mensaje no válido",
              "status": 400,
              "detail": "El campo «email» no es válido.",
              "code": "CONTACT_INVALID"
            }""";

    private static final String PROBLEM_RATE_LIMITED = """
            {
              "type": "about:blank",
              "title": "Demasiados mensajes",
              "status": 429,
              "detail": "Has enviado demasiados mensajes seguidos. Inténtalo de nuevo más tarde.",
              "code": "CONTACT_RATE_LIMITED"
            }""";

    /** Cuerpo del formulario. {@code website} es el campo trampa: la web lo oculta y debe enviarse vacío. */
    @Schema(name = "ContactMessageRequest")
    public record ContactMessageRequest(
            @Schema(description = "Nombre de quien escribe.", required = true, maxLength = 100, example = "Ada Ejemplo")
                    String name,
            @Schema(description = "Correo para responder.", required = true, maxLength = 254, example = "ada@example.com")
                    String email,
            @Schema(description = "El mensaje, entre 10 y 2000 caracteres.", required = true, minLength = 10, maxLength = 2000,
                    example = "Hola, me gustaría hablar contigo sobre una vacante de backend.")
                    String message,
            @Schema(description = "Campo trampa anti-spam: déjalo vacío. Si trae algo, el envío se descarta sin avisar.",
                    example = "")
                    String website) {}

    private final SubmitContactMessageUseCase submitContactMessage;
    private final String ipSalt;

    public ContactMessagesResource(
            SubmitContactMessageUseCase submitContactMessage, @ConfigProperty(name = "cv.contact.ip-salt") String ipSalt) {
        this.submitContactMessage = submitContactMessage;
        this.ipSalt = ipSalt;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(ProblemResponse.MEDIA_TYPE)
    @Operation(
            operationId = "sendContactMessage",
            summary = "Deja un mensaje de contacto",
            description = "Guarda un mensaje y avisa al dueño. Límite de envíos por remitente (por defecto 5 por hora). "
                    + "La IP no se guarda en claro: solo un hash con sal. Si el campo trampa `website` trae algo, "
                    + "el envío se descarta pero la respuesta es igual de correcta, para no dar pistas a los bots.")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ContactMessageRequest.class)))
    @APIResponse(responseCode = "201", description = "Mensaje recibido. Sin cuerpo.")
    @APIResponse(
            responseCode = "400",
            description = "Un campo es inválido o falta (`CONTACT_INVALID`); el detalle nombra el campo.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "invalidEmail", summary = "Correo no válido", value = PROBLEM_INVALID)))
    @APIResponse(
            responseCode = "429",
            description = "El remitente agotó su cupo de mensajes (`CONTACT_RATE_LIMITED`).",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "rateLimited", summary = "Demasiados envíos", value = PROBLEM_RATE_LIMITED)))
    public Response send(
            ContactMessageRequest request,
            @HeaderParam(CLOUDFLARE_IP_HEADER) String cloudflareIp,
            @Context HttpServerRequest http,
            @BeanParam LanguageParams docsOnly) {
        // `docsOnly` solo publica `lang` y `Accept-Language` en Swagger: el idioma de los errores lo resuelve el mapper.
        String senderKey = hash(clientIp(cloudflareIp, http));
        ContactMessageRequest body = request == null ? new ContactMessageRequest(null, null, null, null) : request;
        submitContactMessage.submit(
                new ContactSubmission(body.name(), body.email(), body.message(), body.website(), senderKey));
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Detrás del túnel de Cloudflare la IP real llega en {@code CF-Connecting-IP}. La API no publica puertos, así que
     * esa cabecera solo la pone Cloudflare; si alguien la expusiera directamente, podría falsearse.
     */
    private static String clientIp(String cloudflareIp, HttpServerRequest http) {
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            return cloudflareIp.strip();
        }
        return http.remoteAddress() == null ? "unknown" : http.remoteAddress().host();
    }

    private String hash(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((ipSalt + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
