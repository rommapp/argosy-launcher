package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmulatorConfigDao {

    @Query("SELECT * FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getByGameId(gameId: Long): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultForPlatform(platformId: String): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE platformId IS NULL AND gameId IS NULL AND isDefault = 1 LIMIT 1")
    suspend fun getGlobalDefault(): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL")
    fun observePlatformConfigs(platformId: String): Flow<List<EmulatorConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: EmulatorConfigEntity): Long

    @Query("DELETE FROM emulator_configs WHERE gameId = :gameId")
    suspend fun deleteGameOverride(gameId: Long)

    @Query("UPDATE emulator_configs SET isDefault = 0 WHERE platformId = :platformId AND gameId IS NULL")
    suspend fun clearPlatformDefaults(platformId: String)

    @Query("UPDATE emulator_configs SET isDefault = 1 WHERE id = :configId")
    suspend fun setAsDefault(configId: Long)
}
