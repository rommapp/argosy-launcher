package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PendingAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingAchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingAchievementEntity): Long

    @Query("SELECT * FROM pending_achievements WHERE retryCount < 3 ORDER BY createdAt ASC")
    suspend fun getRetryable(): List<PendingAchievementEntity>

    @Query("UPDATE pending_achievements SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)

    @Query("DELETE FROM pending_achievements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_achievements WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("SELECT COUNT(*) FROM pending_achievements")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_achievements")
    suspend fun getCount(): Int

    @Query("SELECT * FROM pending_achievements ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingAchievementEntity>
}
