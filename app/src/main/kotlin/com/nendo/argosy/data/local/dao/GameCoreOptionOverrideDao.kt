package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.GameCoreOptionOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameCoreOptionOverrideDao {

    @Query("SELECT * FROM game_core_option_overrides WHERE gameId = :gameId AND coreId = :coreId")
    suspend fun getForGame(gameId: Long, coreId: String): List<GameCoreOptionOverrideEntity>

    @Query("SELECT * FROM game_core_option_overrides WHERE gameId = :gameId AND coreId = :coreId")
    fun observeForGame(gameId: Long, coreId: String): Flow<List<GameCoreOptionOverrideEntity>>

    @Query("SELECT COUNT(*) FROM game_core_option_overrides WHERE gameId = :gameId AND coreId = :coreId")
    suspend fun countForGame(gameId: Long, coreId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: GameCoreOptionOverrideEntity)

    @Query("DELETE FROM game_core_option_overrides WHERE gameId = :gameId AND coreId = :coreId AND optionKey = :optionKey")
    suspend fun delete(gameId: Long, coreId: String, optionKey: String)

    @Query("DELETE FROM game_core_option_overrides WHERE gameId = :gameId AND coreId = :coreId")
    suspend fun deleteAllForGame(gameId: Long, coreId: String)
}
