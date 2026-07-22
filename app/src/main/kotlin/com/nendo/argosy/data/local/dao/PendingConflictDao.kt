package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingConflictEntity): Long

    @Update
    suspend fun update(entity: PendingConflictEntity)

    @Query("SELECT * FROM pending_conflicts WHERE dismissed = 0 ORDER BY discoveredAt DESC")
    suspend fun getOpenConflicts(): List<PendingConflictEntity>

    @Query("SELECT * FROM pending_conflicts WHERE dismissed = 0 ORDER BY discoveredAt DESC")
    fun observeOpenConflicts(): Flow<List<PendingConflictEntity>>

    @Query("SELECT COUNT(*) FROM pending_conflicts WHERE dismissed = 0")
    fun getOpenCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_conflicts WHERE dismissed = 0")
    suspend fun getOpenCount(): Int

    @Query("SELECT * FROM pending_conflicts WHERE id = :id")
    suspend fun getById(id: Long): PendingConflictEntity?

    @Query("SELECT * FROM pending_conflicts WHERE gameId = :gameId AND rommSaveId = :rommSaveId LIMIT 1")
    suspend fun findByGameAndSave(gameId: Long, rommSaveId: Long?): PendingConflictEntity?

    @Query("UPDATE pending_conflicts SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE pending_conflicts SET dismissed = 1")
    suspend fun dismissAll()

    @Query("DELETE FROM pending_conflicts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_conflicts WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM pending_conflicts WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("DELETE FROM pending_conflicts")
    suspend fun deleteAll()
}
