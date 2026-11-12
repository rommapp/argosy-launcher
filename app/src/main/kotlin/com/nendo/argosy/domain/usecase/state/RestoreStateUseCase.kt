package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.StateNameKey
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.repository.StateCacheManager
import java.io.File
import javax.inject.Inject

sealed class RestoreStateResult {
    data object Success : RestoreStateResult()
    data class VersionMismatch(
        val savedCoreId: String?,
        val savedVersion: String?,
        val currentCoreId: String?,
        val currentVersion: String?
    ) : RestoreStateResult()
    data class Error(val reason: RestoreStateFailureReason) : RestoreStateResult()
    data object NotFound : RestoreStateResult()
    data object NoConfig : RestoreStateResult()
}

/**
 * Why [RestoreStateUseCase] could not write a cached save state back to disk.
 */
sealed class RestoreStateFailureReason {
    data object TargetPathUnresolved : RestoreStateFailureReason()
    data object WriteFailed : RestoreStateFailureReason()
}

class RestoreStateUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val stateSerialResolver: StateSerialResolver
) {
    /**
     * Writes a cached state back to where the emulator will look for it.
     *
     * RetroArch sorts states into a per-core folder by default, so the core has to be known to
     * land the file in the same directory the capture came from - without one the state goes to
     * the parent and the emulator never sees it. [currentCoreId] is what the caller resolved for
     * the running configuration and is also what the version guard compares; the cached row's own
     * core is the fallback for callers that cannot resolve one, so it decides the path only.
     */
    suspend operator fun invoke(
        cacheId: Long,
        emulatorId: String,
        platformId: String,
        romPath: String,
        currentCoreId: String? = null,
        currentCoreVersion: String? = null,
        forceRestore: Boolean = false
    ): RestoreStateResult {
        val cache = stateCacheManager.getStateById(cacheId)
            ?: return RestoreStateResult.NotFound

        val config = StatePathRegistry.getConfig(emulatorId)
            ?: return RestoreStateResult.NoConfig

        if (!forceRestore) {
            val validation = stateCacheManager.validateCoreVersion(
                cacheId = cacheId,
                currentCoreId = currentCoreId,
                currentVersion = currentCoreVersion
            )

            if (validation is VersionValidationResult.Mismatch) {
                return RestoreStateResult.VersionMismatch(
                    savedCoreId = cache.coreId,
                    savedVersion = cache.coreVersion,
                    currentCoreId = currentCoreId,
                    currentVersion = currentCoreVersion
                )
            }
        }

        val romFile = File(romPath)
        val romBaseName = if (emulatorId == EmulatorRegistry.BUILTIN_ID) {
            ArchiveRomNaming.liveBaseName(romFile, platformId)
        } else {
            romFile.nameWithoutExtension
        }
        val nameKey = StateNameKey(romBaseName, stateSerialResolver.serialsFor(cache.gameId, emulatorId))
        val targetPath = stateCacheManager.buildStateTargetPath(
            config = config,
            platformId = platformId,
            key = nameKey,
            slotNumber = cache.slotNumber,
            cachedFileName = File(cache.cachePath).name,
            cachedFile = File(cache.cachePath),
            emulatorId = emulatorId,
            coreName = currentCoreId ?: cache.coreId,
            romPath = romPath,
            gameId = cache.gameId,
        ) ?: return RestoreStateResult.Error(RestoreStateFailureReason.TargetPathUnresolved)

        val success = stateCacheManager.restoreState(cacheId, targetPath)
        return if (success) {
            RestoreStateResult.Success
        } else {
            RestoreStateResult.Error(RestoreStateFailureReason.WriteFailed)
        }
    }
}
