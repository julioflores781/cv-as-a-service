package dev.julioflores.cv.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Cuerpos de la lectura de mensajes de contacto. Ningún campo expone datos del remitente más allá de lo que él escribió. */
public final class AdminMessagesModels {

    private AdminMessagesModels() {}

    @Schema(name = "AdminContactMessage", description = "Un mensaje dejado desde el formulario de contacto.")
    public record MessageItem(
            @Schema(required = true, description = "Identificador. Sirve de cursor: `before=<id>` pide los anteriores.", example = "41") long id,
            @Schema(required = true, description = "Nombre que escribió el remitente.", example = "Ada Ejemplo") String name,
            @Schema(required = true, description = "Correo para responder.", example = "ada@example.com") String email,
            @Schema(required = true, description = "El mensaje.", example = "Hola, me gustaría hablar contigo sobre una vacante de backend.") String body,
            @Schema(required = true, description = "Cuándo se recibió (UTC).", example = "2026-11-12T09:30:00Z") Instant createdAt) {}

    @Schema(name = "AdminContactMessageList", description = "Una página de mensajes, del más nuevo al más antiguo.")
    public record MessageList(
            @Schema(required = true, description = "Los mensajes de esta página. Vacía si no hay ninguno.") List<MessageItem> messages,
            @Schema(required = true, nullable = true,
                    description = "Cursor de la página siguiente (úsalo como `before`), o `null` si no hay más mensajes.",
                    example = "40") String next) {}
}
