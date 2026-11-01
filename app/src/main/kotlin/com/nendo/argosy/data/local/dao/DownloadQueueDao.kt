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

    @Query("SELECT * FROM download_queue WHERE state IN ('QUEUED', 'DOWNLOADING', 'EXTRACTING', 'MOVING', 'PAUSED', 'WAITING_FOR_STORAGE', 'RETRYING', 'WAITING_FOR_NETWORK')")
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

    @Query("SELECT * FROM download_queue WHERE id = :id")
    suspend fun getById(id: Long): DownloadQueueEntity?

    @Query("SELECT * FROM download_queue WHERE state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'EXTRACTING', 'MOVING', 'WAITING_FOR_STORAGE', 'RETRYING', 'WAITING_FOR_NETWORK', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getRecoverableDownloads(): List<DownloadQueueEntity>

    @Query("UPDATE download_queue SET resumeValidator = :resumeValidator, responseTotalBytes = :responseTotalBytes, validatedBytes = :validatedBytes, representationChecksum = :representationChecksum WHERE id = :id")
    suspend fun updateTransferCheckpoint(id: Long, resumeValidator: String?, responseTotalBytes: Long?, validatedBytes: Long?, representationChecksum: String? = null)

    @Query("UPDATE download_queue SET tempFilePath = :tempFilePath, requestKey = :requestKey WHERE id = :id")
    suspend fun updateTransferPath(id: Long, tempFilePath: String, requestKey: String)

    @Query("UPDATE download_queue SET retryAttempt = :retryAttempt, nextRetryAt = :nextRetryAt WHERE id = :id")
    suspend fun updateRetry(id: Long, retryAttempt: Int, nextRetryAt: Long?)

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
          AND state IN ('QUEUED', 'PAUSED', 'DOWNLOADING', 'EXTRACTING', 'MOVING', 'WAITING_FOR_STORAGE', 'RETRYING', 'WAITING_FOR_NETWORK', 'FAILED')
    """)
    suspend fun countUnfinishedForOwner(ownerUserId: Long): Int

    @Query("SELECT COUNT(*) FROM download_queue WHERE ownerUserId IS NULL")
    suspend fun countUnowned(): Int

    @Query("UPDATE download_queue SET ownerUserId = :ownerUserId WHERE ownerUserId IS NULL")
    suspend fun adoptUnowned(ownerUserId: Long)
}
