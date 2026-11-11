package com.oto.stats.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPeriodTest {
    private val mon = DayOfWeek.MONDAY
    private val today = LocalDate.of(2026, 11, 11) // a Wednesday

    private fun cur(kind: PeriodKind, first: LocalDate? = LocalDate.of(2024, 3, 5)) =
        StatsPeriod.current(kind, today, mon, first)

    @Test fun weekStartsOnConfiguredDay() {
        assertEquals(LocalDate.of(2026, 11, 9), cur(PeriodKind.WEEK).range.start)
        val sunday = StatsPeriod.current(PeriodKind.WEEK, today, DayOfWeek.SUNDAY, null)
        assertEquals(LocalDate.of(2026, 11, 8), sunday.range.start)
        assertEquals(7, sunday.range.days)
    }

    @Test fun monthAndYearBoundaries() {
        val m = cur(PeriodKind.MONTH).range
        assertEquals(LocalDate.of(2026, 11, 1), m.start)
        assertEquals(LocalDate.of(2026, 12, 1), m.endExclusive)
        val y = cur(PeriodKind.YEAR).range
        assertEquals(LocalDate.of(2026, 1, 1), y.start)
        assertEquals(LocalDate.of(2027, 1, 1), y.endExclusive)
    }

    @Test fun steppingBackAcrossYearEnd() {
        val jan = StatsPeriod.containing(PeriodKind.MONTH, LocalDate.of(2026, 1, 20), mon, null, today)
        assertEquals(LocalDate.of(2025, 12, 1), jan.previous(mon).range.start)
        assertEquals(31, jan.previous(mon).range.days)
    }

    @Test fun nextIsBlockedAtCurrentAndFuture() {
        assertFalse(cur(PeriodKind.MONTH).canGoNext(today))
        assertTrue(cur(PeriodKind.MONTH).previous(mon).canGoNext(today))
        assertFalse(cur(PeriodKind.ALL).canGoNext(today))
    }

    @Test fun previousIsBlockedBeforeFirstTrackedDay() {
        val first = LocalDate.of(2026, 10, 15)
        val oct = cur(PeriodKind.MONTH, first).previous(mon)
        assertTrue(cur(PeriodKind.MONTH, first).canGoPrevious(first)) // October overlaps history
        assertFalse(oct.canGoPrevious(first)) // September does not
        assertFalse(cur(PeriodKind.MONTH).canGoPrevious(null))
    }

    @Test fun runningPeriodComparesEqualElapsedDays() {
        val cmp = cur(PeriodKind.MONTH).comparison(today, mon)!!
        assertEquals(LocalDate.of(2026, 10, 1), cmp.start)
        assertEquals(LocalDate.of(2026, 10, 12), cmp.endExclusive) // Oct 1..11 = 11 days, like Nov 1..11
        assertTrue(cur(PeriodKind.MONTH).comparisonIsPartial(today, mon))
    }

    @Test fun elapsedComparisonIsCappedByShorterPreviousMonth() {
        val d = LocalDate.of(2026, 3, 31)
        val march = StatsPeriod.current(PeriodKind.MONTH, d, mon, null)
        val cmp = march.comparison(d, mon)!!
        assertEquals(LocalDate.of(2026, 3, 1), cmp.endExclusive) // Feb has only 28 days
    }

    @Test fun finishedPeriodComparesWithWholePreviousOne() {
        val oct = cur(PeriodKind.MONTH).previous(mon)
        val cmp = oct.comparison(today, mon)!!
        assertEquals(LocalDate.of(2026, 9, 1), cmp.start)
        assertEquals(LocalDate.of(2026, 10, 1), cmp.endExclusive)
        assertFalse(oct.comparisonIsPartial(today, mon))
    }

    @Test fun allTimeHasNoComparisonAndStartsAtFirstDay() {
        val all = cur(PeriodKind.ALL)
        assertNull(all.comparison(today, mon))
        assertEquals(LocalDate.of(2024, 3, 5), all.range.start)
        assertEquals(today.plusDays(1), all.range.endExclusive)
        assertEquals(today, StatsPeriod.current(PeriodKind.ALL, today, mon, null).range.start)
    }

    @Test fun dstDayIsShorterButStillOneCalendarDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val range = DayRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30)) // clocks go forward
        assertEquals(23L * 3_600_000L, range.endMs(zone) - range.startMs(zone))
    }

    @Test fun labels() {
        assertEquals("November 2026", cur(PeriodKind.MONTH).label(Locale.US))
        assertEquals("2026", cur(PeriodKind.YEAR).label(Locale.US))
        assertEquals("Nov 9 – 15", cur(PeriodKind.WEEK).label(Locale.US))
        val cross = StatsPeriod.containing(PeriodKind.WEEK, LocalDate.of(2026, 10, 29), mon, null, today)
        assertEquals("Oct 26 – Nov 1", cross.label(Locale.US))
        assertEquals("Nov 1, 2026 – Nov 11, 2026", cur(PeriodKind.MONTH).resolvedDates(today, Locale.US))
    }
}
