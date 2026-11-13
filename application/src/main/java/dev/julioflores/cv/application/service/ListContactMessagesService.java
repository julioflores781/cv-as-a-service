package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.application.contact.ContactMessagePage;
import dev.julioflores.cv.application.port.in.ListContactMessagesUseCase;
import dev.julioflores.cv.application.port.out.AuditLog;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import java.util.List;

public class ListContactMessagesService implements ListContactMessagesUseCase {

    public static final int MAX_LIMIT = 100;

    private final ContactMessageRepository messages;
    private final AuditLog audit;

    public ListContactMessagesService(ContactMessageRepository messages, AuditLog audit) {
        this.messages = messages;
        this.audit = audit;
    }

    @Override
    public ContactMessagePage list(int limit, Long before) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        // Se pide uno de más: si llega, hay otra página y el cursor es el último que sí se devuelve.
        List<ContactMessageEntry> found = messages.findNewestFirst(before, bounded + 1);
        if (found.size() <= bounded) {
            record(bounded, found.size(), false);
            return new ContactMessagePage(found, null);
        }
        List<ContactMessageEntry> page = found.subList(0, bounded);
        record(bounded, bounded, true);
        return new ContactMessagePage(page, page.get(bounded - 1).id());
    }

    /** Solo cifras: ni nombres, ni correos, ni texto de los mensajes. */
    private void record(int limit, int returned, boolean paged) {
        audit.record(AuditEvent.success(AuditAction.MESSAGES_READ, "limit", limit, "returned", returned, "paged", paged));
    }
}
