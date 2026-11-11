package com.oto.stats.stats

import com.oto.stats.stats.StatsMath.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMathTest {
    private val hour = 3_600_000L

    @Test fun localDayUsesTheOffsetFromPlayTime() {
        val utc2330 = 23 * hour + 30 * 60_000L // 1970-01-01 23:30 UTC
        assertEquals(0L, StatsMath.localDay(utc2330, 0))
        assertEquals(1L, StatsMath.localDay(utc2330, 330)) // India: already the next day
        assertEquals(0L, StatsMath.localDay(utc2330, -300))
        assertEquals(-1L, StatsMath.localDay(1_000L, -300)) // before epoch rounds down, not toward zero
    }

    @Test fun localHour() {
        assertEquals(5, StatsMath.localHour(0L, 330) )
        assertEquals(23, StatsMath.localHour(0L, -60))
    }

    @Test fun streaks() {
        assertEquals(StatsMath.Streaks(0, 0), StatsMath.streaks(emptyList(), 100))
        assertEquals(StatsMath.Streaks(3, 3), StatsMath.streaks(listOf(98L, 99L, 100L), 100))
        // nothing yet today: streak still alive through yesterday
        assertEquals(StatsMath.Streaks(2, 2), StatsMath.streaks(listOf(98L, 99L), 100))
        // two days ago: broken
        assertEquals(StatsMath.Streaks(0, 2), StatsMath.streaks(listOf(97L, 98L), 100))
        // longest is earlier than current; duplicates ignored
        assertEquals(StatsMath.Streaks(1, 4), StatsMath.streaks(listOf(10L, 11L, 11L, 12L, 13L, 100L), 100))
    }

    @Test fun deltaHidesWhenNothingToCompare() {
        assertNull(StatsMath.delta(50, 0))
        assertEquals(StatsMath.Delta(25, Direction.UP), StatsMath.delta(125, 100))
        assertEquals(StatsMath.Delta(40, Direction.DOWN), StatsMath.delta(60, 100))
        assertEquals(StatsMath.Delta(0, Direction.FLAT), StatsMath.delta(1001, 1000))
    }

    @Test fun patternGate() {
        assertFalse(StatsMath.hasEnoughForPatterns(9, 5))
        assertFalse(StatsMath.hasEnoughForPatterns(50, 2))
        assertTrue(StatsMath.hasEnoughForPatterns(10, 3))
    }

    @Test fun averagesStateTheirDenominator() {
        val a = StatsMath.average(300_000, 30, 10, activeOnly = false)!!
        assertEquals(10_000L, a.perDayMs); assertEquals(30, a.overDays)
        val b = StatsMath.average(300_000, 30, 10, activeOnly = true)!!
        assertEquals(30_000L, b.perDayMs); assertEquals(10, b.overDays)
        assertNull(StatsMath.average(0, 0, 0, false))
    }
}
