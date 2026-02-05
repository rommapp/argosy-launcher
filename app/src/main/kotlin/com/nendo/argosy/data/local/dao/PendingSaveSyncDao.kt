package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PendingSaveSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSaveSyncDao {

    @Query("SELECT * FROM pending_save_sync ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSaveSyncEntity>

    @Query("SELECT * FROM pending_save_sync WHERE action = 'UPLOAD' ORDER BY createdAt ASC")
    suspend fun getPendingUploads(): List<PendingSaveSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_save_sync")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_save_sync")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSaveSyncEntity): Long

    @Query("DELETE FROM pending_save_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_save_sync WHERE gameId = :gameId AND emulatorId = :emulatorId")
    suspend fun deleteByGameAndEmulator(gameId: Long, emulatorId: String)

    @Query("DELETE FROM pending_save_sync WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("UPDATE pending_save_sync SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)

    @Query("SELECT * FROM pending_save_sync WHERE retryCount < 3 ORDER BY createdAt ASC")
    suspend fun getRetryable(): List<PendingSaveSyncEntity>

    @Query("DELETE FROM pending_save_sync WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM pending_save_sync")
    suspend fun deleteAll()
}
