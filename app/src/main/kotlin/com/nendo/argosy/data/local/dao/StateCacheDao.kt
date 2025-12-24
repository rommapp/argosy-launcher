package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.StateCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StateCacheDao {

    @Query("SELECT * FROM state_cache WHERE gameId = :gameId ORDER BY slotNumber ASC")
    fun observeByGame(gameId: Long): Flow<List<StateCacheEntity>>

    @Query("SELECT * FROM state_cache WHERE gameId = :gameId ORDER BY slotNumber ASC")
    suspend fun getByGame(gameId: Long): List<StateCacheEntity>

    @Query("SELECT * FROM state_cache WHERE id = :id")
    suspend fun getById(id: Long): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND slotNumber = :slotNumber
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
    """)
    suspend fun getBySlot(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        channelName: String? = null
    ): StateCacheEntity?

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND channelName = :channelName
        ORDER BY slotNumber ASC
    """)
    suspend fun getByChannel(gameId: Long, channelName: String): List<StateCacheEntity>

    @Query("""
        SELECT * FROM state_cache
        WHERE gameId = :gameId AND channelName IS NULL
        ORDER BY slotNumber ASC
    """)
    suspend fun getDefaultChannel(gameId: Long): List<StateCacheEntity>

    @Query("SELECT COUNT(*) FROM state_cache WHERE gameId = :gameId")
    suspend fun countByGame(gameId: Long): Int

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

    @Query("""
        DELETE FROM state_cache
        WHERE gameId = :gameId
        AND (channelName = :channelName OR (channelName IS NULL AND :channelName IS NULL))
    """)
    suspend fun deleteByChannel(gameId: Long, channelName: String?)

    @Query("""
        DELETE FROM state_cache
        WHERE id IN (
            SELECT id FROM state_cache
            WHERE gameId = :gameId AND isLocked = 0
            ORDER BY cachedAt ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldestUnlocked(gameId: Long, count: Int)
}
