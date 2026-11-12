package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.infrastructure.adapter.out.security.Argon2PasswordHasher;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

@QuarkusTest
class Argon2PasswordHasherTest {

    private static final String PASSWORD = "una frase larga y secreta";
    private static final String SALT_AND_HASH = "$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNo";

    @Inject
    Argon2PasswordHasher hasher;

    private static Argon2PasswordHasher withCost(int memoryKib, int iterations, int parallelism) throws Exception {
        Constructor<Argon2PasswordHasher> constructor =
                Argon2PasswordHasher.class.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(memoryKib, iterations, parallelism);
    }

    @Test
    void hashIsPhcArgon2idAndEachHashIsSalted() { // T63
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);
        assertTrue(first.matches("^\\$argon2id\\$v=19\\$m=1024,t=1,p=1\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+$"), first);
        assertNotEquals(first, second);
    }

    @Test
    void verifyAcceptsTheRightPasswordAndRejectsEverythingElseWithoutThrowing() { // T64
        String hash = hasher.hash(PASSWORD);
        assertTrue(hasher.verify(PASSWORD, hash));
        assertFalse(hasher.verify("otra cosa distinta", hash));
        assertFalse(hasher.verify("x", "esto no es un hash"));
        assertFalse(hasher.verify("x", "$argon2id$v=19$m=1024,t=1,p=1$%%%$###"));
        assertFalse(hasher.verify("x", "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"));
        assertFalse(hasher.verify("x", "$argon2i$v=19$m=1024,t=1,p=1" + SALT_AND_HASH));
        assertFalse(hasher.verify("x", null));
    }

    @Test
    void needsRehashIsTrueForAHashMadeWithLessCost() throws Exception { // T65
        String cheap = withCost(512, 1, 1).hash(PASSWORD);
        assertTrue(hasher.needsRehash(cheap));
        assertFalse(hasher.needsRehash(hasher.hash(PASSWORD)));
        assertTrue(withCost(2048, 2, 1).needsRehash(hasher.hash("x")));
    }

    @Test
    void aMaxLengthOrUnicodePasswordWorks() { // T66
        String longest = "ñ".repeat(128);
        String unicode = "contraseña 😀 日本語 larga";
        assertTrue(hasher.verify(longest, hasher.hash(longest)));
        assertTrue(hasher.verify(unicode, hasher.hash(unicode)));
        assertFalse(hasher.verify(unicode + "!", hasher.hash(unicode)));
    }

    @Test
    void aTamperedHashWithAbsurdParametersIsRejectedInsteadOfExhaustingMemory() { // extra fuera del plan
        String absurdMemory = "$argon2id$v=19$m=999999999,t=1,p=1" + SALT_AND_HASH;
        assertFalse(hasher.verify("x", absurdMemory));
        assertFalse(hasher.needsRehash(absurdMemory));
        assertFalse(hasher.verify("x", "$argon2id$v=19$m=1024,t=1,p=99" + SALT_AND_HASH));
        assertFalse(hasher.needsRehash("basura"));
        assertFalse(hasher.needsRehash(null));
    }
}
