package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.infrastructure.config.RequestIds;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequestIdsTest {

    @Test
    void acceptsOnlyTheEdgesOfThePattern() { // T18
        assertEquals(Optional.of("abcdefgh"), RequestIds.accept("abcdefgh")); // 8: mínimo
        assertEquals(Optional.empty(), RequestIds.accept("abcdefg")); // 7
        assertEquals(Optional.of("a".repeat(64)), RequestIds.accept("a".repeat(64))); // 64: máximo
        assertEquals(Optional.empty(), RequestIds.accept("a".repeat(65))); // 65
        assertEquals(Optional.of("ABC-123_xyz.789"), RequestIds.accept("ABC-123_xyz.789")); // guiones, subrayado, puntos, mayúsculas
        assertEquals(Optional.of("--------"), RequestIds.accept("--------"));
        assertEquals(Optional.of("........"), RequestIds.accept("........"));
        assertEquals(Optional.empty(), RequestIds.accept(null));
        assertEquals(Optional.empty(), RequestIds.accept(""));
        assertEquals(Optional.empty(), RequestIds.accept("has space here"));
        assertEquals(Optional.empty(), RequestIds.accept("<script>alert(1)</script>"));
        assertEquals(Optional.empty(), RequestIds.accept("abcdefgh\n"));
        assertEquals(Optional.empty(), RequestIds.accept("abcdefgh\r\nX-Evil: 1"));
        assertEquals(Optional.empty(), RequestIds.accept("identificación-ñ"));
    }

    @Test
    void generatedIdsAre32HexAndDifferent() { // extra fuera del plan: el formato generado es aceptado por el propio patrón
        String first = RequestIds.generate();
        String second = RequestIds.generate();
        assertTrue(first.matches("[0-9a-f]{32}"), first);
        assertNotEquals(first, second);
        assertEquals(Optional.of(first), RequestIds.accept(first));
    }
}
