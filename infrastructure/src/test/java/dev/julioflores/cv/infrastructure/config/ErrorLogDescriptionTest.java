package dev.julioflores.cv.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ErrorLogDescriptionTest {

    private static final String ROW = "Failing row contains (1, Ada, ada@x.test, texto secreto)";

    @Test
    void aDatabaseErrorKeepsOnlyTheClassAndTheSqlState() { // T25
        var sql = new SQLException("ERROR: null value in column \"email\" violates not-null constraint\n  Detail: " + ROW, "23502");
        var error = new IllegalStateException("Could not save the message", sql);

        var described = ErrorLogDescription.describe(error);

        assertTrue(described.message().contains("SQLState=23502"), described.message());
        assertTrue(described.message().contains("SQLException"), described.message());
        assertNull(described.thrown(), "ni traza ni causa se adjuntan");
        String text = described.message();
        for (String secret : new String[] {"Ada", "ada@x.test", "texto secreto", "Failing row", "Could not save", "at dev."}) {
            assertFalse(text.contains(secret), text);
        }
    }

    @Test
    void aSqlErrorBuriedDeepInTheCauseChainIsFound() { // extra fuera del plan
        var error = new RuntimeException("a", new IllegalStateException("b", new RuntimeException("c", new SQLException(ROW, "08006"))));
        assertTrue(ErrorLogDescription.describe(error).message().contains("SQLState=08006"));
    }

    @Test
    void anUnexpectedSqlStateIsNotCopiedIntoTheLog() { // extra fuera del plan: el SQLState también es texto del driver
        assertTrue(ErrorLogDescription.describe(new SQLException(ROW, "not\nvalid")).message().contains("SQLState=-"));
        assertTrue(ErrorLogDescription.describe(new SQLException(ROW)).message().contains("SQLState=-"));
    }

    @Test
    void anErrorWithoutSqlKeepsItsFullTrace() { // T25
        var error = new IllegalStateException("boom", new IllegalArgumentException("inner"));
        var described = ErrorLogDescription.describe(error);
        assertTrue(described.message().equals("Unexpected error"));
        assertSame(error, described.thrown());
    }

    @Test
    void aSelfReferencingCauseDoesNotLoopForever() { // extra fuera del plan
        var error = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertSame(error, ErrorLogDescription.describe(error).thrown());
    }
}
