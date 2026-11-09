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

    /**
     * Saves one finished listen: finds or creates the track and its artists, then the event.
     * Returns false if the session was already saved (duplicate).
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

    @Query(
        """
        SELECT e.id AS id,
               t.title AS title,
               t.artistText AS artist,
               e.sourcePackage AS sourcePackage,
               e.playedMs AS playedMs,
               e.completionPct AS completionPct,
               e.skipped AS skipped,
               e.startedAt AS startedAt
        FROM listening_events e
        JOIN tracks t ON t.id = e.trackId
        ORDER BY e.startedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun recentEvents(limit: Int): Flow<List<EventRow>>

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(playedMs), 0) AS totalPlayedMs,
               COUNT(DISTINCT trackId) AS uniqueTracks
        FROM listening_events
        """,
    )
    abstract fun totals(): Flow<Totals>
}
