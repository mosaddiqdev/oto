package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowingUpTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun day(d: LocalDate, ms: Long) = DayTotal(d.toEpochDay(), ms, 1)

    @Test fun statesAreKnownNotZero() {
        val days = ShowingUp.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), today, LocalDate.of(2026, 11, 4), emptyList())
        assertEquals(30, days.size)
        assertEquals(BarState.BEFORE_TRACKING, days[0].state)
        assertEquals(BarState.TRACKED, days[3].state)
        assertEquals(BarState.FUTURE, days[10].state)
        assertEquals(7, ShowingUp.elapsed(days)) // Nov 4 to Nov 10
    }

    @Test fun levelsFollowQuartilesAndZeroStaysZero() {
        val data = (1..8).map { day(LocalDate.of(2026, 11, it), it * 1000L) }
        val days = ShowingUp.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), today, null, data)
        assertEquals(1, days[0].level)
        assertEquals(4, days[7].level)
        assertEquals(0, days[8].level) // Nov 9, no listening
        assertEquals(8, ShowingUp.active(days))
    }

    @Test fun longestRunIsConsecutiveDaysAndLaterWinsTies() {
        val data = listOf(1, 2, 3, 5, 6, 7).map { day(LocalDate.of(2026, 11, it), 1000L) }
        val days = ShowingUp.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), today, null, data)
        val run = ShowingUp.longestRun(days)!!
        assertEquals(3, run.length)
        assertEquals(LocalDate.of(2026, 11, 7), run.end)
    }

    @Test fun noListeningMeansNoRun() {
        val days = ShowingUp.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), today, null, emptyList())
        assertNull(ShowingUp.longestRun(days))
    }
}
