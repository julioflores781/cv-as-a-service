package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.contact.ContactSubmission;
import dev.julioflores.cv.application.port.in.SubmitContactMessageUseCase;
import dev.julioflores.cv.application.port.out.ContactMessageRepository;
import dev.julioflores.cv.application.port.out.ContactNotifier;
import dev.julioflores.cv.application.port.out.RateLimiter;
import dev.julioflores.cv.domain.ContactMessage;
import java.util.regex.Pattern;

public class SubmitContactMessageService implements SubmitContactMessageUseCase {

    public static final int MAX_NAME = 100;
    public static final int MAX_EMAIL = 254;
    public static final int MIN_MESSAGE = 10;
    public static final int MAX_MESSAGE = 2000;

    /** Deliberadamente simple: comprueba la forma, no que el buzón exista. Sin espacios, así que no admite saltos de línea. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ContactMessageRepository repository;
    private final ContactNotifier notifier;
    private final RateLimiter rateLimiter;

    public SubmitContactMessageService(
            ContactMessageRepository repository, ContactNotifier notifier, RateLimiter rateLimiter) {
        this.repository = repository;
        this.notifier = notifier;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void submit(ContactSubmission submission) {
        if (submission.website() != null && !submission.website().isBlank()) {
            return; // bot: éxito aparente, sin guardar ni avisar
        }
        ContactMessage message = validate(submission);
        // El cupo se consume solo con envíos válidos: un error al teclear no cuesta un intento.
        if (!rateLimiter.tryAcquire(submission.senderKey())) {
            throw new ContactRateLimitedException();
        }
        repository.save(message, submission.senderKey());
        notifier.notify(message);
    }

    private static ContactMessage validate(ContactSubmission submission) {
        String name = trimmed(submission.name());
        String email = trimmed(submission.email());
        String message = trimmed(submission.message());
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new InvalidContactMessageException("name");
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new InvalidContactMessageException("email");
        }
        if (message.length() < MIN_MESSAGE || message.length() > MAX_MESSAGE) {
            throw new InvalidContactMessageException("message");
        }
        return new ContactMessage(name, email, message);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.strip();
    }
}
