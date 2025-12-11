package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {

    @Query("SELECT * FROM download_queue WHERE state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'WAITING_FOR_STORAGE') ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE state = 'FAILED'")
    suspend fun getFailedDownloads(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE state = 'WAITING_FOR_STORAGE'")
    suspend fun getWaitingForStorage(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadQueueEntity): Long

    @Update
    suspend fun update(entity: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM download_queue WHERE gameId = :gameId LIMIT 1")
    suspend fun getByGameId(gameId: Long): DownloadQueueEntity?

    @Query("UPDATE download_queue SET bytesDownloaded = :bytesDownloaded WHERE id = :id")
    suspend fun updateProgress(id: Long, bytesDownloaded: Long)

    @Query("UPDATE download_queue SET state = :state, errorReason = :errorReason WHERE id = :id")
    suspend fun updateState(id: Long, state: String, errorReason: String? = null)

    @Query("DELETE FROM download_queue WHERE state = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_queue WHERE state = 'FAILED'")
    suspend fun clearFailed()
}
