package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
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

    suspend fun getEmulatorPackageForGame(gameId: Long, platformId: Long, platformSlug: String): String? {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        val builtinEnabled = userPreferencesRepository.userPreferences.first().builtinLibretroEnabled

        var installedPackages = emulatorDetector.installedEmulators.value
            .map { it.def.packageName }
            .toSet()

        val gameOverride = emulatorConfigDao.getByGameId(gameId)
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(platformId)

        val configuredPackage = gameOverride?.packageName ?: platformDefault?.packageName
        if (configuredPackage != null && configuredPackage !in installedPackages) {
            emulatorDetector.detectEmulators()
            installedPackages = emulatorDetector.installedEmulators.value
                .map { it.def.packageName }
                .toSet()
        }

        gameOverride.acceptableLaunchPackage(platformSlug, installedPackages, builtinEnabled)?.let { return it }
        platformDefault.acceptableLaunchPackage(platformSlug, installedPackages, builtinEnabled)?.let { return it }

        val adHocPackage = gameOverride.adHocPackageOrNull() ?: platformDefault.adHocPackageOrNull()
        if (adHocPackage != null) return adHocPackage

        return emulatorDetector.getPreferredEmulator(platformSlug, builtinEnabled)?.def?.packageName
    }

    suspend fun getEmulatorIdForGame(gameId: Long, platformId: Long, platformSlug: String): String? {
        return getEmulatorPackageForGame(gameId, platformId, platformSlug)?.let { resolveEmulatorId(it) }
    }

    fun getInstalledForPlatform(platformSlug: String): List<InstalledEmulator> {
        return emulatorDetector.getInstalledForPlatform(platformSlug)
    }

    fun getPreferredEmulator(platformSlug: String): InstalledEmulator? {
        return emulatorDetector.getPreferredEmulator(platformSlug)
    }

    private fun EmulatorConfigEntity?.acceptableLaunchPackage(
        platformSlug: String,
        installedPackages: Set<String>,
        builtinEnabled: Boolean
    ): String? {
        val pkg = this?.packageName ?: return null
        if (pkg !in installedPackages) return null
        if (pkg == EmulatorRegistry.BUILTIN_PACKAGE &&
            (!builtinEnabled || !libretroCoreMgr.isPlatformSupported(platformSlug))) {
            return null
        }
        return pkg
    }

    private fun EmulatorConfigEntity?.adHocPackageOrNull(): String? {
        val pkg = this?.packageName ?: return null
        if (EmulatorRegistry.isKnownPackage(pkg)) return null
        if (!installedAppResolver.isAppInstalled(pkg)) return null
        return pkg
    }
}
