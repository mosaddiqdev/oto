package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkylineTest {
    private val start = LocalDate.of(2026, 1, 1)
    private fun d(offset: Int, ms: Long) = DayTotal(start.plusDays(offset.toLong()).toEpochDay(), ms, 1)

    @Test fun oneColumnPerDayWhenThereIsRoom() {
        val cols = Skyline.build(listOf(d(1, 500)), start, start.plusDays(3), 10, { it.playedMs }, { it == start.plusDays(1) })
        assertEquals(4, cols.size)
        assertEquals(listOf(0L, 500L, 0L, 0L), cols.map { it.value })
        assertEquals(listOf(false, true, false, false), cols.map { it.lit })
    }

    @Test fun bucketsKeepThePeakAndTheLight() {
        val days = (0 until 10).map { d(it, if (it == 7) 9_000L else 100L) }
        val cols = Skyline.build(days, start, start.plusDays(9), 5, { it.playedMs }, { it == start.plusDays(7) })
        assertEquals(5, cols.size) // two days per column
        assertEquals(9_000L, cols[3].value) // days 6 and 7 hold the record: peak, not average
        assertTrue(cols[3].lit)
        assertFalse(cols[2].lit)
    }

    @Test fun emptyWhenRangeIsBackwards() {
        assertTrue(Skyline.build(emptyList(), start, start.minusDays(1), 5, { 0L }, { false }).isEmpty())
    }
}
