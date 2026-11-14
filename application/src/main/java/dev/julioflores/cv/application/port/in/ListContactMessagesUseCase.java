package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.contact.ContactMessagePage;

public interface ListContactMessagesUseCase {

    /**
     * Los mensajes de contacto, del más nuevo al más antiguo.
     *
     * @param limit cuántos devolver como máximo (se acota a 1..100)
     * @param before cursor: solo los anteriores al mensaje con este id; {@code null} para empezar por el más nuevo
     */
    ContactMessagePage list(int limit, Long before);
}
