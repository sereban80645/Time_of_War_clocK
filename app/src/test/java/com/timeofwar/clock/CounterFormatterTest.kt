package com.timeofwar.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CounterFormatterTest {
    @Test fun dayCountChangesAtTheEventClockTime() {
        val start = LocalDateTime.of(2022, 2, 24, 2, 40)
        assertEquals("1 д.", CounterFormatter.format(start, LocalDateTime.of(2022, 2, 25, 2, 39), true, false))
        assertEquals("2 д.", CounterFormatter.format(start, LocalDateTime.of(2022, 2, 25, 2, 40), true, false))
    }

    @Test fun hiddenHoursAreOmittedInCalendarMode() {
        val start = LocalDateTime.of(2022, 2, 24, 2, 40)
        assertEquals("1 р. 0 міс. 0 д.", CounterFormatter.format(start, LocalDateTime.of(2023, 2, 24, 2, 40), false, false))
    }

    @Test fun hoursCanBeDisplayedInDayMode() {
        val start = LocalDateTime.of(2022, 2, 24, 2, 40)
        assertEquals("2 д. 0 год.", CounterFormatter.format(start, LocalDateTime.of(2022, 2, 25, 2, 40), true, true))
    }
}
