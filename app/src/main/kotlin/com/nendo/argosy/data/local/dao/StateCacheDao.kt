package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.StateCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * Owner scoping here is deliberately tolerant: a null `ownerUserId` is a row written before the
 * schema had accounts, so it stays reachable for whoever is signed in instead of being orphaned.
 * Single-row resolvers order the signed-in account's row ahead of an unattributed one, because
 * both can now match where the unique index previously allowed only one.
 */
@Dao
interface StateCacheDao {

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    fun observeByGame(gameId: Long, ownerUserId: Long?): Flow<List<StateCacheEntity>>

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getByGame(gameId: Long, ownerUserId: Long?): List<StateCacheEntity>

    @Query("SELECT * FROM state_cache WHERE id = :id")
    suspend fun getById(id: Long): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND slotNumber = :slotNumber
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getBySlot(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        channelName: String?,
        ownerUserId: Long?
    ): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND slotNumber = :slotNumber
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
        AND (coreId = :coreId OR (coreId IS NULL AND :coreId IS NULL))
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getBySlotAndCore(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        channelName: String?,
        coreId: String?,
        ownerUserId: Long?
    ): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND channelName = :channelName
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getByChannel(gameId: Long, channelName: String, ownerUserId: Long?): List<StateCacheEntity>

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND channelName IS NULL
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getDefaultChannel(gameId: Long, ownerUserId: Long?): List<StateCacheEntity>

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
        AND (coreId = :coreId OR (coreId IS NULL AND :coreId IS NULL))
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getByChannelAndCore(
        gameId: Long,
        channelName: String?,
        coreId: String?,
        ownerUserId: Long?
    ): List<StateCacheEntity>

    @Query("""
        DELETE FROM state_cache
        WHERE gameId = :gameId
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
        AND (coreId = :coreId OR (coreId IS NULL AND :coreId IS NULL))
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun deleteByChannelAndCore(gameId: Long, channelName: String?, coreId: String?, ownerUserId: Long?)

    @Query("""
        SELECT COUNT(*) FROM state_cache
        WHERE gameId = :gameId AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
    """)
    suspend fun countByGameAndOwner(gameId: Long, ownerUserId: Long?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StateCacheEntity): Long

    @Insert
    suspend fun insert(entity: StateCacheEntity): Long

    @Update
    suspend fun update(entity: StateCacheEntity)

    @Query("UPDATE state_cache SET channelName = :channelName, isLocked = 1 WHERE id = :id")
    suspend fun bindToChannel(id: Long, channelName: String)

    @Query("UPDATE state_cache SET channelName = NULL, isLocked = 0 WHERE id = :id")
    suspend fun unbindFromChannel(id: Long)

    @Query("UPDATE state_cache SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("DELETE FROM state_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM state_cache WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("DELETE FROM state_cache WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("""
        DELETE FROM state_cache
        WHERE gameId = :gameId
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun deleteByChannel(gameId: Long, channelName: String?, ownerUserId: Long?)

    /**
     * Cache budget is per account: one account filling its slots must not evict another's states
     * for the same rom. A null [ownerUserId] is its own bucket of unattributed legacy rows rather
     * than a wildcard, which is why these use strict equality where the read paths are tolerant.
     */
    @Query("""
        SELECT COUNT(*) FROM state_cache
        WHERE gameId = :gameId AND isLocked = 1
          AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
    """)
    suspend fun countLockedByGameAndOwner(gameId: Long, ownerUserId: Long?): Int

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND isLocked = 0
          AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
        ORDER BY cachedAt ASC
        LIMIT :count
    """)
    suspend fun getOldestUnlockedForOwner(gameId: Long, ownerUserId: Long?, count: Int): List<StateCacheEntity>

    @Query("DELETE FROM state_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("""
        SELECT * FROM state_cache
        WHERE rommSaveId = :rommSaveId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getByRommSaveId(rommSaveId: Long, ownerUserId: Long?): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND emulatorId = :emulatorId
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getByGameAndEmulator(gameId: Long, emulatorId: String, ownerUserId: Long?): List<StateCacheEntity>

    @Query("""
        SELECT * FROM state_cache
        WHERE syncStatus IN ('PENDING_UPLOAD', 'LOCAL_NEWER')
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY cachedAt ASC
    """)
    suspend fun getPendingUploads(ownerUserId: Long?): List<StateCacheEntity>

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND syncStatus IN ('PENDING_UPLOAD', 'LOCAL_NEWER')
        AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY slotNumber ASC
    """)
    suspend fun getPendingUploadsByGame(gameId: Long, ownerUserId: Long?): List<StateCacheEntity>

    @Query("""
        UPDATE state_cache
        SET rommSaveId = :rommSaveId,
            syncStatus = :syncStatus,
            serverUpdatedAt = :serverUpdatedAt,
            lastUploadedHash = :lastUploadedHash
        WHERE id = :id
    """)
    suspend fun updateSyncState(
        id: Long,
        rommSaveId: Long?,
        syncStatus: String?,
        serverUpdatedAt: Long?,
        lastUploadedHash: String?
    )

    @Query("UPDATE state_cache SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, syncStatus: String?)

    @Query("""
        SELECT * FROM state_cache
        WHERE rommSaveId IS NOT NULL
        ORDER BY cachedAt DESC
    """)
    suspend fun getSyncedStates(): List<StateCacheEntity>

    @Query("DELETE FROM state_cache WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM state_cache WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("DELETE FROM state_cache")
    suspend fun deleteAll()

    @Query("SELECT * FROM state_cache")
    suspend fun getAll(): List<StateCacheEntity>

    @Query("SELECT COUNT(*) FROM state_cache")
    suspend fun count(): Int
}
