package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.ContactMessage;

public interface ContactMessageRepository {

    /** Guarda el mensaje con la clave opaca de quien lo envió. Si falla, lanza: no se puede perder un mensaje en silencio. */
    void save(ContactMessage message, String senderKey);
}
