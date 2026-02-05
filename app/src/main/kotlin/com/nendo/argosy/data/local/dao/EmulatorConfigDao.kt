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
    suspend fun getDefaultForPlatform(platformId: Long): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE platformId IS NULL AND gameId IS NULL AND isDefault = 1 LIMIT 1")
    suspend fun getGlobalDefault(): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL")
    fun observePlatformConfigs(platformId: Long): Flow<List<EmulatorConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: EmulatorConfigEntity): Long

    @Query("DELETE FROM emulator_configs WHERE gameId = :gameId")
    suspend fun deleteGameOverride(gameId: Long)

    @Query("UPDATE emulator_configs SET isDefault = 0 WHERE platformId = :platformId AND gameId IS NULL")
    suspend fun clearPlatformDefaults(platformId: Long)

    @Query("UPDATE emulator_configs SET isDefault = 1 WHERE id = :configId")
    suspend fun setAsDefault(configId: Long)

    @Query("UPDATE emulator_configs SET coreName = :coreName WHERE id = :configId")
    suspend fun updateCoreName(configId: Long, coreName: String?)

    @Query("UPDATE emulator_configs SET coreName = :coreName WHERE gameId = :gameId")
    suspend fun updateCoreNameForGame(gameId: Long, coreName: String?)

    @Query("UPDATE emulator_configs SET coreName = :coreName WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updateCoreNameForPlatform(platformId: Long, coreName: String?)

    @Query("UPDATE emulator_configs SET platformId = :newPlatformId WHERE platformId = :oldPlatformId")
    suspend fun migratePlatform(oldPlatformId: Long, newPlatformId: Long)

    @Query("SELECT preferredExtension FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun getPreferredExtension(platformId: Long): String?

    @Query("UPDATE emulator_configs SET preferredExtension = :extension WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updatePreferredExtension(platformId: Long, extension: String)

    @Query("DELETE FROM emulator_configs WHERE packageName = :packageName AND gameId IS NULL")
    suspend fun clearPlatformConfigsByPackage(packageName: String)
}
