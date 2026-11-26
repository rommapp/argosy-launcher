package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class MusicDownloadServerRow(val source: String, val serverKey: String)

@Dao
interface MusicDownloadDao {

    @Upsert
    suspend fun upsert(entity: MusicDownloadEntity)

    @Query("SELECT * FROM music_downloads WHERE serverKey IN (:serverKeys)")
    fun observeForServers(serverKeys: List<String>): Flow<List<MusicDownloadEntity>>

    @Query("SELECT * FROM music_downloads WHERE serverKey = :serverKey AND trackKey = :trackKey")
    suspend fun get(serverKey: String, trackKey: String): MusicDownloadEntity?

    @Query("SELECT * FROM music_downloads WHERE serverKey IN (:serverKeys) AND trackKey IN (:trackKeys)")
    suspend fun getByKeys(serverKeys: List<String>, trackKeys: List<String>): List<MusicDownloadEntity>

    @Query("SELECT * FROM music_downloads WHERE serverKey = :serverKey AND status = :status")
    suspend fun getByServerAndStatus(serverKey: String, status: String): List<MusicDownloadEntity>

    @Query("SELECT * FROM music_downloads WHERE serverKey IN (:serverKeys) AND status IN (:statuses)")
    suspend fun getByStatuses(serverKeys: List<String>, statuses: List<String>): List<MusicDownloadEntity>

    @Query("SELECT COUNT(*) FROM music_downloads WHERE artLocalPath = :artLocalPath")
    suspend fun countWithArt(artLocalPath: String): Int

    @Query("UPDATE music_downloads SET status = :status WHERE serverKey = :serverKey AND trackKey = :trackKey")
    suspend fun updateStatus(serverKey: String, trackKey: String, status: String)

    @Query(
        "UPDATE music_downloads SET status = :status, filePath = :filePath, sizeBytes = :sizeBytes, " +
            "artLocalPath = :artLocalPath, downloadedAt = :downloadedAt " +
            "WHERE serverKey = :serverKey AND trackKey = :trackKey"
    )
    suspend fun markDone(
        serverKey: String,
        trackKey: String,
        status: String,
        filePath: String,
        sizeBytes: Long,
        artLocalPath: String?,
        downloadedAt: Instant
    )

    @Query("DELETE FROM music_downloads WHERE serverKey = :serverKey AND trackKey = :trackKey")
    suspend fun delete(serverKey: String, trackKey: String)

    @Query(
        "UPDATE OR IGNORE music_downloads SET serverKey = :serverKey, ownerKey = :ownerKey " +
            "WHERE source = :source AND serverKey = '${MusicDownloadEntity.UNSCOPED_SERVER_KEY}'"
    )
    suspend fun adoptUnscoped(source: String, serverKey: String, ownerKey: Long)

    @Query("SELECT DISTINCT source, serverKey FROM music_downloads WHERE ownerKey = :ownerKey")
    suspend fun serversOwnedBy(ownerKey: Long): List<MusicDownloadServerRow>

    @Query(
        "UPDATE music_downloads SET ownerKey = :toOwnerKey " +
            "WHERE serverKey = :serverKey AND ownerKey = :fromOwnerKey"
    )
    suspend fun reassignOwner(serverKey: String, fromOwnerKey: Long, toOwnerKey: Long)

    @Query("DELETE FROM music_downloads WHERE serverKey = :serverKey AND ownerKey = :ownerKey")
    suspend fun deleteForOwner(serverKey: String, ownerKey: Long)
}
