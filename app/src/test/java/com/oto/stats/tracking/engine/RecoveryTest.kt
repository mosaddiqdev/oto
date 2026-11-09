package com.oto.stats.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryTest {

    private val results = mutableListOf<ListenResult>()
    private var elapsed = 0L
    private val epochBase = 1_700_000_000_000L
    private val pkg = "com.example.player"
    private val song = TrackMeta("Song", "Artist", "Album", 200_000)

    private fun clock() = Clock(elapsed, epochBase + elapsed)

    private fun tracker() = PlaybackTracker(pkg, newSessionId = { "new" }) { results += it }

    private fun open(
        played: Long = 60_000,
        lastActive: Long = epochBase,
        meta: TrackMeta = song,
    ) = OpenSession("saved-1", pkg, meta, epochBase - 70_000, lastActive, played, 1, 2)

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
    fun restoredSessionContinuesWhenSameTrackResumes() {
        val t = tracker()
        t.restore(open())
        t.onMetadata(song, clock())
        t.onPlaybackState(PlayState.PLAYING, 70_000, 1f, clock())
        t.play(30, 70_000)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

        assertEquals(1, results.size)
        assertEquals("saved-1", results[0].sessionId)
        assertEquals(90_000L, results[0].playedMs)
        assertEquals(1, results[0].pauseCount)
        assertEquals(2, results[0].seekCount)
    }

    @Test
    fun restoredSessionIsSavedWhenADifferentTrackPlays() {
        val t = tracker()
        t.restore(open())
        t.onMetadata(TrackMeta("Other", "Someone", null, 180_000), clock())

        assertEquals(1, results.size)
        assertEquals("saved-1", results[0].sessionId)
        assertEquals(60_000L, results[0].playedMs)
        assertEquals("Song", results[0].meta.title)
    }

    @Test
    fun restoredSessionIsSavedWhenThePlayerIsStopped() {
        val t = tracker()
        t.restore(open())
        t.onMetadata(song, clock())
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

        assertEquals(1, results.size)
        assertEquals(60_000L, results[0].playedMs)
    }

    @Test
    fun restoreIgnoresNonMusic() {
        val t = tracker()
        t.restore(open(meta = TrackMeta("Advertisement", "Spotify", null, 30_000)))
        assertNull(t.openSession())
    }

    @Test
    fun openSessionExposesProgress() {
        val t = tracker()
        t.onMetadata(song, clock())
        t.onPlaybackState(PlayState.PLAYING, 0, 1f, clock())
        t.play(40, 0)

        val open = t.openSession()
        assertNotNull(open)
        assertEquals("new", open!!.sessionId)
        assertEquals(40_000L, open.playedMs)
        assertEquals("Song", open.meta.title)
    }

    @Test
    fun recoveryBuildsAResultFromAnOpenSession() {
        val result = SessionRecovery.toResult(open(), epochBase + 1_000)
        assertNotNull(result)
        assertEquals(60_000L, result!!.playedMs)
        assertEquals(epochBase, result.endedAtMs)
    }

    @Test
    fun recoveryDropsStaleSessions() {
        val dayAndAHour = 25L * 60 * 60 * 1000
        assertNull(SessionRecovery.toResult(open(), epochBase + dayAndAHour))
    }

    @Test
    fun recoveryDropsSessionsThatWereTooShort() {
        assertNull(SessionRecovery.toResult(open(played = 10_000), epochBase + 1_000))
    }

    @Test
    fun longGapBetweenTicksStillCountsFully() {
        val t = tracker()
        t.onMetadata(TrackMeta("Long", "Artist", null, 600_000), clock())
        t.onPlaybackState(PlayState.PLAYING, 0, 1f, clock())
        elapsed += 480_000 // screen off, the next position check came 8 minutes late
        t.onTick(480_000, 1f, clock())
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

        assertEquals(1, results.size)
        assertEquals(480_000L, results[0].playedMs)
    }
}
