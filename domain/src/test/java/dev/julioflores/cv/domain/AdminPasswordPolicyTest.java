package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminPasswordPolicyTest {

    @Test
    void acceptsOnlyTwelveToOneTwentyEightDifferentFromTheCurrentOne() { // T7
        assertFalse(AdminPasswordPolicy.isAcceptable("a".repeat(11), null));
        assertTrue(AdminPasswordPolicy.isAcceptable("a".repeat(12), null));
        assertTrue(AdminPasswordPolicy.isAcceptable("a".repeat(128), null));
        assertFalse(AdminPasswordPolicy.isAcceptable("a".repeat(129), null));
        assertFalse(AdminPasswordPolicy.isAcceptable(null, null));
        assertFalse(AdminPasswordPolicy.isAcceptable("", null));
        assertFalse(AdminPasswordPolicy.isAcceptable(" ".repeat(20), null));
        assertFalse(AdminPasswordPolicy.isAcceptable("la misma frase larga", "la misma frase larga"));
        assertTrue(AdminPasswordPolicy.isAcceptable("otra frase larga distinta", "la misma frase larga"));
    }

    @Test
    void lengthCountsCodePointsNotBytes() { // T8
        // 12 emojis = 12 puntos de código (24 chars UTF-16, 48 bytes UTF-8): vale.
        assertTrue(AdminPasswordPolicy.isAcceptable("😀".repeat(12), null));
        assertFalse(AdminPasswordPolicy.isAcceptable("😀".repeat(11), null));
        // 128 emojis valen; 129 no, aunque ocupen muchos más bytes.
        assertTrue(AdminPasswordPolicy.isAcceptable("😀".repeat(128), null));
        assertFalse(AdminPasswordPolicy.isAcceptable("😀".repeat(129), null));
        // Acentos: 12 caracteres, 24 bytes.
        assertTrue(AdminPasswordPolicy.isAcceptable("ñáéíóúüñáéíó".repeat(1), null));
    }
}
