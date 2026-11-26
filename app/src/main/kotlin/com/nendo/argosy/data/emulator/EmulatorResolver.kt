package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SessionEmulator(val packageName: String, val emulatorId: String)

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

    /**
     * The id save and state path resolution expect: family-variant defs collapse to their
     * base id, since [RetroArchPathResolver] and [StatePathRegistry] key on exact ids.
     */
    fun canonicalEmulatorId(def: EmulatorDef): String = resolveEmulatorId(def.packageName) ?: def.id

    suspend fun ensureDetected() {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
    }

    suspend fun getEmulatorPackageForGame(gameId: Long, platformId: Long, platformSlug: String): String? =
        resolveEmulatorForGame(gameId, platformId, platformSlug)?.packageName

    /**
     * The emulator a game launches with: the game override, then the platform default, each as a
     * known emulator or an ad-hoc app bound by package, then the platform's preferred installed
     * emulator. GameLauncher
     * launches with this def and save sync resolves paths from its package, so both read here.
     */
    suspend fun resolveEmulatorForGame(gameId: Long, platformId: Long, platformSlug: String): EmulatorDef? {
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

        gameOverride.acceptableLaunchPackage(platformSlug, installedPackages, builtinEnabled)
            ?.let { return emulatorDetector.getByPackage(it) }
        gameOverride.adHocDef(platformSlug)?.let { return it }
        platformDefault.acceptableLaunchPackage(platformSlug, installedPackages, builtinEnabled)
            ?.let { return emulatorDetector.getByPackage(it) }
        platformDefault.adHocDef(platformSlug)?.let { return it }

        return emulatorDetector.getPreferredEmulator(platformSlug, builtinEnabled)?.def
    }

    private fun EmulatorConfigEntity?.adHocDef(platformSlug: String): EmulatorDef? {
        val pkg = adHocPackageOrNull() ?: return null
        return EmulatorRegistry.synthesizeAdHocEmulatorDef(
            packageName = pkg,
            displayName = this?.displayName ?: pkg,
            platformSlug = platformSlug
        )
    }

    /**
     * True when the game launches with an app Argosy does not know. Such a game has no save
     * locations Argosy can read, so save sync leaves it alone.
     */
    suspend fun launchesAdHoc(gameId: Long, platformId: Long, platformSlug: String): Boolean =
        resolveEmulatorForGame(gameId, platformId, platformSlug)?.let(EmulatorRegistry::isAdHoc) == true

    suspend fun getEmulatorIdForGame(gameId: Long, platformId: Long, platformSlug: String): String? {
        return getEmulatorPackageForGame(gameId, platformId, platformSlug)?.let { resolveEmulatorId(it) }
    }

    /**
     * The emulator a finished session's saves and states belong to. A non-blank [sessionPackage]
     * that resolves to a known emulator wins; a blank or unknown one falls back to
     * [getEmulatorPackageForGame].
     */
    suspend fun resolveSessionEmulator(
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        sessionPackage: String?
    ): SessionEmulator? {
        sessionPackage?.takeIf { it.isNotBlank() }?.let { pkg ->
            resolveEmulatorId(pkg)?.let { return SessionEmulator(pkg, it) }
        }
        val launchPackage = getEmulatorPackageForGame(gameId, platformId, platformSlug) ?: return null
        val launchId = resolveEmulatorId(launchPackage) ?: return null
        return SessionEmulator(launchPackage, launchId)
    }

    fun getInstalledForPlatform(platformSlug: String): List<InstalledEmulator> {
        return emulatorDetector.getInstalledForPlatform(platformSlug)
    }

    fun getPreferredEmulator(platformSlug: String): InstalledEmulator? {
        return emulatorDetector.getPreferredEmulator(platformSlug)
    }

    /**
     * Preferred emulator for a platform honouring the built-in toggle, as the id save and
     * state paths key on. Used where a configured package resolves to no known emulator.
     */
    suspend fun getPreferredEmulatorId(platformSlug: String): String? {
        val builtinEnabled = userPreferencesRepository.userPreferences.first().builtinLibretroEnabled
        val def = emulatorDetector.getPreferredEmulator(platformSlug, builtinEnabled)?.def ?: return null
        return canonicalEmulatorId(def)
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
