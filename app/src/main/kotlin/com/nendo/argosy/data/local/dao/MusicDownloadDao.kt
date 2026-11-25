package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MusicDownloadDao {

    @Upsert
    suspend fun upsert(entity: MusicDownloadEntity)

    @Query("SELECT * FROM music_downloads")
    fun observeAll(): Flow<List<MusicDownloadEntity>>

    @Query("SELECT * FROM music_downloads")
    suspend fun getAll(): List<MusicDownloadEntity>

    @Query("SELECT * FROM music_downloads WHERE trackKey = :trackKey")
    suspend fun get(trackKey: String): MusicDownloadEntity?

    @Query("SELECT * FROM music_downloads WHERE trackKey IN (:trackKeys)")
    suspend fun getByKeys(trackKeys: List<String>): List<MusicDownloadEntity>

    @Query("SELECT * FROM music_downloads WHERE source = :source AND status = :status")
    suspend fun getBySourceAndStatus(source: String, status: String): List<MusicDownloadEntity>

    @Query("SELECT * FROM music_downloads WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<MusicDownloadEntity>

    @Query("SELECT COUNT(*) FROM music_downloads WHERE artLocalPath = :artLocalPath")
    suspend fun countWithArt(artLocalPath: String): Int

    @Query("UPDATE music_downloads SET status = :status WHERE trackKey = :trackKey")
    suspend fun updateStatus(trackKey: String, status: String)

    @Query(
        "UPDATE music_downloads SET status = :status, filePath = :filePath, sizeBytes = :sizeBytes, " +
            "artLocalPath = :artLocalPath, downloadedAt = :downloadedAt WHERE trackKey = :trackKey"
    )
    suspend fun markDone(
        trackKey: String,
        status: String,
        filePath: String,
        sizeBytes: Long,
        artLocalPath: String?,
        downloadedAt: Instant
    )

    @Query("DELETE FROM music_downloads WHERE trackKey = :trackKey")
    suspend fun delete(trackKey: String)
}
