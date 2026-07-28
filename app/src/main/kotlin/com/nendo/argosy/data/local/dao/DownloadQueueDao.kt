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

    @Query("SELECT * FROM download_queue WHERE state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'EXTRACTING', 'WAITING_FOR_STORAGE') ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE state = 'FAILED'")
    suspend fun getFailedDownloads(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE state = 'WAITING_FOR_STORAGE'")
    suspend fun getWaitingForStorage(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE state IN ('QUEUED', 'DOWNLOADING', 'EXTRACTING', 'PAUSED', 'WAITING_FOR_STORAGE')")
    fun observeActiveDownloads(): Flow<List<DownloadQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadQueueEntity): Long

    @Update
    suspend fun update(entity: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM download_queue WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

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

    @Query("DELETE FROM download_queue WHERE state IN ('COMPLETED', 'FAILED')")
    suspend fun clearFinished()

    @Query("DELETE FROM download_queue WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    /**
     * Rows this account queued and never finished. Removing the account discards them, so the
     * tally is what the caller is asked to confirm before that happens.
     */
    @Query("""
        SELECT COUNT(*) FROM download_queue
        WHERE ownerUserId = :ownerUserId
          AND state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'EXTRACTING', 'WAITING_FOR_STORAGE')
    """)
    suspend fun countUnfinishedForOwner(ownerUserId: Long): Int
}
