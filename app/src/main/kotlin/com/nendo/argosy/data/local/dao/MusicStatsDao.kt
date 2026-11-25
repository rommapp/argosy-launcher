package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query

private const val TRACK_COLUMNS =
    "COALESCE(l.recordingKey, l.trackKey) AS groupKey, l.trackKey AS trackKey, " +
        "l.recordingKey AS recordingKey, l.source AS source, l.sourceAccountKey AS sourceAccountKey, " +
        "l.sourceTrackId AS sourceTrackId, l.albumKey AS albumKey, l.romId AS romId, " +
        "l.romFileId AS romFileId, l.title AS title, l.artistCredit AS artistCredit, " +
        "l.albumTitle AS albumTitle, l.trackGameTitle AS trackGameTitle, l.durationMs AS durationMs, " +
        "SUM(l.listenedMs) AS totalMs, SUM(l.counted) AS playCount, MAX(l.startedAt) AS lastStartedAt"

/**
 * One aggregated track group. Columns other than the sums come from the group's most recent
 * listen.
 */
data class MusicTrackStatRow(
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
    val totalMs: Long,
    val playCount: Int,
    val lastStartedAt: Long
)

data class MusicAlbumStatRow(
    val albumKey: String,
    val source: String,
    val romId: Long?,
    val albumTitle: String?,
    val artistCredit: String?,
    val trackGameTitle: String?,
    val totalMs: Long,
    val playCount: Int,
    val lastStartedAt: Long
)

data class MusicArtistStatRow(
    val artistKey: String,
    val name: String,
    val totalMs: Long,
    val playCount: Int,
    val lastStartedAt: Long
)

data class MusicMonthTotalsRow(
    val totalMs: Long,
    val playCount: Int,
    val trackCount: Int,
    val activeDays: Int
)

data class MusicFlashbackRow(
    val year: Int,
    @Embedded val track: MusicTrackStatRow
)

data class MusicPairingRow(
    val contextKey: String,
    val contextLabel: String,
    @Embedded val track: MusicTrackStatRow
)

data class MusicContextListenRow(
    val listenId: Long,
    val contextId: Long?,
    val contextGameId: Long?,
    val contextGameKey: String?,
    val contextPlatformSlug: String?,
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
    val counted: Boolean,
    val startedAt: Long,
    val endReason: String?
)

@Dao
interface MusicStatsDao {

    @Query(
        "SELECT $TRACK_COLUMNS FROM music_listens l " +
            "WHERE l.ownerKey = :ownerKey AND l.localDay BETWEEN :fromDay AND :toDay " +
            "GROUP BY groupKey ORDER BY totalMs DESC, lastStartedAt DESC LIMIT :limit"
    )
    suspend fun topTracks(ownerKey: Long, fromDay: Int, toDay: Int, limit: Int): List<MusicTrackStatRow>

    @Query(
        "SELECT l.albumKey AS albumKey, l.source AS source, l.romId AS romId, l.albumTitle AS albumTitle, " +
            "l.artistCredit AS artistCredit, l.trackGameTitle AS trackGameTitle, " +
            "SUM(l.listenedMs) AS totalMs, SUM(l.counted) AS playCount, MAX(l.startedAt) AS lastStartedAt " +
            "FROM music_listens l WHERE l.ownerKey = :ownerKey AND l.albumKey IS NOT NULL " +
            "AND l.localDay BETWEEN :fromDay AND :toDay " +
            "GROUP BY l.albumKey ORDER BY totalMs DESC, lastStartedAt DESC LIMIT :limit"
    )
    suspend fun topAlbums(ownerKey: Long, fromDay: Int, toDay: Int, limit: Int): List<MusicAlbumStatRow>

    @Query(
        "SELECT a.artistKey AS artistKey, a.name AS name, SUM(l.listenedMs) AS totalMs, " +
            "SUM(l.counted) AS playCount, MAX(l.startedAt) AS lastStartedAt " +
            "FROM music_listen_artists a JOIN music_listens l ON l.id = a.listenId " +
            "WHERE a.ownerKey = :ownerKey AND l.ownerKey = :ownerKey " +
            "AND l.localDay BETWEEN :fromDay AND :toDay " +
            "GROUP BY a.artistKey ORDER BY totalMs DESC, lastStartedAt DESC LIMIT :limit"
    )
    suspend fun topArtists(ownerKey: Long, fromDay: Int, toDay: Int, limit: Int): List<MusicArtistStatRow>

    @Query(
        "SELECT COALESCE(SUM(listenedMs), 0) AS totalMs, COALESCE(SUM(counted), 0) AS playCount, " +
            "COUNT(DISTINCT COALESCE(recordingKey, trackKey)) AS trackCount, " +
            "COUNT(DISTINCT localDay) AS activeDays " +
            "FROM music_listens WHERE ownerKey = :ownerKey AND localDay BETWEEN :fromDay AND :toDay"
    )
    suspend fun totals(ownerKey: Long, fromDay: Int, toDay: Int): MusicMonthTotalsRow

    @Query(
        "SELECT l.localDay / 10000 AS year, $TRACK_COLUMNS FROM music_listens l " +
            "WHERE l.ownerKey = :ownerKey AND l.localDay % 10000 = :monthDay " +
            "GROUP BY year, groupKey ORDER BY year DESC, totalMs DESC"
    )
    suspend fun tracksOnDay(ownerKey: Long, monthDay: Int): List<MusicFlashbackRow>

    @Query(
        "SELECT l.localDay / 10000 AS year, $TRACK_COLUMNS FROM music_listens l " +
            "WHERE l.ownerKey = :ownerKey AND (l.localDay / 100) % 100 = :month " +
            "GROUP BY year, groupKey ORDER BY year DESC, totalMs DESC"
    )
    suspend fun tracksInMonth(ownerKey: Long, month: Int): List<MusicFlashbackRow>

    @Query(
        "SELECT c.gameKey AS contextKey, c.gameTitle AS contextLabel, $TRACK_COLUMNS " +
            "FROM music_listens l JOIN music_game_contexts c ON c.id = l.contextId " +
            "WHERE l.ownerKey = :ownerKey AND c.ownerKey = :ownerKey " +
            "GROUP BY c.gameKey, groupKey"
    )
    suspend fun pairingsByGame(ownerKey: Long): List<MusicPairingRow>

    @Query(
        "SELECT c.platformSlug AS contextKey, c.platformSlug AS contextLabel, $TRACK_COLUMNS " +
            "FROM music_listens l JOIN music_game_contexts c ON c.id = l.contextId " +
            "WHERE l.ownerKey = :ownerKey AND c.ownerKey = :ownerKey " +
            "GROUP BY c.platformSlug, groupKey"
    )
    suspend fun pairingsByPlatform(ownerKey: Long): List<MusicPairingRow>

    @Query(
        "SELECT t.tagKey AS contextKey, t.label AS contextLabel, $TRACK_COLUMNS " +
            "FROM music_listens l JOIN music_game_contexts c ON c.id = l.contextId " +
            "JOIN music_game_context_tags t ON t.contextId = c.id " +
            "WHERE l.ownerKey = :ownerKey AND c.ownerKey = :ownerKey AND t.ownerKey = :ownerKey " +
            "AND t.kind = :kind GROUP BY t.tagKey, groupKey"
    )
    suspend fun pairingsByTag(ownerKey: Long, kind: String): List<MusicPairingRow>

    @Query(
        "SELECT l.id AS listenId, l.contextId AS contextId, c.gameId AS contextGameId, " +
            "c.gameKey AS contextGameKey, c.platformSlug AS contextPlatformSlug, l.trackKey AS trackKey, " +
            "l.recordingKey AS recordingKey, l.source AS source, l.sourceAccountKey AS sourceAccountKey, " +
            "l.sourceTrackId AS sourceTrackId, l.albumKey AS albumKey, l.romId AS romId, " +
            "l.romFileId AS romFileId, l.title AS title, l.artistCredit AS artistCredit, " +
            "l.albumTitle AS albumTitle, l.trackGameTitle AS trackGameTitle, l.durationMs AS durationMs, " +
            "l.listenedMs AS listenedMs, l.counted AS counted, l.startedAt AS startedAt, " +
            "l.endReason AS endReason " +
            "FROM music_listens l LEFT JOIN music_game_contexts c ON c.id = l.contextId AND c.ownerKey = :ownerKey " +
            "WHERE l.ownerKey = :ownerKey AND l.durationMs >= :minDurationMs " +
            "AND (l.contextId IS NOT NULL OR l.albumKey = :seedAlbumKey)"
    )
    suspend fun contextListens(ownerKey: Long, minDurationMs: Long, seedAlbumKey: String?): List<MusicContextListenRow>
}
