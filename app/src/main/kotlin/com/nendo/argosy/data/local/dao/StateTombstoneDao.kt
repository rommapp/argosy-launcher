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

    @Query("""
        SELECT rommSaveId FROM state_tombstones
        WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun getServerIdsForGame(gameId: Long, ownerUserId: Long?): List<Long>

    @Query("""
        DELETE FROM state_tombstones
        WHERE rommSaveId = :rommSaveId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun deleteByServerId(rommSaveId: Long, ownerUserId: Long?)

    @Query("""
        DELETE FROM state_tombstones
        WHERE rommSaveId IN (:rommSaveIds) AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun deleteByServerIds(rommSaveIds: List<Long>, ownerUserId: Long?)

    @Query("DELETE FROM state_tombstones WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("DELETE FROM state_tombstones WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("DELETE FROM state_tombstones WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM state_tombstones WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("DELETE FROM state_tombstones")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM state_tombstones WHERE ownerUserId IS NULL")
    suspend fun countUnowned(): Int

    @Query("UPDATE state_tombstones SET ownerUserId = :ownerUserId WHERE ownerUserId IS NULL")
    suspend fun adoptUnowned(ownerUserId: Long)
}
