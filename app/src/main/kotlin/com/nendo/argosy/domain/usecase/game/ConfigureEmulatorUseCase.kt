package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import javax.inject.Inject

class ConfigureEmulatorUseCase @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao,
    private val saveSyncDao: SaveSyncDao
) {
    suspend fun setForGame(gameId: Long, platformId: Long, platformSlug: String, emulator: InstalledEmulator?) {
        val hadSavePath = emulatorConfigDao.getByGameId(gameId)?.savePath != null
        emulatorConfigDao.deleteGameOverride(gameId)

        if (emulator != null) {
            val config = EmulatorConfigEntity(
                platformId = platformId,
                gameId = gameId,
                packageName = emulator.def.packageName,
                displayName = emulator.def.displayName,
                coreName = EmulatorRegistry.getDefaultCore(platformSlug)?.id,
                isDefault = false
            )
            emulatorConfigDao.insert(config)
        }
        if (hadSavePath) saveSyncDao.clearLocalPathsForGame(gameId)
    }

    suspend fun setForPlatform(platformId: Long, platformSlug: String, emulator: InstalledEmulator?) {
        emulatorConfigDao.clearPlatformDefaults(platformId)

        if (emulator != null) {
            val config = EmulatorConfigEntity(
                platformId = platformId,
                gameId = null,
                packageName = emulator.def.packageName,
                displayName = emulator.def.displayName,
                coreName = EmulatorRegistry.getDefaultCore(platformSlug)?.id,
                isDefault = true
            )
            emulatorConfigDao.insert(config)
        }
    }

    suspend fun setAdHocForPlatform(platformId: Long, packageName: String, displayName: String) {
        emulatorConfigDao.clearPlatformDefaults(platformId)
        val config = EmulatorConfigEntity(
            platformId = platformId,
            gameId = null,
            packageName = packageName,
            displayName = displayName,
            coreName = null,
            isDefault = true
        )
        emulatorConfigDao.insert(config)
    }

    suspend fun clearForGame(gameId: Long) {
        val hadSavePath = emulatorConfigDao.getByGameId(gameId)?.savePath != null
        emulatorConfigDao.deleteGameOverride(gameId)
        if (hadSavePath) saveSyncDao.clearLocalPathsForGame(gameId)
    }

    suspend fun clearForPlatform(platformId: Long) {
        emulatorConfigDao.clearPlatformDefaults(platformId)
    }

    suspend fun setCoreForPlatform(platformId: Long, coreId: String?) {
        val existing = emulatorConfigDao.getDefaultForPlatform(platformId)
        if (existing != null) {
            emulatorConfigDao.updateCoreNameForPlatform(platformId, coreId)
        } else {
            val config = EmulatorConfigEntity(
                platformId = platformId,
                gameId = null,
                packageName = null,
                displayName = null,
                coreName = coreId,
                isDefault = true
            )
            emulatorConfigDao.insert(config)
        }
    }

    suspend fun setExtensionForPlatform(platformId: Long, extension: String?) {
        val existing = emulatorConfigDao.getDefaultForPlatform(platformId)
        if (existing != null) {
            emulatorConfigDao.updatePreferredExtension(platformId, extension ?: "")
        } else {
            val config = EmulatorConfigEntity(
                platformId = platformId,
                gameId = null,
                packageName = null,
                displayName = null,
                coreName = null,
                preferredExtension = extension,
                isDefault = true
            )
            emulatorConfigDao.insert(config)
        }
    }

    suspend fun setCoreForGame(gameId: Long, coreId: String?) {
        val existing = emulatorConfigDao.getByGameId(gameId)
        if (existing != null) {
            emulatorConfigDao.updateCoreNameForGame(gameId, coreId)
        } else if (coreId != null) {
            val config = EmulatorConfigEntity(
                platformId = null,
                gameId = gameId,
                packageName = null,
                displayName = null,
                coreName = coreId,
                isDefault = false
            )
            emulatorConfigDao.insert(config)
        }
    }

    suspend fun setSavePathForGame(gameId: Long, path: String) {
        val existing = emulatorConfigDao.getByGameId(gameId)
        if (existing != null) {
            emulatorConfigDao.updateSavePathForGame(gameId, path)
        } else {
            val config = EmulatorConfigEntity(
                platformId = null,
                gameId = gameId,
                packageName = null,
                displayName = null,
                coreName = null,
                isDefault = false,
                savePath = path
            )
            emulatorConfigDao.insert(config)
        }
        saveSyncDao.clearLocalPathsForGame(gameId)
    }

    suspend fun clearSavePathForGame(gameId: Long) {
        val existing = emulatorConfigDao.getByGameId(gameId) ?: return
        if (existing.savePath == null) return
        emulatorConfigDao.updateSavePathForGame(gameId, null)
        saveSyncDao.clearLocalPathsForGame(gameId)
    }

    suspend fun getConfigForPlatform(platformId: Long): EmulatorConfigEntity? {
        return emulatorConfigDao.getDefaultForPlatform(platformId)
    }

    suspend fun getConfigForGame(gameId: Long): EmulatorConfigEntity? {
        return emulatorConfigDao.getByGameId(gameId)
    }

    suspend fun setUseFileUriForPlatform(platformId: Long, useFileUri: Boolean) {
        val existing = emulatorConfigDao.getDefaultForPlatform(platformId)
        if (existing != null) {
            emulatorConfigDao.updateUseFileUriForPlatform(platformId, useFileUri)
        } else {
            emulatorConfigDao.insert(
                EmulatorConfigEntity(
                    platformId = platformId,
                    gameId = null,
                    packageName = null,
                    displayName = null,
                    coreName = null,
                    useFileUri = useFileUri,
                    isDefault = true
                )
            )
        }
    }

    suspend fun setDisplayTargetForPlatform(platformId: Long, displayTarget: String?) {
        val existing = emulatorConfigDao.getDefaultForPlatform(platformId)
        if (existing != null) {
            emulatorConfigDao.updateDisplayTargetForPlatform(platformId, displayTarget)
        } else {
            emulatorConfigDao.insert(
                EmulatorConfigEntity(
                    platformId = platformId,
                    gameId = null,
                    packageName = null,
                    displayName = null,
                    coreName = null,
                    displayTarget = displayTarget,
                    isDefault = true
                )
            )
        }
    }

    suspend fun setDisplayTargetForGame(gameId: Long, displayTarget: String?) {
        val existing = emulatorConfigDao.getByGameId(gameId)
        if (existing != null) {
            emulatorConfigDao.updateDisplayTargetForGame(gameId, displayTarget)
        } else if (displayTarget != null) {
            emulatorConfigDao.insert(
                EmulatorConfigEntity(
                    platformId = null,
                    gameId = gameId,
                    packageName = null,
                    displayName = null,
                    coreName = null,
                    displayTarget = displayTarget,
                    isDefault = false
                )
            )
        }
    }

    suspend fun setExtensionForGame(gameId: Long, extension: String?) {
        val existing = emulatorConfigDao.getByGameId(gameId)
        if (existing != null) {
            emulatorConfigDao.updatePreferredExtensionForGame(gameId, extension)
        } else if (extension != null) {
            emulatorConfigDao.insert(
                EmulatorConfigEntity(
                    platformId = null,
                    gameId = gameId,
                    packageName = null,
                    displayName = null,
                    coreName = null,
                    preferredExtension = extension,
                    isDefault = false
                )
            )
        }
    }

    suspend fun clearBuiltinSelections() {
        emulatorConfigDao.clearPlatformConfigsByPackage(EmulatorRegistry.BUILTIN_PACKAGE)
    }
}
