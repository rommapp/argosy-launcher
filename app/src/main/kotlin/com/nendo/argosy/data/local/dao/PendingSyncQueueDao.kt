package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PendingSyncQueueDao {

    @Query("""
        SELECT * FROM pending_sync_queue
        WHERE status = 'PENDING'
        ORDER BY priority ASC, createdAt ASC
    """)
    suspend fun getPendingByPriority(): List<PendingSyncQueueEntity>

    @Query("""
        SELECT * FROM pending_sync_queue
        WHERE status = 'PENDING' AND priority = :priority
        ORDER BY createdAt ASC
    """)
    suspend fun getPendingByPriorityTier(priority: Int): List<PendingSyncQueueEntity>

    @Query("SELECT * FROM pending_sync_queue WHERE id = :id")
    suspend fun getById(id: Long): PendingSyncQueueEntity?

    @Query("SELECT * FROM pending_sync_queue WHERE gameId = :gameId")
    suspend fun getByGameId(gameId: Long): List<PendingSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSyncQueueEntity): Long

    @Update
    suspend fun update(entity: PendingSyncQueueEntity)

    @Query("DELETE FROM pending_sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_sync_queue WHERE gameId = :gameId AND syncType = :syncType")
    suspend fun deleteByGameAndType(gameId: Long, syncType: SyncType)

    @Query("""
        UPDATE pending_sync_queue
        SET status = 'IN_PROGRESS', updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markInProgress(id: Long, now: Instant = Instant.now())

    @Query("""
        UPDATE pending_sync_queue
        SET status = 'COMPLETED', updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markCompleted(id: Long, now: Instant = Instant.now())

    @Query("""
        UPDATE pending_sync_queue
        SET status = 'FAILED', lastError = :error, retryCount = retryCount + 1, updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, error: String?, now: Instant = Instant.now())

    @Query("""
        UPDATE pending_sync_queue
        SET status = 'PENDING', updatedAt = :now
        WHERE id = :id AND retryCount < maxRetries
    """)
    suspend fun retryIfEligible(id: Long, now: Instant = Instant.now())

    @Query("DELETE FROM pending_sync_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("""
        DELETE FROM pending_sync_queue
        WHERE status = 'FAILED' AND retryCount >= maxRetries
    """)
    suspend fun deleteExhaustedRetries()

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sync_queue WHERE gameId = :gameId AND syncType = :syncType AND status = 'PENDING')")
    suspend fun hasPending(gameId: Long, syncType: SyncType): Boolean

    @Query("""
        SELECT * FROM pending_sync_queue
        WHERE syncType = :syncType AND status = 'PENDING' AND retryCount < maxRetries
        ORDER BY createdAt ASC
    """)
    suspend fun getRetryableBySyncType(syncType: SyncType): List<PendingSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE syncType = :syncType AND status = 'PENDING'")
    suspend fun countPendingBySyncType(syncType: SyncType): Int

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE syncType = :syncType AND status = 'PENDING'")
    fun observePendingCountBySyncType(syncType: SyncType): Flow<Int>

    @Query("DELETE FROM pending_sync_queue WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM pending_sync_queue WHERE syncType = :syncType")
    suspend fun deleteBySyncType(syncType: SyncType)

    @Query("DELETE FROM pending_sync_queue")
    suspend fun deleteAll()
}
