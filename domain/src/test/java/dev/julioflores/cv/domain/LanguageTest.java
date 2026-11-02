package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void parsesRegionalTags() {
        assertEquals(Language.EN, Language.fromTag("en-US"));
        assertEquals(Language.ES, Language.fromTag("es-ES"));
    }

    @Test
    void fallsBackToSpanish() {
        assertEquals(Language.ES, Language.fromTag(null));
        assertEquals(Language.ES, Language.fromTag(" "));
        assertEquals(Language.ES, Language.fromTag("fr"));
    }
}
