package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MusicListenArtistEntity
import com.nendo.argosy.data.local.entity.MusicListenEntity

@Dao
interface MusicListenDao {

    @Insert
    suspend fun insert(listen: MusicListenEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(rows: List<MusicListenArtistEntity>)

    @Transaction
    suspend fun insertWithArtists(listen: MusicListenEntity, artists: List<MusicListenArtistEntity>): Long {
        val id = insert(listen)
        if (artists.isNotEmpty()) insertArtists(artists.map { it.copy(listenId = id) })
        return id
    }

    @Query(
        "UPDATE music_listens SET endedAt = :endedAt, listenedMs = :listenedMs, " +
            "endPositionMs = :endPositionMs, durationMs = :durationMs, counted = :counted, " +
            "endReason = COALESCE(:endReason, endReason), contextId = COALESCE(contextId, :contextId) " +
            "WHERE ownerKey = :ownerKey AND id = :id"
    )
    suspend fun updateProgress(
        ownerKey: Long,
        id: Long,
        endedAt: Long,
        listenedMs: Long,
        endPositionMs: Long,
        durationMs: Long,
        counted: Boolean,
        endReason: String?,
        contextId: Long?
    )

    @Query(
        "UPDATE music_listens SET scrobbleState = '${MusicListenEntity.SCROBBLE_PENDING}' " +
            "WHERE ownerKey = :ownerKey AND id = :id AND scrobbleState = '${MusicListenEntity.SCROBBLE_NONE}'"
    )
    suspend fun markScrobblePending(ownerKey: Long, id: Long)

    @Query(
        "UPDATE music_listens SET scrobbleState = '${MusicListenEntity.SCROBBLE_SENT}', scrobbledAt = :sentAt " +
            "WHERE ownerKey = :ownerKey AND id = :id"
    )
    suspend fun markScrobbleSent(ownerKey: Long, id: Long, sentAt: Long)

    @Query(
        "SELECT * FROM music_listens WHERE ownerKey = :ownerKey AND source = :source " +
            "AND sourceAccountKey = :sourceAccountKey " +
            "AND scrobbleState = '${MusicListenEntity.SCROBBLE_PENDING}' ORDER BY startedAt ASC, id ASC LIMIT :limit"
    )
    suspend fun pendingScrobbles(ownerKey: Long, source: String, sourceAccountKey: String, limit: Int): List<MusicListenEntity>

    @Query(
        "SELECT l.* FROM music_listens l JOIN (" +
            "SELECT albumKey, MAX(startedAt) AS lastStartedAt FROM music_listens " +
            "WHERE ownerKey = :ownerKey AND albumKey IS NOT NULL GROUP BY albumKey " +
            "ORDER BY lastStartedAt DESC LIMIT :limit" +
            ") r ON l.albumKey = r.albumKey AND l.startedAt = r.lastStartedAt " +
            "WHERE l.ownerKey = :ownerKey ORDER BY l.startedAt DESC, l.id DESC"
    )
    suspend fun latestPerAlbum(ownerKey: Long, limit: Int): List<MusicListenEntity>

    @Query(
        "SELECT * FROM music_listens WHERE ownerKey = :ownerKey AND albumKey IS NOT NULL " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestWithAlbum(ownerKey: Long): MusicListenEntity?

    @Query("DELETE FROM music_listen_artists WHERE ownerKey = :ownerKey")
    suspend fun deleteArtists(ownerKey: Long)

    @Query("DELETE FROM music_listens WHERE ownerKey = :ownerKey")
    suspend fun deleteListens(ownerKey: Long)

    @Transaction
    suspend fun deleteByOwner(ownerKey: Long) {
        deleteArtists(ownerKey)
        deleteListens(ownerKey)
    }
}
