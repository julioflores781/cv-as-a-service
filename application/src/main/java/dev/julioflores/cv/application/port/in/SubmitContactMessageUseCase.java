package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.contact.ContactSubmission;

public interface SubmitContactMessageUseCase {

    /**
     * Valida, limita, guarda y avisa. Un envío con el campo trampa relleno termina sin error y sin hacer nada,
     * para que un bot no sepa que lo hemos descartado.
     */
    void submit(ContactSubmission submission);
}
