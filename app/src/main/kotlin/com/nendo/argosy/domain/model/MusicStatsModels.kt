package com.nendo.argosy.domain.model

/**
 * One track in a stats list. [groupKey] is the recording key when known, else [trackKey];
 * [source], [sourceTrackId], [romId] and [romFileId] are enough to rebuild a queue entry, and
 * the title fields are snapshots taken when it was last heard.
 */
data class MusicStatTrack(
    val groupKey: String,
    val trackKey: String,
    val recordingKey: String?,
    val source: String,
    val sourceAccountKey: String,
    val sourceTrackId: String,
    val albumKey: String?,
    val romId: Long?,
    val romFileId: Long?,
    val title: String,
    val artistCredit: String?,
    val albumTitle: String?,
    val trackGameTitle: String?,
    val durationMs: Long,
    val listenedMs: Long,
    val playCount: Int,
    val lastListenedAt: Long
)

data class MusicStatAlbum(
    val albumKey: String,
    val source: String,
    val romId: Long?,
    val title: String?,
    val artistCredit: String?,
    val trackGameTitle: String?,
    val listenedMs: Long,
    val playCount: Int,
    val lastListenedAt: Long
)

data class MusicStatArtist(
    val artistKey: String,
    val name: String,
    val listenedMs: Long,
    val playCount: Int,
    val lastListenedAt: Long
)

data class MusicMonthTotals(
    val yearMonth: Int,
    val listenedMs: Long,
    val playCount: Int,
    val trackCount: Int,
    val activeDays: Int
)

data class MusicFlashbackYear(
    val year: Int,
    val listenedMs: Long,
    val playCount: Int,
    val tracks: List<MusicStatTrack>
)

enum class MusicContextTagKind(val token: String) {
    GENRE("genre"),
    FRANCHISE("franchise"),
    COLLECTION("collection");

    companion object {
        fun fromToken(token: String?): MusicContextTagKind? = entries.firstOrNull { it.token == token }
    }
}

/**
 * Tracks heard while one context group was being played. [groupKey] is a game key, a platform
 * slug or a tag key depending on the query; [label] is its display snapshot.
 */
data class MusicPairingGroup(
    val groupKey: String,
    val label: String,
    val listenedMs: Long,
    val tracks: List<MusicStatTrack>
)

/**
 * [seeded] marks a track from the game's own soundtrack that has no listening history yet.
 */
data class MusicRecommendation(
    val track: MusicStatTrack,
    val score: Double,
    val seeded: Boolean
)

data class MusicRecommendations(
    val gameId: Long,
    val seedAlbumKey: String?,
    val tracks: List<MusicRecommendation>
)

/**
 * Where the last listen stopped. [completed] is true when the track played out, and resume
 * then starts the next track at 0.
 */
data class MusicResumePoint(
    val selectionId: String?,
    val albumKey: String,
    val trackKey: String,
    val positionMs: Long,
    val completed: Boolean
)
