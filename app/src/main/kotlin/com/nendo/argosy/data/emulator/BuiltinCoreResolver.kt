package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.dao.getDownloadedForPlatformFamily
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core the built-in libretro path will actually load: game override, then platform
 * default, then the Manage Cores selection, then the registry default. Every candidate
 * must be a core registered for the platform; anything else is rejected and reported so
 * the caller can tell the user their choice was dropped. A registry default with a BIOS fallback
 * counts a BIOS present when the system directory holds it or the downloaded firmware library
 * holds a file distribution would copy there under that name.
 *
 * Settings, game detail and save sync all resolve through this so no screen can show a
 * core the launcher would not load.
 */
@Singleton
class BuiltinCoreResolver(
    private val emulatorConfigDao: EmulatorConfigDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val firmwareDao: FirmwareDao,
    private val systemDir: File
) {
    @Inject
    constructor(
        emulatorConfigDao: EmulatorConfigDao,
        userPreferencesRepository: UserPreferencesRepository,
        firmwareDao: FirmwareDao,
        @ApplicationContext context: Context
    ) : this(emulatorConfigDao, userPreferencesRepository, firmwareDao, AppPaths.libretroSystemDir(context.filesDir))

    data class Resolution(
        val coreId: String?,
        val rejectedCoreId: String? = null
    )

    suspend fun resolve(gameId: Long?, platformId: Long, platformSlug: String): Resolution {
        val validCoreIds = LibretroCoreRegistry.getCoresForPlatform(platformSlug)
            .map { it.coreId }
            .toSet()
        var rejected: String? = null

        fun accept(coreId: String?): String? {
            if (coreId.isNullOrBlank()) return null
            if (coreId !in validCoreIds) {
                if (rejected == null) rejected = coreId
                return null
            }
            return coreId
        }

        if (gameId != null) {
            accept(emulatorConfigDao.getByGameId(gameId)?.coreName)
                ?.let { return Resolution(it, rejected) }
        }
        accept(emulatorConfigDao.getDefaultForPlatform(platformId)?.coreName)
            ?.let { return Resolution(it, rejected) }
        accept(userPreferencesRepository.getBuiltinCoreSelections().first()[platformSlug])
            ?.let { return Resolution(it, rejected) }

        return Resolution(defaultCoreFor(platformSlug)?.coreId, rejected)
    }

    private suspend fun defaultCoreFor(platformSlug: String): LibretroCoreRegistry.CoreInfo? {
        val default = LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug) ?: return null
        val fallbackId = default.biosFallbackCoreId ?: return default
        if (hasAnyBios(platformSlug, default.requiresBios.toSet())) return default
        return LibretroCoreRegistry.getCoreById(fallbackId) ?: default
    }

    private suspend fun hasAnyBios(platformSlug: String, systemFileNames: Set<String>): Boolean {
        val library = firmwareDao.getDownloadedForPlatformFamily(platformSlug)
        val builtinSlug = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return withContext(Dispatchers.IO) {
            systemFileNames.any { File(systemDir, it).isFile } || library.any { firmware ->
                BiosPathRegistry.exactSystemFileName(firmware.fileName, builtinSlug) { firmware.md5Hash } in systemFileNames
            }
        }
    }

    suspend fun resolveCoreId(gameId: Long?, platformId: Long, platformSlug: String): String? =
        resolve(gameId, platformId, platformSlug).coreId
}
