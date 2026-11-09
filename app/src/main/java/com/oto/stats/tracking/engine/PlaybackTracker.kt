package com.oto.stats.tracking.engine

import java.util.UUID

private const val NO_BASELINE = -1L

/**
 * Turns the events of ONE media player into finished listens.
 *
 * Pure Kotlin with injected clocks, so it can be unit tested without a device.
 *
 * Play time is accumulated from position changes, not wall-clock time:
 * pauses, buffering and seeks do not inflate it.
 */
class PlaybackTracker(
    private val packageName: String,
    private val rules: SessionRules = SessionRules(),
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
    private val onFinished: (ListenResult) -> Unit,
) {
    private class Session(val id: String, val startedAtMs: Long) {
        var playedMs = 0L
        var baselinePositionMs = NO_BASELINE
        var baselineElapsedMs = 0L
        var lastActiveEpochMs = startedAtMs
        var pauseCount = 0
        var seekCount = 0
    }

    private var meta: TrackMeta? = null
    private var countable = false
    private var state = PlayState.STOPPED
    private var session: Session? = null

    /** The player reported (new) metadata. */
    fun onMetadata(newMeta: TrackMeta?, clock: Clock) {
        if (newMeta == null || newMeta.title.isBlank()) return

        val current = meta
        if (current != null && sameTrack(current, newMeta)) {
            // Same track with extra detail (duration or artist arriving late).
            val merged = merge(current, newMeta)
            meta = merged
            countable = ContentFilter.isCountable(merged, packageName, rules)
            if (!countable) closeSession(clock)
            openSessionIfReady(clock)
            return
        }

        // A different track: finish the old one with the old metadata first.
        closeSession(clock)
        meta = newMeta
        countable = ContentFilter.isCountable(newMeta, packageName, rules)
        openSessionIfReady(clock)
    }

    /** The player reported a playback state change. [positionMs] is the current position estimate. */
    fun onPlaybackState(newState: PlayState, positionMs: Long, speed: Float, clock: Clock) {
        val previous = state
        state = newState
        when (newState) {
            PlayState.PLAYING -> {
                openSessionIfReady(clock)
                val s = session ?: return
                if (previous == PlayState.PLAYING) {
                    sample(s, positionMs, speed, clock)
                } else {
                    // Starting or resuming: re-baseline so the gap is never counted.
                    s.baselinePositionMs = positionMs
                    s.baselineElapsedMs = clock.elapsedMs
                    s.lastActiveEpochMs = clock.epochMs
                }
            }
            PlayState.PAUSED -> {
                val s = session
                if (s != null && previous == PlayState.PLAYING) {
                    sample(s, positionMs, speed, clock)
                    s.pauseCount++
                    s.baselinePositionMs = NO_BASELINE
                }
            }
            PlayState.STOPPED -> closeSession(clock)
        }
    }

    /** Periodic position sample while playing. */
    fun onTick(positionMs: Long, speed: Float, clock: Clock) {
        if (state != PlayState.PLAYING) return
        val s = session ?: return
        sample(s, positionMs, speed, clock)
    }

    /** The player went away: finish whatever is open. */
    fun shutdown(clock: Clock) {
        closeSession(clock)
        state = PlayState.STOPPED
    }

    fun snapshot(): NowPlayingSnapshot? {
        val m = meta ?: return null
        return NowPlayingSnapshot(packageName, m, state, session?.playedMs ?: 0L, countable)
    }

    private fun openSessionIfReady(clock: Clock) {
        if (session == null && state == PlayState.PLAYING && countable && meta != null) {
            session = Session(newSessionId(), clock.epochMs)
        }
    }

    private fun sample(s: Session, positionMs: Long, speed: Float, clock: Clock) {
        if (s.baselinePositionMs == NO_BASELINE) {
            s.baselinePositionMs = positionMs
            s.baselineElapsedMs = clock.elapsedMs
            s.lastActiveEpochMs = clock.epochMs
            return
        }

        val priorPosition = s.baselinePositionMs
        val delta = positionMs - priorPosition
        val wallDelta = clock.elapsedMs - s.baselineElapsedMs

        when {
            delta > 0 -> {
                val allowed = (wallDelta * maxOf(speed, 1f)).toLong() + rules.seekToleranceMs
                if (delta <= allowed) {
                    s.playedMs += minOf(delta, rules.maxSingleStepMs)
                } else {
                    s.seekCount++ // jumped forward
                }
            }
            delta < 0 -> {
                val restarted = positionMs <= rules.restartWindowMs &&
                    priorPosition >= rules.restartMinPriorPositionMs
                if (restarted && isReplayWorthy(s)) {
                    // Looped or replayed: close this play and start a new one.
                    finalize(s, s.lastActiveEpochMs)
                    session = Session(newSessionId(), clock.epochMs).also {
                        it.baselinePositionMs = positionMs
                        it.baselineElapsedMs = clock.elapsedMs
                    }
                    return
                }
                s.seekCount++ // jumped backward
            }
            else -> Unit // no progress (buffering or frozen)
        }

        s.baselinePositionMs = positionMs
        s.baselineElapsedMs = clock.elapsedMs
        s.lastActiveEpochMs = clock.epochMs
    }

    private fun isReplayWorthy(s: Session): Boolean {
        val known = meta?.durationMs?.takeIf { it > 0 }
        val duration = known ?: rules.fallbackDurationMs
        return s.playedMs >= rules.requiredPlayMs(known) &&
            s.playedMs >= duration * rules.replayMinFraction
    }

    private fun closeSession(@Suppress("UNUSED_PARAMETER") clock: Clock) {
        val s = session ?: return
        session = null
        finalize(s, s.lastActiveEpochMs)
    }

    private fun finalize(s: Session, endedAtMs: Long) {
        val m = meta ?: return
        if (!countable) return

        val known = m.durationMs?.takeIf { it > 0 }
        val duration = known ?: rules.fallbackDurationMs
        val played = minOf(s.playedMs, (duration * rules.maxPlayedMultiple).toLong())
        if (played < rules.requiredPlayMs(known)) return

        val completion = ((played * 100) / duration).coerceIn(0L, 100L).toInt()
        val skipped = played.toDouble() < duration * rules.skipBelowFraction

        onFinished(
            ListenResult(
                sessionId = s.id,
                packageName = packageName,
                meta = m,
                startedAtMs = s.startedAtMs,
                endedAtMs = maxOf(endedAtMs, s.startedAtMs),
                playedMs = played,
                durationMs = duration,
                durationEstimated = known == null,
                completionPct = completion,
                skipped = skipped,
                pauseCount = s.pauseCount,
                seekCount = s.seekCount,
            ),
        )
    }

    private fun sameTrack(a: TrackMeta, b: TrackMeta): Boolean {
        if (Normalize.key(a.title) != Normalize.key(b.title)) return false
        return Normalize.isUnknown(a.artist) ||
            Normalize.isUnknown(b.artist) ||
            Normalize.artistKey(a.artist) == Normalize.artistKey(b.artist)
    }

    private fun merge(old: TrackMeta, new: TrackMeta): TrackMeta = TrackMeta(
        title = new.title,
        artist = if (Normalize.isUnknown(new.artist)) old.artist else new.artist,
        album = new.album ?: old.album,
        durationMs = new.durationMs?.takeIf { it > 0 } ?: old.durationMs,
    )
}
