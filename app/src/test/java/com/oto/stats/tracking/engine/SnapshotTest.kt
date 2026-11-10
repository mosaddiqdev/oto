package com.oto.stats.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {

    private val results = mutableListOf<ListenResult>()
    private val discards = mutableListOf<String>()
    private var elapsed = 0L
    private val epochBase = 1_700_000_000_000L
    private val song = TrackMeta("Song", "Artist", "Album", 200_000)

    private fun clock() = Clock(elapsed, epochBase + elapsed)

    private fun tracker() = PlaybackTracker(
        "com.example.player",
        newSessionId = { "s1" },
        onDiscarded = { discards += it },
    ) { results += it }

    private fun PlaybackTracker.start() {
        onMetadata(song, clock())
        onPlaybackState(PlayState.PLAYING, 0, 1f, clock())
    }

    private fun PlaybackTracker.play(seconds: Int, startPos: Long): Long {
        var pos = startPos
        var remaining = seconds
        while (remaining > 0) {
            val step = minOf(5, remaining)
            elapsed += step * 1000L
            pos += step * 1000L
            onTick(pos, 1f, clock())
            remaining -= step
        }
        return pos
    }

    @Test
    fun noSnapshotUntilTheListenCounts() {
        val t = tracker()
        t.start()
        t.play(10, 0)
        assertNull(t.snapshotResult())
    }

    @Test
    fun snapshotAppearsOnceLongEnoughAndKeepsTheSessionId() {
        val t = tracker()
        t.start()
        t.play(30, 0)
        val snap = t.snapshotResult()

        assertNotNull(snap)
        assertEquals("s1", snap!!.sessionId)
        assertEquals(30_000L, snap.playedMs)
        assertTrue(results.isEmpty())

        t.play(20, 30_000)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())
        assertEquals(1, results.size)
        assertEquals("s1", results[0].sessionId)
        assertEquals(50_000L, results[0].playedMs)
    }

    @Test
    fun pausedListenCanBeSavedWithoutFinishingIt() {
        val t = tracker()
        t.start()
        val pos = t.play(40, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())

        val snap = t.snapshotResult()
        assertNotNull(snap)
        assertEquals(40_000L, snap!!.playedMs)
        assertEquals(1, snap.pauseCount)
        assertTrue(results.isEmpty())
    }

    @Test
    fun noSnapshotAfterTheListenEnded() {
        val t = tracker()
        t.start()
        t.play(60, 0)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())
        assertNull(t.snapshotResult())
    }

    @Test
    fun tooShortListensAreReportedAsDiscarded() {
        val t = tracker()
        t.start()
        t.play(10, 0)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

        assertTrue(results.isEmpty())
        assertEquals(1, discards.size)
        assertTrue(discards[0].contains("Song"))
    }
}
