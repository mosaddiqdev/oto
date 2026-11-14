package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackInsightsTest {
    private fun d(day: Long, plays: Int) = DayTotal(day, plays * 200_000L, plays)

    @Test fun findsPeakHourRunAndDays() {
        val daily = listOf(d(10, 1), d(11, 4), d(12, 1), d(20, 2))
        val hours = MutableList(24) { 0 }.also { it[21] = 5; it[8] = 2 }
        val i = TrackInsights.of(daily, hours)
        assertEquals(21, i.favouriteHour)
        assertEquals(4, i.daysPlayed)
        assertEquals(11L, i.peakDay?.day)
        assertEquals(3, i.run?.first)
    }

    @Test fun emptyHasNothing() {
        val i = TrackInsights.of(emptyList(), List(24) { 0 })
        assertNull(i.favouriteHour)
        assertNull(i.peakDay)
        assertNull(i.run)
        assertEquals(0, i.daysPlayed)
    }

    @Test fun peakTieGoesToTheLaterDayAndHourTieToTheEarlierHour() {
        val i = TrackInsights.of(listOf(d(5, 3), d(9, 3)), MutableList(24) { 0 }.also { it[7] = 2; it[19] = 2 })
        assertEquals(9L, i.peakDay?.day)
        assertEquals(7, i.favouriteHour)
    }
}
