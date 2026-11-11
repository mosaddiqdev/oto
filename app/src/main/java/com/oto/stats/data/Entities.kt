package com.oto.stats.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
)

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["identityKey"], unique = true)],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** The artist text exactly as the player reported it. */
    val artistText: String,
    val album: String?,
    val durationMs: Long?,
    /** Normalised "title|artist" used to recognise the same track again. */
    val identityKey: String,
    val createdAt: Long,
)

@Entity(
    tableName = "track_artists",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("artistId")],
)
data class TrackArtistEntity(
    val trackId: Long,
    val artistId: Long,
    /** 0 is the primary artist. */
    val position: Int,
)

@Entity(
    tableName = "listening_events",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("trackId"),
        Index("startedAt"),
        Index(value = ["sessionId"], unique = true),
    ],
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val playedMs: Long,
    val durationMs: Long,
    val durationEstimated: Boolean,
    val completionPct: Int,
    val sourcePackage: String,
    val skipped: Boolean,
    val pauseCount: Int,
    val seekCount: Int,
    val sessionId: String,
    /** UTC offset when the play happened, so travelling does not rewrite past days. */
    val tzOffsetMinutes: Int,
)

/** Points at an already saved listen, so progress can update it instead of adding a duplicate. */
data class EventRef(
    val id: Long,
    val trackId: Long,
)

/** Flat row for the recent-plays list. */
data class EventRow(
    val id: Long,
    val title: String,
    val artist: String,
    val sourcePackage: String,
    val playedMs: Long,
    val completionPct: Int,
    val skipped: Boolean,
    val startedAt: Long,
    val identityKey: String,
    val sessionId: String,
)

/** Listening time and number of listens on one local calendar day (days since 1970-01-01). */
data class DayTotal(
    val day: Long,
    val playedMs: Long,
    val plays: Int,
)

data class Totals(
    val plays: Int,
    val totalPlayedMs: Long,
    val uniqueTracks: Int,
)
