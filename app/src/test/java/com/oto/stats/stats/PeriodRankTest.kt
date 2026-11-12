package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodRankTest {
    private val mon = DayOfWeek.MONDAY
    private fun day(y: Int, m: Int, d: Int, ms: Long) = DayTotal(LocalDate.of(y, m, d).toEpochDay(), ms, 1)
    private fun month(today: LocalDate, offset: Long = 0) =
        StatsPeriod.current(PeriodKind.MONTH, today, mon, null).let { p -> (0 until -offset).fold(p) { acc, _ -> acc.previous(mon) } }

    @Test fun finishedMonthIsRankedAmongAllMonths() {
        val today = LocalDate.of(2026, 11, 20)
        val daily = listOf(
            day(2026, 7, 5, 100), day(2026, 8, 5, 300), day(2026, 9, 5, 200), day(2026, 10, 5, 250),
        )
        val oct = month(today, -1)
        val r = PeriodRank.compute(daily, oct, today, mon)!!
        assertEquals(2, r.rank)
        assertEquals(4, r.of)
        assertEquals(false, r.sameElapsed)
        assertEquals("Your 2nd biggest month of 4.", PeriodRank.sentence(r, PeriodKind.MONTH))
    }

    @Test fun runningMonthComparesEqualElapsedDays() {
        val today = LocalDate.of(2026, 11, 11) // 11 days elapsed
        val daily = listOf(
            day(2026, 9, 5, 100), day(2026, 9, 25, 9_000), // late-month listening must not count
            day(2026, 10, 5, 200), day(2026, 10, 28, 9_000),
            day(2026, 8, 3, 50),
            day(2026, 11, 2, 150),
        )
        val r = PeriodRank.compute(daily, month(today), today, mon)!!
        assertEquals(true, r.sameElapsed)
        assertEquals(2, r.rank) // 200 (Oct) > 150 (Nov) > 100 (Sep) > 50 (Aug)
        assertEquals(4, r.of)
        assertEquals("Your 2nd best start to a month of 4.", PeriodRank.sentence(r, PeriodKind.MONTH))
    }

    @Test fun tooFewPeriodsOrAllTimeGivesNothing() {
        val today = LocalDate.of(2026, 11, 20)
        assertNull(PeriodRank.compute(listOf(day(2026, 11, 2, 100), day(2026, 10, 2, 50)), month(today), today, mon))
        assertNull(PeriodRank.compute(emptyList(), month(today), today, mon))
        val all = StatsPeriod.current(PeriodKind.ALL, today, mon, LocalDate.of(2026, 1, 1))
        assertNull(PeriodRank.compute(listOf(day(2026, 11, 2, 100)), all, today, mon))
    }

    @Test fun ordinals() {
        assertEquals("1st", PeriodRank.ordinal(1)); assertEquals("2nd", PeriodRank.ordinal(2))
        assertEquals("3rd", PeriodRank.ordinal(3)); assertEquals("4th", PeriodRank.ordinal(4))
        assertEquals("11th", PeriodRank.ordinal(11)); assertEquals("12th", PeriodRank.ordinal(12))
        assertEquals("22nd", PeriodRank.ordinal(22)); assertEquals("113th", PeriodRank.ordinal(113))
    }

    @Test fun firstPlaceWording() {
        assertEquals("Your biggest week of 9.", PeriodRank.sentence(PeriodRank.Result(1, 9, false), PeriodKind.WEEK))
        assertEquals("Your best start to a year of 3.", PeriodRank.sentence(PeriodRank.Result(1, 3, true), PeriodKind.YEAR))
    }
}
