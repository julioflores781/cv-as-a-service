package dev.julioflores.cv.application.contact;

import java.util.List;

/** Una página de mensajes, del más nuevo al más antiguo. {@code next} es el cursor de la siguiente página, o {@code null} si no hay más. */
public record ContactMessagePage(List<ContactMessageEntry> messages, Long next) {

    public ContactMessagePage {
        messages = List.copyOf(messages);
    }
}
