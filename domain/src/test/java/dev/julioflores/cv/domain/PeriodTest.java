package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PeriodTest {

    @Test
    void periodWithoutEndIsCurrent() {
        assertTrue(new Period(YearMonth.of(2017, 10), null).current());
    }

    @Test
    void periodWithEndIsNotCurrent() {
        assertFalse(new Period(YearMonth.of(2014, 3), YearMonth.of(2016, 8)).current());
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new Period(YearMonth.of(2016, 8), YearMonth.of(2014, 3)));
    }
}
