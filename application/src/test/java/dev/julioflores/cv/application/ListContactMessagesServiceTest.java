package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.AdminFakes.RecordingAuditLog;
import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.application.contact.ContactMessagePage;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.application.service.ListContactMessagesService;
import dev.julioflores.cv.domain.ContactMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListContactMessagesServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-12T09:00:00Z");

    /** Repositorio en memoria con el mismo contrato que el real: más nuevo primero, id mayor primero a igualdad de fecha. */
    private static final class FakeRepository implements ContactMessageRepository {
        final List<ContactMessageEntry> stored = new ArrayList<>();
        Long lastBefore;
        int lastLimit;

        @Override
        public void save(ContactMessage message, String senderKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ContactMessageEntry> findNewestFirst(Long before, int limit) {
            lastBefore = before;
            lastLimit = limit;
            Comparator<ContactMessageEntry> order = Comparator.comparing(ContactMessageEntry::createdAt)
                    .thenComparing(ContactMessageEntry::id).reversed();
            List<ContactMessageEntry> sorted = stored.stream().sorted(order).toList();
            ContactMessageEntry cursor = before == null ? null
                    : stored.stream().filter(m -> m.id() == before).findFirst().orElse(null);
            if (before != null && cursor == null) {
                return List.of();
            }
            return sorted.stream().filter(m -> cursor == null || order.compare(m, cursor) > 0).limit(limit).toList();
        }
    }

    private final FakeRepository repository = new FakeRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final ListContactMessagesService service = new ListContactMessagesService(repository, audit);

    private void store(int count) {
        for (int id = 1; id <= count; id++) {
            repository.stored.add(new ContactMessageEntry(id, "Ada " + id, "ada" + id + "@example.com", "Mensaje número " + id, T0.plusSeconds(id)));
        }
    }

    private static List<Long> ids(ContactMessagePage page) {
        return page.messages().stream().map(ContactMessageEntry::id).toList();
    }

    @Test
    void messagesComeNewestFirstWithoutTheSenderKeyAndTheNextCursor() { // T62
        store(5);
        ContactMessagePage first = service.list(2, null);
        assertEquals(List.of(5L, 4L), ids(first));
        assertEquals(4L, first.next());

        ContactMessagePage second = service.list(2, first.next());
        assertEquals(List.of(3L, 2L), ids(second));
        assertEquals(2L, second.next());

        ContactMessagePage last = service.list(2, second.next());
        assertEquals(List.of(1L), ids(last));
        assertNull(last.next());
        // Un mensaje de la lista solo lleva lo que el remitente escribió y la fecha: no hay ningún campo de clave de remitente.
        assertEquals(List.of("id", "name", "email", "body", "createdAt"),
                java.util.Arrays.stream(ContactMessageEntry.class.getRecordComponents()).map(c -> c.getName()).toList());
    }

    @Test
    void aPageThatFitsExactlyHasNoNextCursor() {
        store(3);
        ContactMessagePage page = service.list(3, null);
        assertEquals(List.of(3L, 2L, 1L), ids(page));
        assertNull(page.next());
    }

    @Test
    void anEmptyListGivesAnEmptyPageWithoutCursor() {
        ContactMessagePage page = service.list(50, null);
        assertTrue(page.messages().isEmpty());
        assertNull(page.next());
    }

    @Test
    void aCursorThatDoesNotExistGivesAnEmptyPage() {
        store(3);
        ContactMessagePage page = service.list(10, 999L);
        assertTrue(page.messages().isEmpty());
        assertNull(page.next());
        assertEquals(999L, repository.lastBefore);
    }

    @Test
    void theLimitIsBoundedBetweenOneAndOneHundredAndOneExtraRowIsRequested() {
        store(1);
        service.list(0, null);
        assertEquals(2, repository.lastLimit);
        service.list(-7, null);
        assertEquals(2, repository.lastLimit);
        service.list(100_000, null);
        assertEquals(101, repository.lastLimit);
        service.list(30, null);
        assertEquals(31, repository.lastLimit);
    }

    @Test
    void sameTimestampMessagesKeepAStableOrderByIdAndPaginateWithoutRepeatsOrGaps() {
        for (int id = 1; id <= 4; id++) {
            repository.stored.add(new ContactMessageEntry(id, "Ada", "ada@example.com", "Mensaje del mismo instante", T0));
        }
        ContactMessagePage first = service.list(3, null);
        assertEquals(List.of(4L, 3L, 2L), ids(first));
        assertEquals(List.of(1L), ids(service.list(3, first.next())));
    }

    @Test
    void everyReadRecordsLimitReturnedAndPagedWithoutNamesEmailsOrBodies() { // T43
        store(5);
        service.list(2, null);
        assertEquals(RecordingAuditLog.success(AuditAction.MESSAGES_READ, Map.of("limit", "2", "returned", "2", "paged", "true")),
                audit.only());
        audit.events.clear();

        service.list(100_000, null);
        assertEquals(Map.of("limit", "100", "returned", "5", "paged", "false"), audit.only().details());
        String logged = audit.everything();
        assertTrue(!logged.contains("Ada") && !logged.contains("@example.com") && !logged.contains("Mensaje número"));
    }

    @Test
    void anEmptyPageIsStillRecordedAsARead() { // extra fuera del plan
        service.list(10, null);
        assertEquals(Map.of("limit", "10", "returned", "0", "paged", "false"), audit.only().details());
    }
}
