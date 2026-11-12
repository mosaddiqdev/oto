package com.oto.stats.data

/** Result rows for the Stats queries. Column aliases in the DAO match these names. */
data class Headline(
    val plays: Int,
    val playedMs: Long,
    val uniqueTracks: Int,
    val fullListens: Int,
    val skips: Int,
)

data class TopTrackRow(
    val trackId: Long,
    val title: String,
    val artist: String,
    val identityKey: String,
    val plays: Int,
    val playedMs: Long,
)

/** [identityKey] is the artist's most played track in the window, used to borrow its cover (we have no artist photos). */
data class TopArtistRow(val artistId: Long, val name: String, val plays: Int, val playedMs: Long, val identityKey: String?)

/** [identityKey] is one of the album's tracks, used to borrow its cover. */
data class TopAlbumRow(
    val album: String,
    val artist: String,
    val identityKey: String,
    val plays: Int,
    val playedMs: Long,
)

data class HourBucket(val hour: Int, val playedMs: Long, val plays: Int)

data class AppShareRow(val sourcePackage: String, val playedMs: Long, val plays: Int)

data class TrackCount(val title: String, val artist: String, val total: Int)
