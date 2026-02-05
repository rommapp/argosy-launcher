package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PendingStateSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingStateSyncDao {

    @Query("SELECT * FROM pending_state_sync ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingStateSyncEntity>

    @Query("SELECT * FROM pending_state_sync WHERE retryCount < 3 ORDER BY createdAt ASC")
    suspend fun getRetryable(): List<PendingStateSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_state_sync")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_state_sync")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingStateSyncEntity): Long

    @Query("DELETE FROM pending_state_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_state_sync WHERE stateCacheId = :stateCacheId")
    suspend fun deleteByStateCacheId(stateCacheId: Long)

    @Query("DELETE FROM pending_state_sync WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("UPDATE pending_state_sync SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String?)

    @Query("SELECT * FROM pending_state_sync WHERE stateCacheId = :stateCacheId LIMIT 1")
    suspend fun getByStateCacheId(stateCacheId: Long): PendingStateSyncEntity?

    @Query("DELETE FROM pending_state_sync WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM pending_state_sync")
    suspend fun deleteAll()
}
