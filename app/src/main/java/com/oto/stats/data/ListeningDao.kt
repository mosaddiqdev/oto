package com.oto.stats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.oto.stats.tracking.engine.ListenResult
import com.oto.stats.tracking.engine.Normalize
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ListeningDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTrack(track: TrackEntity): Long

    @Query("SELECT id FROM tracks WHERE identityKey = :key")
    abstract suspend fun trackIdByKey(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertArtist(artist: ArtistEntity): Long

    @Query("SELECT id FROM artists WHERE normalizedName = :normalizedName")
    abstract suspend fun artistIdByName(normalizedName: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTrackArtists(links: List<TrackArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEvent(event: ListeningEventEntity): Long

    @Query("SELECT id AS id, trackId AS trackId FROM listening_events WHERE sessionId = :sessionId")
    abstract suspend fun eventBySession(sessionId: String): EventRef?

    @Query(
        """
        UPDATE listening_events
        SET trackId = :trackId,
            endedAt = :endedAt,
            playedMs = :playedMs,
            durationMs = :durationMs,
            durationEstimated = :durationEstimated,
            completionPct = :completionPct,
            skipped = :skipped,
            pauseCount = :pauseCount,
            seekCount = :seekCount
        WHERE id = :id
        """,
    )
    abstract suspend fun updateEvent(
        id: Long,
        trackId: Long,
        endedAt: Long,
        playedMs: Long,
        durationMs: Long,
        durationEstimated: Boolean,
        completionPct: Int,
        skipped: Boolean,
        pauseCount: Int,
        seekCount: Int,
    )

    @Query(
        """
        DELETE FROM tracks
        WHERE id = :trackId
          AND NOT EXISTS (SELECT 1 FROM listening_events WHERE trackId = :trackId)
        """,
    )
    abstract suspend fun deleteTrackIfUnused(trackId: Long)

    /**
     * Saves a listen, or updates it if this session was already saved.
     *
     * A listen is saved as soon as it is long enough to count and then kept up to date while
     * it plays, so it shows up right away and survives the app being killed.
     * Returns true when a new listen was added, false when an existing one was updated.
     */
    @Transaction
    open suspend fun recordListen(
        result: ListenResult,
        artists: List<String>,
        tzOffsetMinutes: Int,
    ): Boolean {
        val key = Normalize.trackIdentity(result.meta.title, result.meta.artist)

        var trackId = insertTrack(
            TrackEntity(
                title = result.meta.title,
                artistText = result.meta.artist,
                album = result.meta.album,
                durationMs = result.meta.durationMs?.takeIf { it > 0 },
                identityKey = key,
                createdAt = result.endedAtMs,
            ),
        )
        if (trackId == -1L) {
            trackId = trackIdByKey(key) ?: return false
        } else {
            val links = ArrayList<TrackArtistEntity>()
            for ((index, name) in artists.withIndex()) {
                val normalized = Normalize.artistKey(name)
                var artistId = insertArtist(ArtistEntity(name = name, normalizedName = normalized))
                if (artistId == -1L) {
                    artistId = artistIdByName(normalized) ?: continue
                }
                links.add(TrackArtistEntity(trackId = trackId, artistId = artistId, position = index))
            }
            insertTrackArtists(links)
        }

        val existing = eventBySession(result.sessionId)
        if (existing == null) {
            val eventId = insertEvent(
                ListeningEventEntity(
                    trackId = trackId,
                    startedAt = result.startedAtMs,
                    endedAt = result.endedAtMs,
                    playedMs = result.playedMs,
                    durationMs = result.durationMs,
                    durationEstimated = result.durationEstimated,
                    completionPct = result.completionPct,
                    sourcePackage = result.packageName,
                    skipped = result.skipped,
                    pauseCount = result.pauseCount,
                    seekCount = result.seekCount,
                    sessionId = result.sessionId,
                    tzOffsetMinutes = tzOffsetMinutes,
                ),
            )
            return eventId != -1L
        }

        updateEvent(
            id = existing.id,
            trackId = trackId,
            endedAt = result.endedAtMs,
            playedMs = result.playedMs,
            durationMs = result.durationMs,
            durationEstimated = result.durationEstimated,
            completionPct = result.completionPct,
            skipped = result.skipped,
            pauseCount = result.pauseCount,
            seekCount = result.seekCount,
        )
        // The artist can arrive late, which changes the track identity. Drop the empty leftover.
        if (existing.trackId != trackId) deleteTrackIfUnused(existing.trackId)
        return false
    }

    @Query(
        """
        SELECT e.id AS id,
               t.title AS title,
               t.artistText AS artist,
               e.sourcePackage AS sourcePackage,
               e.playedMs AS playedMs,
               e.completionPct AS completionPct,
               e.skipped AS skipped,
               e.startedAt AS startedAt,
               t.identityKey AS identityKey,
               e.sessionId AS sessionId
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        ORDER BY e.startedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun recentEvents(limit: Int): Flow<List<EventRow>>

    @Query(
        """
        SELECT ((startedAt + :tzOffsetMs) / 86400000) AS day,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(*) AS plays
        FROM listening_events
        WHERE startedAt >= :sinceMs
        GROUP BY day
        ORDER BY day
        """,
    )
    abstract fun dailyTotals(sinceMs: Long, tzOffsetMs: Long): Flow<List<DayTotal>>

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(playedMs), 0) AS totalPlayedMs,
               COUNT(DISTINCT trackId) AS uniqueTracks
        FROM listening_events
        """,
    )
    abstract fun totals(): Flow<Totals>

    // Demo history is tagged by its session id prefix, so it can be removed cleanly.
    @Query("DELETE FROM listening_events WHERE sessionId LIKE 'demo:%'")
    abstract suspend fun deleteDemoEvents(): Int

    @Query("DELETE FROM tracks WHERE id NOT IN (SELECT trackId FROM listening_events)")
    abstract suspend fun deleteTracksWithoutEvents()

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT artistId FROM track_artists)")
    abstract suspend fun deleteArtistsWithoutTracks()

    @Query("SELECT COUNT(*) FROM listening_events WHERE sessionId LIKE 'demo:%'")
    abstract fun demoEventCount(): Flow<Int>

    // ---- Stats. Every range is [startMs, endMs) in epoch ms. Days and hours use the UTC offset
    // stored with each listen, so travelling never moves an old listen to another day. ----

    /** Changes whenever a listen is added or its play time grows; drives live refresh. */
    @Query("SELECT COUNT(*) + COALESCE(SUM(playedMs), 0) FROM listening_events")
    abstract fun changeMarker(): Flow<Long>

    @Query("SELECT MIN(startedAt) FROM listening_events")
    abstract suspend fun firstEventAt(): Long?

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(DISTINCT trackId) AS uniqueTracks,
               COALESCE(SUM(CASE WHEN completionPct >= 90 THEN 1 ELSE 0 END), 0) AS fullListens,
               COALESCE(SUM(CASE WHEN skipped THEN 1 ELSE 0 END), 0) AS skips
        FROM listening_events
        WHERE startedAt >= :startMs AND startedAt < :endMs
        """,
    )
    abstract suspend fun headline(startMs: Long, endMs: Long): Headline

    @Query(
        """
        SELECT COUNT(DISTINCT ta.artistId)
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        JOIN artists a ON a.id = ta.artistId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs AND a.normalizedName != :unknownKey
        """,
    )
    abstract suspend fun uniqueArtists(startMs: Long, endMs: Long, unknownKey: String): Int

    @Query(
        """
        SELECT ((startedAt + tzOffsetMinutes * 60000) / 86400000) AS day,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(*) AS plays
        FROM listening_events
        WHERE startedAt >= :startMs AND startedAt < :endMs
        GROUP BY day
        ORDER BY day
        """,
    )
    abstract suspend fun dailyInRange(startMs: Long, endMs: Long): List<DayTotal>

    /** Every local day that has any listening, for streaks (all time, not just the shown period). */
    @Query(
        """
        SELECT DISTINCT ((startedAt + tzOffsetMinutes * 60000) / 86400000) AS day
        FROM listening_events
        ORDER BY day
        """,
    )
    abstract suspend fun activeDays(): List<Long>

    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artistText AS artist, t.identityKey AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs
        GROUP BY t.id
        ORDER BY CASE WHEN :byTime = 1 THEN SUM(e.playedMs) ELSE COUNT(*) END DESC,
                 COUNT(*) DESC, t.title
        LIMIT :limit
        """,
    )
    abstract suspend fun topTracks(startMs: Long, endMs: Long, byTime: Boolean, limit: Int): List<TopTrackRow>

    /** A track with several artists counts in full for each of them. Unknown Artist is left out. */
    @Query(
        """
        SELECT a.id AS artistId, a.name AS name,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs,
               (SELECT t2.identityKey
                  FROM listening_events e2
                  JOIN tracks t2 ON t2.id = e2.trackId
                  JOIN track_artists ta2 ON ta2.trackId = t2.id
                 WHERE ta2.artistId = a.id AND e2.startedAt >= :startMs AND e2.startedAt < :endMs
                 GROUP BY t2.id
                 ORDER BY SUM(e2.playedMs) DESC
                 LIMIT 1) AS identityKey
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        JOIN artists a ON a.id = ta.artistId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs AND a.normalizedName != :unknownKey
        GROUP BY a.id
        ORDER BY CASE WHEN :byTime = 1 THEN SUM(e.playedMs) ELSE COUNT(*) END DESC,
                 COUNT(*) DESC, a.name
        LIMIT :limit
        """,
    )
    abstract suspend fun topArtists(
        startMs: Long,
        endMs: Long,
        byTime: Boolean,
        unknownKey: String,
        limit: Int,
    ): List<TopArtistRow>

    /** Albums are the album text on the track, grouped with the primary artist. No album, no row. */
    @Query(
        """
        SELECT MIN(t.album) AS album, a.name AS artist, MIN(t.identityKey) AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        JOIN track_artists ta ON ta.trackId = t.id AND ta.position = 0
        JOIN artists a ON a.id = ta.artistId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs
          AND t.album IS NOT NULL AND TRIM(t.album) != ''
        GROUP BY LOWER(TRIM(t.album)), a.id
        ORDER BY CASE WHEN :byTime = 1 THEN SUM(e.playedMs) ELSE COUNT(*) END DESC,
                 COUNT(*) DESC, album
        LIMIT :limit
        """,
    )
    abstract suspend fun topAlbums(startMs: Long, endMs: Long, byTime: Boolean, limit: Int): List<TopAlbumRow>

    @Query(
        """
        SELECT ((startedAt + tzOffsetMinutes * 60000) % 86400000) / 3600000 AS hour,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(*) AS plays
        FROM listening_events
        WHERE startedAt >= :startMs AND startedAt < :endMs
        GROUP BY hour
        ORDER BY hour
        """,
    )
    abstract suspend fun hourly(startMs: Long, endMs: Long): List<HourBucket>

    @Query(
        """
        SELECT sourcePackage AS sourcePackage,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(*) AS plays
        FROM listening_events
        WHERE startedAt >= :startMs AND startedAt < :endMs
        GROUP BY sourcePackage
        ORDER BY playedMs DESC
        """,
    )
    abstract suspend fun appShare(startMs: Long, endMs: Long): List<AppShareRow>

    /**
     * Replays: listens of a track beyond its first on the same local day.
     * One day is the window so a song you love on repeat counts, a song you hear weekly does not.
     */
    @Query(
        """
        SELECT COALESCE(SUM(c - 1), 0) FROM (
            SELECT COUNT(*) AS c
            FROM listening_events
            WHERE startedAt >= :startMs AND startedAt < :endMs
            GROUP BY trackId, ((startedAt + tzOffsetMinutes * 60000) / 86400000)
        )
        """,
    )
    abstract suspend fun replayCount(startMs: Long, endMs: Long): Int

    @Query(
        """
        SELECT t.title AS title, t.artistText AS artist, SUM(c - 1) AS total
        FROM (
            SELECT trackId, COUNT(*) AS c
            FROM listening_events
            WHERE startedAt >= :startMs AND startedAt < :endMs
            GROUP BY trackId, ((startedAt + tzOffsetMinutes * 60000) / 86400000)
        ) x
        JOIN tracks t ON t.id = x.trackId
        GROUP BY x.trackId
        HAVING SUM(c - 1) > 0
        ORDER BY total DESC, t.title
        LIMIT 1
        """,
    )
    abstract suspend fun mostReplayed(startMs: Long, endMs: Long): TrackCount?

    @Query(
        """
        SELECT t.title AS title, t.artistText AS artist, COUNT(*) AS total
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs AND e.skipped = 1
        GROUP BY t.id
        ORDER BY total DESC, t.title
        LIMIT 1
        """,
    )
    abstract suspend fun mostSkipped(startMs: Long, endMs: Long): TrackCount?

    /** How many different tracks of one artist were played in the range. */
    @Query(
        """
        SELECT COUNT(DISTINCT e.trackId)
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        """,
    )
    abstract suspend fun artistTrackCount(artistId: Long, startMs: Long, endMs: Long): Int

    /** The most played track of one artist in the range; its cover stands in for the artist. */
    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artistText AS artist, t.identityKey AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        JOIN track_artists ta ON ta.trackId = t.id
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        GROUP BY t.id
        ORDER BY plays DESC, playedMs DESC
        LIMIT 1
        """,
    )
    abstract suspend fun artistTopTrack(artistId: Long, startMs: Long, endMs: Long): TopTrackRow?

    /** On how many different local days a track was played. */
    @Query(
        """
        SELECT COUNT(DISTINCT ((startedAt + tzOffsetMinutes * 60000) / 86400000))
        FROM listening_events
        WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs
        """,
    )
    abstract suspend fun trackActiveDays(trackId: Long, startMs: Long, endMs: Long): Int

    /** Tracks whose very first play was inside the range, i.e. new to the listener. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT trackId FROM listening_events
            GROUP BY trackId
            HAVING MIN(startedAt) >= :startMs AND MIN(startedAt) < :endMs
        )
        """,
    )
    abstract suspend fun newTrackCount(startMs: Long, endMs: Long): Int

    /** Your best finds: tracks whose very first play was in the range, ranked by how much you played them in it. */
    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artistText AS artist, t.identityKey AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        WHERE e.startedAt >= :startMs AND e.startedAt < :endMs
          AND t.id IN (
              SELECT trackId FROM listening_events
              GROUP BY trackId
              HAVING MIN(startedAt) >= :startMs AND MIN(startedAt) < :endMs
          )
        GROUP BY t.id
        ORDER BY SUM(e.playedMs) DESC, COUNT(*) DESC, t.title
        LIMIT :limit
        """,
    )
    abstract suspend fun newTracksTop(startMs: Long, endMs: Long, limit: Int): List<TopTrackRow>

    /** Artists you heard for the first time inside the range. Unknown Artist is left out. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT ta.artistId
            FROM track_artists ta
            JOIN artists a ON a.id = ta.artistId
            JOIN listening_events e ON e.trackId = ta.trackId
            WHERE a.normalizedName != :unknownKey
            GROUP BY ta.artistId
            HAVING MIN(e.startedAt) >= :startMs AND MIN(e.startedAt) < :endMs
        )
        """,
    )
    abstract suspend fun newArtistCount(startMs: Long, endMs: Long, unknownKey: String): Int

    // ---- One track (detail page) ----

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract suspend fun trackById(id: Long): TrackEntity?

    /** The artists credited on a track, primary first. */
    @Query(
        """
        SELECT a.id AS artistId, a.name AS name
        FROM track_artists ta JOIN artists a ON a.id = ta.artistId
        WHERE ta.trackId = :trackId
        ORDER BY ta.position
        """,
    )
    abstract suspend fun artistsOfTrack(trackId: Long): List<ArtistRef>

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COALESCE(SUM(CASE WHEN skipped THEN 1 ELSE 0 END), 0) AS skips,
               COALESCE(AVG(completionPct), 0) AS avgCompletion,
               COALESCE(MIN(startedAt), 0) AS firstAt,
               COALESCE(MAX(startedAt), 0) AS lastAt
        FROM listening_events
        WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs
        """,
    )
    abstract suspend fun trackStats(trackId: Long, startMs: Long, endMs: Long): TrackStats

    @Query(
        """
        SELECT ((startedAt + tzOffsetMinutes * 60000) / 86400000) AS day,
               COALESCE(SUM(playedMs), 0) AS playedMs,
               COUNT(*) AS plays
        FROM listening_events
        WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs
        GROUP BY day
        ORDER BY day
        """,
    )
    abstract suspend fun trackDaily(trackId: Long, startMs: Long, endMs: Long): List<DayTotal>

    @Query(
        """
        SELECT CAST(((startedAt + tzOffsetMinutes * 60000) % 86400000) / 3600000 AS INTEGER) AS hour,
               COALESCE(SUM(playedMs), 0) AS playedMs, COUNT(*) AS plays
        FROM listening_events
        WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs
        GROUP BY hour
        """,
    )
    abstract suspend fun trackHourly(trackId: Long, startMs: Long, endMs: Long): List<HourBucket>

    @Query(
        """
        SELECT startedAt, playedMs, completionPct, skipped, sourcePackage, tzOffsetMinutes
        FROM listening_events
        WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs
        ORDER BY startedAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun trackListens(trackId: Long, startMs: Long, endMs: Long, limit: Int): List<TrackListen>

    /** Where this track stands among all tracks in the same window, by plays: 1 is the most played. */
    @Query(
        """
        SELECT COUNT(*) + 1 FROM (
            SELECT trackId FROM listening_events
            WHERE startedAt >= :startMs AND startedAt < :endMs
            GROUP BY trackId
            HAVING COUNT(*) > (SELECT COUNT(*) FROM listening_events WHERE trackId = :trackId AND startedAt >= :startMs AND startedAt < :endMs)
        )
        """,
    )
    abstract suspend fun trackRank(trackId: Long, startMs: Long, endMs: Long): Int

    @Query("SELECT COUNT(DISTINCT trackId) FROM listening_events WHERE startedAt >= :startMs AND startedAt < :endMs")
    abstract suspend fun tracksPlayed(startMs: Long, endMs: Long): Int

    // ---- One artist (detail page) ----

    @Query("SELECT * FROM artists WHERE id = :id")
    abstract suspend fun artistById(id: Long): ArtistEntity?

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(e.playedMs), 0) AS playedMs,
               COUNT(DISTINCT e.trackId) AS tracks,
               COALESCE(MIN(e.startedAt), 0) AS firstAt,
               COALESCE(MAX(e.startedAt), 0) AS lastAt
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        """,
    )
    abstract suspend fun artistStats(artistId: Long, startMs: Long, endMs: Long): ArtistStats

    @Query(
        """
        SELECT ((e.startedAt + e.tzOffsetMinutes * 60000) / 86400000) AS day,
               COALESCE(SUM(e.playedMs), 0) AS playedMs, COUNT(*) AS plays
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        GROUP BY day
        ORDER BY day
        """,
    )
    abstract suspend fun artistDaily(artistId: Long, startMs: Long, endMs: Long): List<DayTotal>

    @Query(
        """
        SELECT CAST(((e.startedAt + e.tzOffsetMinutes * 60000) % 86400000) / 3600000 AS INTEGER) AS hour,
               COALESCE(SUM(e.playedMs), 0) AS playedMs, COUNT(*) AS plays
        FROM listening_events e
        JOIN track_artists ta ON ta.trackId = e.trackId
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        GROUP BY hour
        """,
    )
    abstract suspend fun artistHourly(artistId: Long, startMs: Long, endMs: Long): List<HourBucket>

    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artistText AS artist, t.identityKey AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        JOIN track_artists ta ON ta.trackId = t.id
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
        GROUP BY t.id
        ORDER BY COUNT(*) DESC, SUM(e.playedMs) DESC, t.title
        LIMIT :limit
        """,
    )
    abstract suspend fun artistTopTracks(artistId: Long, startMs: Long, endMs: Long, limit: Int): List<TopTrackRow>

    @Query(
        """
        SELECT MIN(t.album) AS album, (SELECT name FROM artists WHERE id = :artistId) AS artist, MIN(t.identityKey) AS identityKey,
               COUNT(*) AS plays, COALESCE(SUM(e.playedMs), 0) AS playedMs
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        JOIN track_artists ta ON ta.trackId = t.id
        WHERE ta.artistId = :artistId AND e.startedAt >= :startMs AND e.startedAt < :endMs
          AND t.album IS NOT NULL AND TRIM(t.album) != ''
        GROUP BY LOWER(TRIM(t.album))
        ORDER BY COUNT(*) DESC, SUM(e.playedMs) DESC, album
        LIMIT :limit
        """,
    )
    abstract suspend fun artistTopAlbums(artistId: Long, startMs: Long, endMs: Long, limit: Int): List<TopAlbumRow>

    /** Where this artist stands among all artists in the window, by plays: 1 is the most played. */
    @Query(
        """
        SELECT COUNT(*) + 1 FROM (
            SELECT ta.artistId FROM listening_events e
            JOIN track_artists ta ON ta.trackId = e.trackId
            JOIN artists a ON a.id = ta.artistId
            WHERE e.startedAt >= :startMs AND e.startedAt < :endMs AND a.normalizedName != :unknownKey
            GROUP BY ta.artistId
            HAVING COUNT(*) > (
                SELECT COUNT(*) FROM listening_events e2
                JOIN track_artists ta2 ON ta2.trackId = e2.trackId
                WHERE ta2.artistId = :artistId AND e2.startedAt >= :startMs AND e2.startedAt < :endMs
            )
        )
        """,
    )
    abstract suspend fun artistRank(artistId: Long, startMs: Long, endMs: Long, unknownKey: String): Int
}
