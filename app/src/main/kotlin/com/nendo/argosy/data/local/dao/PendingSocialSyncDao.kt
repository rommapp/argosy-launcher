package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.SocialSyncType
import java.time.Instant

@Dao
interface PendingSocialSyncDao {

    @Insert
    suspend fun insert(entity: PendingSocialSyncEntity): Long

    @Query("""
        SELECT * FROM pending_social_sync
        WHERE syncType = :type AND status = 'PENDING'
        ORDER BY occurredAt ASC
        LIMIT :limit
    """)
    suspend fun getPendingByType(type: SocialSyncType, limit: Int = 50): List<PendingSocialSyncEntity>

    @Query("""
        UPDATE pending_social_sync
        SET status = 'IN_PROGRESS', updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markInProgress(id: Long, now: Instant = Instant.now())

    @Query("""
        UPDATE pending_social_sync
        SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error, updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, error: String, now: Instant = Instant.now())

    @Query("DELETE FROM pending_social_sync WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_social_sync WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_social_sync WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("""
        SELECT * FROM pending_social_sync
        WHERE status = 'FAILED' AND retryCount < maxRetries
        ORDER BY occurredAt ASC
    """)
    suspend fun getRetryable(): List<PendingSocialSyncEntity>

    @Query("""
        UPDATE pending_social_sync
        SET status = 'PENDING', updatedAt = :now
        WHERE status = 'IN_PROGRESS'
    """)
    suspend fun resetInProgress(now: Instant = Instant.now())

    @Query("""
        DELETE FROM pending_social_sync
        WHERE status = 'FAILED' AND retryCount >= maxRetries
    """)
    suspend fun deleteExhausted()
}
