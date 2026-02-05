package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import javax.inject.Inject

class ConfigureEmulatorUseCase @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao
) {
    suspend fun setForGame(gameId: Long, platformId: Long, platformSlug: String, emulator: InstalledEmulator?) {
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

    suspend fun clearForGame(gameId: Long) {
        emulatorConfigDao.deleteGameOverride(gameId)
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

    suspend fun getConfigForPlatform(platformId: Long): EmulatorConfigEntity? {
        return emulatorConfigDao.getDefaultForPlatform(platformId)
    }

    suspend fun getConfigForGame(gameId: Long): EmulatorConfigEntity? {
        return emulatorConfigDao.getByGameId(gameId)
    }

    suspend fun clearBuiltinSelections() {
        emulatorConfigDao.clearPlatformConfigsByPackage(EmulatorRegistry.BUILTIN_PACKAGE)
    }
}
