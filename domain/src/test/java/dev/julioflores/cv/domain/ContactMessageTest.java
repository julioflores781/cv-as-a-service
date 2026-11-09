package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContactMessageTest {

    @Test
    void keepsItsFields() {
        ContactMessage message = new ContactMessage("Ada", "ada@example.com", "Hola");
        assertEquals("Ada", message.name());
        assertEquals("ada@example.com", message.email());
        assertEquals("Hola", message.body());
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(NullPointerException.class, () -> new ContactMessage(null, "a@b.co", "x"));
        assertThrows(NullPointerException.class, () -> new ContactMessage("Ada", null, "x"));
        assertThrows(NullPointerException.class, () -> new ContactMessage("Ada", "a@b.co", null));
    }
}
