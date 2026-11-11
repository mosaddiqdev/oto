package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityBucketsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(d: LocalDate, ms: Long, plays: Int) = DayTotal(d.toEpochDay(), ms, plays)

    @Test fun monthHasOneBarPerDayWithStates() {
        val period = StatsPeriod.current(PeriodKind.MONTH, today, DayOfWeek.MONDAY, LocalDate.of(2026, 11, 5))
        val bars = ActivityBuckets.build(PeriodKind.MONTH, period.range, today, LocalDate.of(2026, 11, 5), listOf(day(LocalDate.of(2026, 11, 6), 5_000, 2)))
        assertEquals(30, bars.size)
        assertEquals(BarState.BEFORE_TRACKING, bars[0].state) // Nov 1: before the first tracked day
        assertEquals(BarState.TRACKED, bars[4].state)         // Nov 5
        assertEquals(5_000L, bars[5].playedMs)                // Nov 6
        assertEquals(BarState.FUTURE, bars[29].state)
    }

    @Test fun yearGroupsDaysIntoMonths() {
        val period = StatsPeriod.current(PeriodKind.YEAR, today, DayOfWeek.MONDAY, null)
        val bars = ActivityBuckets.build(
            PeriodKind.YEAR, period.range, today, LocalDate.of(2026, 1, 1),
            listOf(day(LocalDate.of(2026, 3, 2), 100, 1), day(LocalDate.of(2026, 3, 20), 50, 2)),
        )
        assertEquals(12, bars.size)
        assertEquals(150L, bars[2].playedMs)
        assertEquals(3, bars[2].plays)
        assertEquals(BarState.FUTURE, bars[11].state)
    }

    @Test fun allTimeSwitchesToYearsWhenLong() {
        val first = LocalDate.of(2018, 1, 1)
        val period = StatsPeriod.current(PeriodKind.ALL, today, DayOfWeek.MONDAY, first)
        assertEquals(BarUnit.YEAR, ActivityBuckets.unitFor(PeriodKind.ALL, period.range))
        val recent = StatsPeriod.current(PeriodKind.ALL, today, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 1))
        assertEquals(BarUnit.MONTH, ActivityBuckets.unitFor(PeriodKind.ALL, recent.range))
    }
}
