package com.oto.stats.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoHistoryTest {
    private val now = 1_794_000_000_000L // fixed instant
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun sameInputsGiveSameHistory() {
        val a = DemoHistory.generate(DemoPreset.MONTHS_3, now, zone)
        val b = DemoHistory.generate(DemoPreset.MONTHS_3, now, zone)
        assertEquals(a.size, b.size)
        assertEquals(a.first().result.meta, b.first().result.meta)
        assertEquals(a.last().result.startedAtMs, b.last().result.startedAtMs)
    }

    @Test fun nothingInTheFutureAndAllTagged() {
        val all = DemoHistory.generate(DemoPreset.YEARS_3, now, zone)
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.result.endedAtMs < now })
        assertTrue(all.all { it.result.sessionId.startsWith(DEMO_SESSION_PREFIX) })
        assertEquals(all.size, all.map { it.result.sessionId }.toSet().size)
    }

    @Test fun skipsAreConsistentAndPresetsScale() {
        val week = DemoHistory.generate(DemoPreset.WEEK, now, zone)
        val years = DemoHistory.generate(DemoPreset.YEARS_3, now, zone)
        assertTrue(years.size > week.size * 50)
        assertTrue(years.any { it.result.skipped })
        assertTrue(years.filter { it.result.skipped }.all { it.result.completionPct < 40 })
        assertTrue(years.any { it.tzOffsetMinutes == 540 }) // the trip
    }
}
