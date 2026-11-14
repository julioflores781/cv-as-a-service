package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.contact.ContactMessagePage;
import dev.julioflores.cv.application.port.in.ListContactMessagesUseCase;
import dev.julioflores.cv.application.service.ListContactMessagesService;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminMessagesModels.MessageItem;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminMessagesModels.MessageList;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Lectura de los mensajes de contacto. Solo lectura: borrar o marcar como leído no existe. Los datos personales no se registran en logs. */
@Path("/api/v1/admin/contact-messages")
@Tag(name = "Admin")
@SecurityRequirement(name = AdminAuthResource.BEARER)
public class AdminMessagesResource {

    private static final int DEFAULT_LIMIT = 50;

    private final ListContactMessagesUseCase listMessages;

    public AdminMessagesResource(ListContactMessagesUseCase listMessages) {
        this.listMessages = listMessages;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminListContactMessages",
            summary = "Lista los mensajes de contacto",
            description = "Los mensajes que dejaron los visitantes, del más nuevo al más antiguo. Paginación por cursor: "
                    + "cada página trae `next`; pásalo como `before` para pedir la siguiente (`null` cuando no hay más). "
                    + "Un `before` que no existe da una lista vacía. Contiene datos personales (nombre, correo, texto); "
                    + "nunca devuelve el hash de la IP del remitente. Solo lectura.")
    @Parameter(name = "limit", description = "Cuántos mensajes devolver, entre 1 y 100 (50 por defecto).", example = "50")
    @Parameter(name = "before", description = "Cursor: el `next` de la página anterior (un entero positivo). Sin él se empieza por "
            + "el mensaje más nuevo.", example = "40")
    @APIResponse(responseCode = "200", description = "Una página de mensajes.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MessageList.class),
                    examples = {
                        @ExampleObject(name = "page", summary = "Con más páginas", value = AdminMessagesExamples.LIST),
                        @ExampleObject(name = "empty", summary = "Sin mensajes (o pasado el último)", value = AdminMessagesExamples.LAST_PAGE)
                    }))
    @APIResponse(responseCode = "400", description = "`limit` o `before` no son enteros, o están fuera de rango (`BAD_REQUEST`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "badParameter", value = AdminMessagesExamples.BAD_PARAMETER)))
    public MessageList list(@QueryParam("limit") String limit, @QueryParam("before") String before) {
        ContactMessagePage page = listMessages.list(
                parse(limit, 1, ListContactMessagesService.MAX_LIMIT, DEFAULT_LIMIT), parseCursor(before));
        return new MessageList(
                page.messages().stream()
                        .map(m -> new MessageItem(m.id(), m.name(), m.email(), m.body(), m.createdAt())).toList(),
                page.next() == null ? null : page.next().toString());
    }

    /** Un entero dentro de rango; ausente, el valor por defecto. Cualquier otra cosa es un 400, no un valor corregido en silencio. */
    private static int parse(String value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // cae al 400 de abajo
        }
        throw new BadRequestException();
    }

    private static Long parseCursor(String value) {
        if (value == null) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id >= 1) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // cae al 400 de abajo
        }
        throw new BadRequestException();
    }
}
