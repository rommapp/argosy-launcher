package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings UI facade over [EmulatorConfigDao] for global, per-platform, and
 * per-game emulator configuration entries.
 */
@Singleton
class EmulatorConfigRepository @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao
) {
    suspend fun getByGameId(gameId: Long): EmulatorConfigEntity? =
        emulatorConfigDao.getByGameId(gameId)

    suspend fun getDefaultForPlatform(platformId: Long): EmulatorConfigEntity? =
        emulatorConfigDao.getDefaultForPlatform(platformId)

    suspend fun getGlobalDefault(): EmulatorConfigEntity? =
        emulatorConfigDao.getGlobalDefault()

    fun observePlatformConfigs(platformId: Long): Flow<List<EmulatorConfigEntity>> =
        emulatorConfigDao.observePlatformConfigs(platformId)

    suspend fun insert(config: EmulatorConfigEntity): Long =
        emulatorConfigDao.insert(config)

    suspend fun deleteGameOverride(gameId: Long) =
        emulatorConfigDao.deleteGameOverride(gameId)

    suspend fun clearPlatformDefaults(platformId: Long) =
        emulatorConfigDao.clearPlatformDefaults(platformId)

    suspend fun setAsDefault(configId: Long) =
        emulatorConfigDao.setAsDefault(configId)

    suspend fun updateCoreName(configId: Long, coreName: String?) =
        emulatorConfigDao.updateCoreName(configId, coreName)

    suspend fun updateCoreNameForGame(gameId: Long, coreName: String?) =
        emulatorConfigDao.updateCoreNameForGame(gameId, coreName)

    suspend fun updateCoreNameForPlatform(platformId: Long, coreName: String?) =
        emulatorConfigDao.updateCoreNameForPlatform(platformId, coreName)

    suspend fun getPreferredExtension(platformId: Long): String? =
        emulatorConfigDao.getPreferredExtension(platformId)

    suspend fun updatePreferredExtension(platformId: Long, extension: String) =
        emulatorConfigDao.updatePreferredExtension(platformId, extension)

    suspend fun updateUseFileUriForPlatform(platformId: Long, useFileUri: Boolean) =
        emulatorConfigDao.updateUseFileUriForPlatform(platformId, useFileUri)

    suspend fun getDisplayTargetForPlatform(platformId: Long): String? =
        emulatorConfigDao.getDisplayTargetForPlatform(platformId)

    suspend fun updateDisplayTargetForPlatform(platformId: Long, displayTarget: String?) =
        emulatorConfigDao.updateDisplayTargetForPlatform(platformId, displayTarget)

    suspend fun clearPlatformConfigsByPackage(packageName: String) =
        emulatorConfigDao.clearPlatformConfigsByPackage(packageName)

    fun observeAssignedPackageNames(): Flow<List<String>> =
        emulatorConfigDao.observeAssignedPackageNames()
}
