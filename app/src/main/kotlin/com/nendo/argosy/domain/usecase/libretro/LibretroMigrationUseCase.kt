package com.nendo.argosy.domain.usecase.libretro

import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "LibretroMigration"

class LibretroMigrationUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val coreManager: LibretroCoreManager,
    private val emulatorDetector: EmulatorDetector
) {
    /**
     * Pins every PS2 platform that already has games and no stored default to the emulator it
     * resolved to before the built-in core became the PS2 default, so an existing install keeps
     * launching where it did. Runs once, against a fresh emulator scan; an install with no PS2
     * games yet is never pinned, and a scan that returns nothing leaves the pin for the next start.
     */
    suspend fun pinExistingPs2Emulator() {
        if (preferencesRepository.isPs2StandalonePinDone().first()) return
        val pinned = pinExistingEmulators(setOf(PS2_SLUG)) { slug ->
            emulatorDetector.getPreferredEmulator(slug, builtinEnabled = false)?.def
        }
        if (pinned) preferencesRepository.setPs2StandalonePinDone()
    }

    /**
     * Pins every platform in [BUILTIN_PRIORITY_SLUGS] to the emulator it resolved to before the
     * built-in core led its recommendations, under the same rules as [pinExistingPs2Emulator].
     */
    suspend fun pinExistingBuiltinPriorityEmulators() {
        if (preferencesRepository.isBuiltinPriorityPinDone().first()) return
        val pinned = pinExistingEmulators(BUILTIN_PRIORITY_SLUGS, ::emulatorBeforeBuiltinPriority)
        if (pinned) preferencesRepository.setBuiltinPriorityPinDone()
    }

    private fun emulatorBeforeBuiltinPriority(slug: String): EmulatorDef? {
        val recommended = EmulatorRegistry.getRecommendedEmulators()[slug].orEmpty()
            .filter { it != EmulatorRegistry.BUILTIN_ID }
        val outrankedBuiltin = if (slug in BUILTIN_ALREADY_AHEAD_OF_RETROARCH) {
            recommended.filterNot { it in RETROARCH_IDS }
        } else {
            recommended
        }
        val installed = emulatorDetector.getInstalledForPlatform(slug)
        return outrankedBuiltin.firstNotNullOfOrNull { id -> installed.find { it.def.id == id }?.def }
    }

    private suspend fun pinExistingEmulators(
        slugs: Set<String>,
        previousFor: (String) -> EmulatorDef?
    ): Boolean {
        val platforms = platformDao.getAllPlatforms().filter {
            it.gameCount > 0 && PlatformDefinitions.getCanonicalSlug(it.slug) in slugs
        }
        if (platforms.isNotEmpty()) {
            if (emulatorDetector.detectEmulators().isEmpty()) {
                Log.w(TAG, "Emulator detection returned nothing; emulator pin retried next start")
                return false
            }
            for (platform in platforms) {
                val previous = previousFor(PlatformDefinitions.getCanonicalSlug(platform.slug)) ?: continue
                val existing = emulatorConfigDao.getDefaultForPlatform(platform.id)
                if (!existing?.packageName.isNullOrEmpty()) continue
                emulatorConfigDao.insert(
                    existing?.copy(packageName = previous.packageName, displayName = previous.displayName)
                        ?: EmulatorConfigEntity(
                            platformId = platform.id,
                            gameId = null,
                            packageName = previous.packageName,
                            displayName = previous.displayName,
                            coreName = null,
                            isDefault = true
                        )
                )
                Log.i(TAG, "Pinned ${platform.slug} (${platform.id}) to ${previous.packageName}")
            }
        }
        return true
    }

    suspend fun runMigrationIfNeeded(): MigrationResult {
        if (preferencesRepository.isBuiltinMigrationComplete().first()) {
            Log.d(TAG, "Migration already complete, skipping")
            return MigrationResult.AlreadyComplete
        }

        Log.i(TAG, "Starting built-in emulator migration for existing users")

        val platformsWithGames = platformDao.getSyncEnabledPlatforms()
        if (platformsWithGames.isEmpty()) {
            Log.d(TAG, "No sync-enabled platforms, marking migration complete")
            preferencesRepository.setBuiltinMigrationComplete()
            return MigrationResult.NoPlatforms
        }

        val supportedPlatformSlugs = platformsWithGames
            .map { it.slug }
            .filter { LibretroCoreRegistry.isPlatformSupported(it) }
            .toSet()

        if (supportedPlatformSlugs.isEmpty()) {
            Log.d(TAG, "No supported platforms found, marking migration complete")
            preferencesRepository.setBuiltinMigrationComplete()
            return MigrationResult.NoSupportedPlatforms
        }

        Log.i(TAG, "Found ${supportedPlatformSlugs.size} supported platforms: $supportedPlatformSlugs")

        val missingCores = coreManager.getMissingCoresForPlatforms(supportedPlatformSlugs)
        val downloadedCores = mutableListOf<String>()
        val failedCores = mutableListOf<String>()

        for (coreInfo in missingCores) {
            Log.i(TAG, "Downloading core: ${coreInfo.displayName}")
            val result = coreManager.downloadCoreById(coreInfo.coreId)
            if (result.isSuccess) {
                downloadedCores.add(coreInfo.displayName)
                Log.i(TAG, "Successfully downloaded ${coreInfo.displayName}")
            } else {
                failedCores.add(coreInfo.displayName)
                Log.e(TAG, "Failed to download ${coreInfo.displayName}", result.exceptionOrNull())
            }
        }

        var platformsSetToBuiltin = 0
        for (platform in platformsWithGames) {
            if (platform.slug !in supportedPlatformSlugs) continue

            val existingConfig = emulatorConfigDao.getDefaultForPlatform(platform.id)
            val isUserConfigured = existingConfig != null &&
                !existingConfig.packageName.isNullOrEmpty() &&
                existingConfig.packageName != EmulatorRegistry.BUILTIN_PACKAGE

            if (!isUserConfigured) {
                Log.i(TAG, "Setting ${platform.slug} to built-in (previous: ${existingConfig?.packageName})")
                if (existingConfig != null) {
                    emulatorConfigDao.clearPlatformDefaults(platform.id)
                }
                emulatorConfigDao.insert(
                    EmulatorConfigEntity(
                        platformId = platform.id,
                        gameId = null,
                        packageName = EmulatorRegistry.BUILTIN_PACKAGE,
                        displayName = "Built-in",
                        coreName = null,
                        isDefault = true
                    )
                )
                platformsSetToBuiltin++
            } else {
                Log.d(TAG, "Platform ${platform.slug} has user-configured emulator: ${existingConfig.packageName}")
            }
        }

        preferencesRepository.setBuiltinMigrationComplete()

        Log.i(TAG, "Migration complete: downloaded ${downloadedCores.size} cores, " +
            "failed ${failedCores.size}, set $platformsSetToBuiltin platforms to built-in")

        return MigrationResult.Success(
            coresDownloaded = downloadedCores,
            coresFailed = failedCores,
            platformsSetToBuiltin = platformsSetToBuiltin
        )
    }

    suspend fun cleanupRemovedCores() {
        for (coreId in REMOVED_CORE_IDS) {
            emulatorConfigDao.clearCoreName(coreId)
            preferencesRepository.removeBuiltinCoreSelectionsByCore(coreId)
        }
    }

    private companion object {
        val REMOVED_CORE_IDS = setOf("play")
        const val PS2_SLUG = "ps2"
        val BUILTIN_PRIORITY_SLUGS = setOf(
            "3ds", "gc", "wii", "dreamcast", "arcade", "fbneo", "mame", "cps1", "cps2", "cps3",
            "neogeo", "dos", "scummvm", "lynx", "tgcd", "ngp", "ngpc", "c64", "pc9800"
        )
        val BUILTIN_ALREADY_AHEAD_OF_RETROARCH = setOf("3ds")
        val RETROARCH_IDS = setOf("retroarch", "retroarch_64", "retroarch_32")
    }
}

sealed class MigrationResult {
    data object AlreadyComplete : MigrationResult()
    data object NoPlatforms : MigrationResult()
    data object NoSupportedPlatforms : MigrationResult()
    data class Success(
        val coresDownloaded: List<String>,
        val coresFailed: List<String>,
        val platformsSetToBuiltin: Int
    ) : MigrationResult()
}
