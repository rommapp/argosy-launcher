package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}
