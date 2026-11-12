package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.application.contact.ContactMessagePage;
import dev.julioflores.cv.application.port.in.ListContactMessagesUseCase;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import java.util.List;

public class ListContactMessagesService implements ListContactMessagesUseCase {

    public static final int MAX_LIMIT = 100;

    private final ContactMessageRepository messages;

    public ListContactMessagesService(ContactMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public ContactMessagePage list(int limit, Long before) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        // Se pide uno de más: si llega, hay otra página y el cursor es el último que sí se devuelve.
        List<ContactMessageEntry> found = messages.findNewestFirst(before, bounded + 1);
        if (found.size() <= bounded) {
            return new ContactMessagePage(found, null);
        }
        List<ContactMessageEntry> page = found.subList(0, bounded);
        return new ContactMessagePage(page, page.get(bounded - 1).id());
    }
}
