package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.domain.ContactMessage;
import java.util.List;

public interface ContactMessageRepository {

    /** Guarda el mensaje con la clave opaca de quien lo envió. Si falla, lanza: no se puede perder un mensaje en silencio. */
    void save(ContactMessage message, String senderKey);

    /**
     * Hasta {@code limit} mensajes, de {@code created_at} más reciente a más antiguo y, a igualdad, de id mayor a menor.
     * Con {@code before}, solo los que van después de ese mensaje en ese orden; si no existe, la lista sale vacía.
     * Nunca expone la clave del remitente.
     */
    List<ContactMessageEntry> findNewestFirst(Long before, int limit);
}
