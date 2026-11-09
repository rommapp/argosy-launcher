package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import kotlinx.coroutines.flow.Flow

data class PlatformDisplayTarget(
    val platformId: Long,
    val displayTarget: String
)

data class PlatformGameTargetCount(
    val platformId: Long,
    val gameCount: Int
)

@Dao
interface EmulatorConfigDao {

    @Query("SELECT * FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getByGameId(gameId: Long): EmulatorConfigEntity?

    @Query("SELECT * FROM emulator_configs WHERE gameId IS NOT NULL")
    suspend fun getGameOverrides(): List<EmulatorConfigEntity>

    @Query("SELECT * FROM emulator_configs WHERE platformId IS NOT NULL AND gameId IS NULL AND isDefault = 1")
    suspend fun getPlatformDefaults(): List<EmulatorConfigEntity>

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

    @Query(
        "SELECT platformId, displayTarget FROM emulator_configs " +
            "WHERE platformId IS NOT NULL AND gameId IS NULL AND isDefault = 1 AND displayTarget IS NOT NULL"
    )
    suspend fun getPlatformDisplayTargets(): List<PlatformDisplayTarget>

    @Query(
        """
        SELECT g.platformId AS platformId, COUNT(*) AS gameCount
        FROM emulator_configs c INNER JOIN games g ON g.id = c.gameId
        WHERE c.gameId IS NOT NULL AND c.displayTarget IS NOT NULL
        GROUP BY g.platformId
        """
    )
    suspend fun getGameDisplayTargetCounts(): List<PlatformGameTargetCount>

    @Query("SELECT displayTarget FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getDisplayTargetForGame(gameId: Long): String?

    @Query(
        """
        SELECT COALESCE(
            (SELECT displayTarget FROM emulator_configs WHERE gameId = :gameId LIMIT 1),
            (SELECT displayTarget FROM emulator_configs
             WHERE platformId = (SELECT platformId FROM games WHERE id = :gameId)
               AND gameId IS NULL AND isDefault = 1 LIMIT 1)
        )
        """
    )
    suspend fun getEffectiveDisplayTarget(gameId: Long): String?

    @Query("SELECT savePath FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getSavePathForGame(gameId: Long): String?

    @Query(
        "SELECT COUNT(*) FROM emulator_configs " +
            "WHERE savePath = :savePath AND gameId IS NOT NULL AND gameId != :gameId"
    )
    suspend fun countOtherGamesWithSavePath(gameId: Long, savePath: String): Int

    @Query("UPDATE emulator_configs SET savePath = :savePath WHERE gameId = :gameId")
    suspend fun updateSavePathForGame(gameId: Long, savePath: String?)

    @Query("SELECT selectedMemcardPath FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getSelectedMemcardForGame(gameId: Long): String?

    @Query("UPDATE emulator_configs SET selectedMemcardPath = :memcardPath WHERE gameId = :gameId")
    suspend fun updateSelectedMemcardForGame(gameId: Long, memcardPath: String?)

    @Query("UPDATE emulator_configs SET displayTarget = :displayTarget WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updateDisplayTargetForPlatform(platformId: Long, displayTarget: String?)

    @Query("UPDATE emulator_configs SET displayTarget = :displayTarget WHERE gameId = :gameId")
    suspend fun updateDisplayTargetForGame(gameId: Long, displayTarget: String?)

    @Query("SELECT preferredExtension FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getPreferredExtensionForGame(gameId: Long): String?

    @Query("UPDATE emulator_configs SET preferredExtension = :extension WHERE gameId = :gameId")
    suspend fun updatePreferredExtensionForGame(gameId: Long, extension: String?)

    @Query("UPDATE emulator_configs SET gpuDriver = :gpuDriver WHERE gameId = :gameId")
    suspend fun updateGpuDriverForGame(gameId: Long, gpuDriver: String?)

    @Query("UPDATE emulator_configs SET graphicsBackend = :graphicsBackend WHERE gameId = :gameId")
    suspend fun updateGraphicsBackendForGame(gameId: Long, graphicsBackend: String?)

    @Query("SELECT controllerTypes FROM emulator_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getControllerTypesForGame(gameId: Long): String?

    @Query("SELECT controllerTypes FROM emulator_configs WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun getControllerTypesForPlatform(platformId: Long): String?

    @Query("UPDATE emulator_configs SET controllerTypes = :controllerTypes WHERE gameId = :gameId")
    suspend fun updateControllerTypesForGame(gameId: Long, controllerTypes: String?)

    @Query("UPDATE emulator_configs SET controllerTypes = :controllerTypes WHERE platformId = :platformId AND gameId IS NULL AND isDefault = 1")
    suspend fun updateControllerTypesForPlatform(platformId: Long, controllerTypes: String?)

    @Query("DELETE FROM emulator_configs WHERE packageName = :packageName AND gameId IS NULL")
    suspend fun clearPlatformConfigsByPackage(packageName: String)

    @Query("SELECT DISTINCT packageName FROM emulator_configs WHERE isDefault = 1 AND gameId IS NULL AND packageName IS NOT NULL")
    fun observeAssignedPackageNames(): Flow<List<String>>
}
