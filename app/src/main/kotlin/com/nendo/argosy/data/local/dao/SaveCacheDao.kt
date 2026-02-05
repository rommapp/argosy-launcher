package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveCacheDao {

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC")
    fun observeByGame(gameId: Long): Flow<List<SaveCacheEntity>>

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC")
    suspend fun getByGame(gameId: Long): List<SaveCacheEntity>

    @Query("SELECT * FROM save_cache WHERE id = :id")
    suspend fun getById(id: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND note = :channelName LIMIT 1")
    suspend fun getByGameAndChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND contentHash = :hash LIMIT 1")
    suspend fun getByGameAndHash(gameId: Long, hash: String): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND slotName = :slotName LIMIT 1")
    suspend fun getByGameAndSlot(gameId: Long, slotName: String): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND isHardcore = 1 ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLatestHardcoreSave(gameId: Long): SaveCacheEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM save_cache WHERE gameId = :gameId AND isHardcore = 1)")
    suspend fun hasHardcoreSave(gameId: Long): Boolean

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND isHardcore = 0
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestCasualSave(gameId: Long): SaveCacheEntity?

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND isHardcore = 0 AND slotName = :channelName
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestCasualSaveInChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Update
    suspend fun update(entity: SaveCacheEntity)

    @Query("SELECT COUNT(*) FROM save_cache WHERE gameId = :gameId")
    suspend fun countByGame(gameId: Long): Int

    @Insert
    suspend fun insert(entity: SaveCacheEntity): Long

    @Query("UPDATE save_cache SET note = :note, isLocked = (:note IS NOT NULL) WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("DELETE FROM save_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM save_cache WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("""
        DELETE FROM save_cache
        WHERE id IN (
            SELECT id FROM save_cache
            WHERE gameId = :gameId AND isLocked = 0
            ORDER BY cachedAt ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldestUnlocked(gameId: Long, count: Int)

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND isLocked = 0
        ORDER BY cachedAt ASC
    """)
    suspend fun getOldestUnlocked(gameId: Long): List<SaveCacheEntity>

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getMostRecent(gameId: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND cachedAt = :timestamp LIMIT 1")
    suspend fun getByTimestamp(gameId: Long, timestamp: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND note = :channelName ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getMostRecentInChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Query("DELETE FROM save_cache WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM save_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM save_cache")
    suspend fun count(): Int
}
