package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.contact.ContactMessageEntry;
import dev.julioflores.cv.application.contact.ContactSubmission;
import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.application.port.out.ContactNotifier;
import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.application.service.ContactRateLimitedException;
import dev.julioflores.cv.application.service.InvalidContactMessageException;
import dev.julioflores.cv.application.service.SubmitContactMessageService;
import dev.julioflores.cv.domain.ContactMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubmitContactMessageServiceTest {

    private static final String BODY = "Hola, quiero hablar contigo de un proyecto.";

    private final List<ContactMessage> saved = new ArrayList<>();
    private final List<String> savedKeys = new ArrayList<>();
    private final List<ContactMessage> notified = new ArrayList<>();
    private boolean allowed = true;
    private final List<String> limiterKeys = new ArrayList<>();

    private final ContactMessageRepository repository = new ContactMessageRepository() {
        @Override
        public void save(ContactMessage message, String key) {
            saved.add(message);
            savedKeys.add(key);
        }

        @Override
        public List<ContactMessageEntry> findNewestFirst(Long before, int limit) {
            throw new UnsupportedOperationException("not used when submitting");
        }
    };
    private final ContactNotifier notifier = notified::add;
    private final RateLimiter limiter = key -> {
        limiterKeys.add(key);
        return allowed;
    };
    private final SubmitContactMessageService service = new SubmitContactMessageService(repository, notifier, limiter);

    private static ContactSubmission submission(String name, String email, String message, String website) {
        return new ContactSubmission(name, email, message, website, "key-1");
    }

    private static String invalidField(Runnable action) {
        InvalidContactMessageException e = assertThrows(InvalidContactMessageException.class, action::run);
        assertEquals(ErrorCode.CONTACT_INVALID, e.code());
        return (String) e.args().get(0);
    }

    @Test
    void validMessageIsSavedWithTheSenderKeyAndNotified() {
        service.submit(submission("Ada", "ada@example.com", BODY, null));
        ContactMessage expected = new ContactMessage("Ada", "ada@example.com", BODY);
        assertEquals(List.of(expected), saved);
        assertEquals(List.of("key-1"), savedKeys);
        assertEquals(List.of(expected), notified);
    }

    @Test
    void fieldsAreTrimmedBeforeSaving() {
        service.submit(submission("  Ada  ", " ada@example.com ", "  " + BODY + "\n", ""));
        assertEquals(new ContactMessage("Ada", "ada@example.com", BODY), saved.get(0));
    }

    @Test
    void filledHoneypotSucceedsSilentlyWithoutSavingNotifyingOrCountingTheLimit() {
        service.submit(submission("Bot", "bot@example.com", BODY, "http://spam.example"));
        assertTrue(saved.isEmpty());
        assertTrue(notified.isEmpty());
        assertTrue(limiterKeys.isEmpty());
    }

    @Test
    void blankHoneypotCountsAsEmpty() {
        service.submit(submission("Ada", "ada@example.com", BODY, "   "));
        assertEquals(1, saved.size());
    }

    @Test
    void missingOrOversizedNameIsRejected() {
        assertEquals("name", invalidField(() -> service.submit(submission(null, "ada@example.com", BODY, null))));
        assertEquals("name", invalidField(() -> service.submit(submission("   ", "ada@example.com", BODY, null))));
        assertEquals("name", invalidField(() -> service.submit(
                submission("x".repeat(SubmitContactMessageService.MAX_NAME + 1), "ada@example.com", BODY, null))));
        service.submit(submission("x".repeat(SubmitContactMessageService.MAX_NAME), "ada@example.com", BODY, null));
    }

    @Test
    void badEmailsAreRejected() {
        for (String email : new String[] {null, "", "ada", "ada@", "@example.com", "ada@example", "a b@example.com",
                "ada@example.com\nBcc: x@y.com"}) {
            assertEquals("email", invalidField(() -> service.submit(submission("Ada", email, BODY, null))), email);
        }
        String longEmail = "a".repeat(SubmitContactMessageService.MAX_EMAIL) + "@example.com";
        assertEquals("email", invalidField(() -> service.submit(submission("Ada", longEmail, BODY, null))));
    }

    @Test
    void messageLengthIsBounded() {
        assertEquals("message", invalidField(() -> service.submit(submission("Ada", "ada@example.com", null, null))));
        assertEquals("message", invalidField(() -> service.submit(submission("Ada", "ada@example.com", "corto", null))));
        assertEquals("message", invalidField(() -> service.submit(submission("Ada", "ada@example.com",
                "x".repeat(SubmitContactMessageService.MAX_MESSAGE + 1), null))));
        service.submit(submission("Ada", "ada@example.com", "x".repeat(SubmitContactMessageService.MIN_MESSAGE), null));
        service.submit(submission("Ada", "ada@example.com", "x".repeat(SubmitContactMessageService.MAX_MESSAGE), null));
        assertEquals(2, saved.size());
    }

    @Test
    void invalidMessagesDoNotConsumeTheRateLimit() {
        assertThrows(InvalidContactMessageException.class, () -> service.submit(submission("", "x", BODY, null)));
        assertTrue(limiterKeys.isEmpty());
    }

    @Test
    void rateLimitedSenderIsRejectedWithoutSavingOrNotifying() {
        allowed = false;
        ContactRateLimitedException e = assertThrows(ContactRateLimitedException.class,
                () -> service.submit(submission("Ada", "ada@example.com", BODY, null)));
        assertEquals(ErrorCode.CONTACT_RATE_LIMITED, e.code());
        assertEquals(List.of("key-1"), limiterKeys);
        assertTrue(saved.isEmpty());
        assertTrue(notified.isEmpty());
    }

    @Test
    void developerMessagesStayInEnglishForLogs() {
        assertEquals("Invalid contact field 'email'", new InvalidContactMessageException("email").getMessage());
    }
}
