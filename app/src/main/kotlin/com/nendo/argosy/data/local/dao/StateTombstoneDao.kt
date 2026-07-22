package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.StateTombstoneEntity

@Dao
interface StateTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: StateTombstoneEntity)

    @Query("SELECT rommSaveId FROM state_tombstones WHERE gameId = :gameId")
    suspend fun getServerIdsForGame(gameId: Long): List<Long>

    @Query("DELETE FROM state_tombstones WHERE rommSaveId = :rommSaveId")
    suspend fun deleteByServerId(rommSaveId: Long)

    @Query("DELETE FROM state_tombstones WHERE rommSaveId IN (:rommSaveIds)")
    suspend fun deleteByServerIds(rommSaveIds: List<Long>)

    @Query("DELETE FROM state_tombstones WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("DELETE FROM state_tombstones WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("DELETE FROM state_tombstones")
    suspend fun deleteAll()
}
