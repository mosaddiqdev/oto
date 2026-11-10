package com.oto.stats.tracking.engine

/** All tunable numbers for deciding what counts as a play. */
data class SessionRules(
    /** Minimum accumulated play time for a listen to be saved. */
    val minPlayMs: Long = 25_000,
    /** For tracks shorter than twice [minPlayMs], require this fraction of the track instead. */
    val shortTrackMinFraction: Double = 0.5,
    /** Below this fraction of the track, a listen counts as a skip. */
    val skipBelowFraction: Double = 0.30,
    /** Used when the player does not report a duration. */
    val fallbackDurationMs: Long = 210_000,
    /** Known durations outside this window are treated as not-music (ringtones, podcasts, long mixes). */
    val minTrackDurationMs: Long = 15_000,
    val maxTrackDurationMs: Long = 20 * 60_000,
    /** Slack when comparing position progress with wall-clock progress. */
    val seekToleranceMs: Long = 2_000,
    /** Position at or below this, after being well past it, means the track restarted. */
    val restartWindowMs: Long = 5_000,
    val restartMinPriorPositionMs: Long = 10_000,
    /** A restart counts as a new play only if this much of the track was already played. */
    val replayMinFraction: Double = 0.5,
    /** Safety cap on played time relative to track length. */
    val maxPlayedMultiple: Double = 3.0,
    /**
     * A listen paused (or left stopped) this long is over. Resuming later starts a new listen,
     * so a song paused overnight does not keep last night's start time.
     */
    val pauseTimeoutMs: Long = 30 * 60_000,
) {
    fun requiredPlayMs(knownDurationMs: Long?): Long {
        if (knownDurationMs != null && knownDurationMs > 0 && knownDurationMs < minPlayMs * 2) {
            return (knownDurationMs * shortTrackMinFraction).toLong()
        }
        return minPlayMs
    }
}
