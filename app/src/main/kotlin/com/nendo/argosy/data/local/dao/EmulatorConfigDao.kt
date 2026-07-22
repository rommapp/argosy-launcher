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

    @Query("UPDATE emulator_configs SET coreName = NULL WHERE coreName = :coreName")
    suspend fun clearCoreName(coreName: String)

    @Query("UPDATE emulator_configs SET platformId = :newPlatformId WHERE platformId = :oldPlatformId")
    suspend fun migratePlatform(oldPlatformId: Long, newPlatformId: Long)

    @Query("SELECT preferredExtension FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun getPreferredExtension(platformId: Long): String?

    @Query("UPDATE emulator_configs SET preferredExtension = :extension WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updatePreferredExtension(platformId: Long, extension: String)

    @Query("UPDATE emulator_configs SET useFileUri = :useFileUri WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updateUseFileUriForPlatform(platformId: Long, useFileUri: Boolean)

    @Query("SELECT displayTarget FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun getDisplayTargetForPlatform(platformId: Long): String?

    @Query("SELECT displayTarget FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getDisplayTargetForGame(gameId: Long): String?

    @Query("SELECT savePath FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getSavePathForGame(gameId: Long): String?

    @Query("UPDATE emulator_configs SET savePath = :savePath WHERE gameId = :gameId")
    suspend fun updateSavePathForGame(gameId: Long, savePath: String?)

    @Query("UPDATE emulator_configs SET displayTarget = :displayTarget WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updateDisplayTargetForPlatform(platformId: Long, displayTarget: String?)

    @Query("UPDATE emulator_configs SET displayTarget = :displayTarget WHERE gameId = :gameId")
    suspend fun updateDisplayTargetForGame(gameId: Long, displayTarget: String?)

    @Query("SELECT preferredExtension FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getPreferredExtensionForGame(gameId: Long): String?

    @Query("UPDATE emulator_configs SET preferredExtension = :extension WHERE gameId = :gameId")
    suspend fun updatePreferredExtensionForGame(gameId: Long, extension: String?)

    @Query("DELETE FROM emulator_configs WHERE packageName = :packageName AND gameId IS NULL")
    suspend fun clearPlatformConfigsByPackage(packageName: String)

    @Query("SELECT DISTINCT packageName FROM emulator_configs WHERE isDefault = 1 AND gameId IS NULL AND packageName IS NOT NULL")
    fun observeAssignedPackageNames(): Flow<List<String>>
}
