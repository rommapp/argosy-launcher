package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.SteamDownloadQueueEntity

@Dao
interface SteamDownloadQueueDao {

    @Query("SELECT * FROM steam_download_queue WHERE state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'PREPARING') ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<SteamDownloadQueueEntity>

    @Query("SELECT * FROM steam_download_queue WHERE appId = :appId LIMIT 1")
    suspend fun getByAppId(appId: Long): SteamDownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SteamDownloadQueueEntity): Long

    @Query("UPDATE steam_download_queue SET bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes WHERE appId = :appId")
    suspend fun updateProgress(appId: Long, bytesDownloaded: Long, totalBytes: Long)

    @Query("UPDATE steam_download_queue SET state = :state, errorReason = :errorReason WHERE appId = :appId")
    suspend fun updateState(appId: Long, state: String, errorReason: String? = null)

    @Query("UPDATE steam_download_queue SET installPath = :installPath WHERE appId = :appId")
    suspend fun updateInstallPath(appId: Long, installPath: String)

    @Query("DELETE FROM steam_download_queue WHERE appId = :appId")
    suspend fun deleteByAppId(appId: Long)

    @Query("DELETE FROM steam_download_queue WHERE state = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM steam_download_queue WHERE state IN ('COMPLETED', 'FAILED')")
    suspend fun clearFinished()
}
