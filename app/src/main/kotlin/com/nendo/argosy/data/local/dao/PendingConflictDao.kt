package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingConflictEntity): Long

    @Query("SELECT * FROM pending_conflicts WHERE dismissed = 0 AND ownerUserId IN (:owners) ORDER BY discoveredAt DESC")
    suspend fun getOpenConflicts(owners: List<Long>): List<PendingConflictEntity>

    @Query("SELECT * FROM pending_conflicts WHERE dismissed = 0 AND ownerUserId IN (:owners) ORDER BY discoveredAt DESC")
    fun observeOpenConflicts(owners: List<Long>): Flow<List<PendingConflictEntity>>

    @Query("SELECT COUNT(*) FROM pending_conflicts WHERE dismissed = 0 AND ownerUserId IN (:owners)")
    fun getOpenCountFlow(owners: List<Long>): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_conflicts WHERE dismissed = 0 AND ownerUserId IN (:owners)")
    suspend fun getOpenCount(owners: List<Long>): Int

    @Query("SELECT * FROM pending_conflicts WHERE id = :id")
    suspend fun getById(id: Long): PendingConflictEntity?

    @Query("""
        SELECT * FROM pending_conflicts
        WHERE gameId = :gameId AND rommSaveId = :rommSaveId AND ownerUserId = :ownerUserId
        LIMIT 1
    """)
    suspend fun findByGameSaveAndOwner(
        gameId: Long,
        rommSaveId: Long?,
        ownerUserId: Long
    ): PendingConflictEntity?

    @Query("UPDATE pending_conflicts SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("DELETE FROM pending_conflicts WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("DELETE FROM pending_conflicts WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("DELETE FROM pending_conflicts")
    suspend fun deleteAll()
}
