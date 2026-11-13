package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import com.oto.stats.data.TrackCount
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    private fun d(day: Int, ms: Long, plays: Int = 1) = DayTotal(LocalDate.of(2026, 11, day).toEpochDay(), ms, plays)

    @Test fun biggestDayAndAllTimeFlag() {
        val period = listOf(d(2, 1_000), d(8, 5_000, 9), d(9, 2_000))
        val all = period + DayTotal(LocalDate.of(2025, 3, 3).toEpochDay(), 9_000, 20)
        val r = Records.compute(period, all, null)
        assertEquals(5_000L, r.biggestDay!!.value)
        assertEquals(LocalDate.of(2026, 11, 8), r.biggestDay!!.day)
        assertFalse(r.biggestDay!!.allTimeBest)      // 9,000 in March 2025 is bigger
        assertEquals(9L, r.mostPlaysDay!!.value)
        assertFalse(r.mostPlaysDay!!.allTimeBest)
        val onlyHere = Records.compute(period, period, null)
        assertTrue(onlyHere.biggestDay!!.allTimeBest)
        assertTrue(onlyHere.anyAllTimeBest)
    }

    @Test fun longestRunNeedsTwoDaysAndLaterWinsTies() {
        val days = listOf(1, 2, 3, 5, 6, 7, 9).map { d(it, 1_000) }
        val run = Records.compute(days, days, null).longestRun!!
        assertEquals(3, run.length)
        assertEquals(LocalDate.of(2026, 11, 5), run.start)
        assertEquals(LocalDate.of(2026, 11, 7), run.end)
        assertNull(Records.compute(listOf(d(1, 1_000), d(3, 1_000)), emptyList(), null).longestRun)
    }

    @Test fun replayedTrackOnlyCountsWithRepeats() {
        assertNull(Records.compute(emptyList(), emptyList(), TrackCount("A", "B", 1)).mostReplayed)
        assertEquals("A", Records.compute(emptyList(), emptyList(), TrackCount("A", "B", 4)).mostReplayed!!.title)
    }

    @Test fun emptyWindowHasNoRecords() {
        val r = Records.compute(emptyList(), emptyList(), null)
        assertNull(r.biggestDay); assertNull(r.mostPlaysDay); assertNull(r.longestRun)
    }
}
