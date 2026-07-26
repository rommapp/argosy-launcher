package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorResolver @Inject constructor(
    private val emulatorDetector: EmulatorDetector,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libretroCoreMgr: LibretroCoreManager,
    private val installedAppResolver: InstalledAppResolver
) {
    fun resolveEmulatorId(packageName: String): String? {
        EmulatorRegistry.getByPackage(packageName)?.let { return it.id }
        EmulatorRegistry.findFamilyForPackage(packageName)?.let { return it.baseId }
        return emulatorDetector.getByPackage(packageName)?.id
    }

    suspend fun ensureDetected() {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
    }

    suspend fun getEmulatorForGame(
        gameId: Long,
        platformId: Long,
        platformSlug: String
    ): EmulatorDef? {
        val gameOverride = emulatorConfigDao.getByGameId(gameId)
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(platformId)
        return resolveEmulator(platformSlug, gameOverride, platformDefault)
    }

    suspend fun getEmulatorForPlatform(platformId: Long, platformSlug: String): EmulatorDef? {
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(platformId)
        return resolveEmulator(platformSlug, platformDefault)
    }

    suspend fun getEmulatorPackageForGame(gameId: Long, platformId: Long, platformSlug: String): String? {
        return getEmulatorForGame(gameId, platformId, platformSlug)?.packageName
    }

    suspend fun getEmulatorIdForGame(gameId: Long, platformId: Long, platformSlug: String): String? {
        return getEmulatorForGame(gameId, platformId, platformSlug)?.id
    }

    suspend fun resolveCoreSelectionForGame(
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        emulator: EmulatorDef
    ): ResolvedCoreSelection? {
        if (!emulator.launchConfig.isCoreSelectable) return null
        val gameOverride = emulatorConfigDao.getByGameId(gameId)
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(platformId)
        val legacyCore = if (emulator.launchConfig is LaunchConfig.BuiltIn) {
            userPreferencesRepository.getBuiltinCoreSelections().first()[platformSlug]
        } else {
            null
        }
        return EmulatorRegistry.resolveCoreSelection(
            platformId = platformSlug,
            isBuiltIn = emulator.launchConfig is LaunchConfig.BuiltIn,
            storedCoreIds = listOf(gameOverride?.coreName, platformDefault?.coreName, legacyCore)
        )
    }

    suspend fun resolveCoreSelectionForPlatform(
        platformId: Long,
        platformSlug: String,
        emulator: EmulatorDef
    ): ResolvedCoreSelection? {
        if (!emulator.launchConfig.isCoreSelectable) return null
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(platformId)
        val legacyCore = if (emulator.launchConfig is LaunchConfig.BuiltIn) {
            userPreferencesRepository.getBuiltinCoreSelections().first()[platformSlug]
        } else {
            null
        }
        return EmulatorRegistry.resolveCoreSelection(
            platformId = platformSlug,
            isBuiltIn = emulator.launchConfig is LaunchConfig.BuiltIn,
            storedCoreIds = listOf(platformDefault?.coreName, legacyCore)
        )
    }

    fun getInstalledForPlatform(platformSlug: String): List<InstalledEmulator> {
        return emulatorDetector.getInstalledForPlatform(platformSlug)
    }

    fun getPreferredEmulator(platformSlug: String): InstalledEmulator? {
        return emulatorDetector.getPreferredEmulator(platformSlug)
    }

    private suspend fun resolveEmulator(
        platformSlug: String,
        vararg configs: EmulatorConfigEntity?
    ): EmulatorDef? {
        ensureDetected()

        val builtinEnabled = userPreferencesRepository.userPreferences.first().builtinLibretroEnabled
        var installedPackages = emulatorDetector.installedEmulators.value
            .map { it.def.packageName }
            .toSet()

        val configuredPackages = configs.mapNotNull { it?.packageName }
        if (configuredPackages.any { it !in installedPackages }) {
            emulatorDetector.detectEmulators()
            installedPackages = emulatorDetector.installedEmulators.value
                .map { it.def.packageName }
                .toSet()
        }

        configs.forEach { config ->
            resolveConfiguredEmulator(
                config = config,
                platformSlug = platformSlug,
                installedPackages = installedPackages,
                builtinEnabled = builtinEnabled
            )?.let { return it }
        }

        val allowBuiltin = builtinEnabled && libretroCoreMgr.isPlatformSupported(platformSlug)
        return emulatorDetector.getPreferredEmulator(platformSlug, allowBuiltin)?.def
    }

    private fun resolveConfiguredEmulator(
        config: EmulatorConfigEntity?,
        platformSlug: String,
        installedPackages: Set<String>,
        builtinEnabled: Boolean
    ): EmulatorDef? {
        val packageName = config?.packageName ?: return null
        if (packageName in installedPackages) {
            val emulator = emulatorDetector.getByPackage(packageName) ?: return null
            val canonicalPlatform = PlatformDefinitions.getCanonicalSlug(platformSlug)
            if (canonicalPlatform !in emulator.supportedPlatforms) return null
            if (packageName == EmulatorRegistry.BUILTIN_PACKAGE &&
                (!builtinEnabled || !libretroCoreMgr.isPlatformSupported(platformSlug))) {
                return null
            }
            return emulator
        }
        if (EmulatorRegistry.isKnownPackage(packageName) ||
            !installedAppResolver.isAppInstalled(packageName)) {
            return null
        }
        return EmulatorRegistry.synthesizeAdHocEmulatorDef(
            packageName = packageName,
            displayName = config.displayName ?: packageName,
            platformSlug = platformSlug
        )
    }
}
