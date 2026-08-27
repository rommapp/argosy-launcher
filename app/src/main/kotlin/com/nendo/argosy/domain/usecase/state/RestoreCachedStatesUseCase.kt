package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import java.io.File
import javax.inject.Inject

private const val TAG = "RestoreCachedStates"

sealed class RestoreCachedStatesResult {
    data class Success(val restoredCount: Int) : RestoreCachedStatesResult()
    data class Error(val reason: RestoreCachedStatesFailureReason) : RestoreCachedStatesResult()
    data object NotConfigured : RestoreCachedStatesResult()
    data object NoStates : RestoreCachedStatesResult()
}

/**
 * Why [RestoreCachedStatesUseCase] could not put cached states back on disk. [Unexpected] keeps
 * the exception text as-is because it is not this app's sentence to translate.
 */
sealed class RestoreCachedStatesFailureReason {
    data object GameNotFound : RestoreCachedStatesFailureReason()
    data object NoLocalPath : RestoreCachedStatesFailureReason()
    data object StateDirectoryUnresolved : RestoreCachedStatesFailureReason()
    data class Unexpected(val message: String?) : RestoreCachedStatesFailureReason()
}

class RestoreCachedStatesUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val gameDao: GameDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
    private val libretroStatePathResolver: com.nendo.argosy.data.emulator.LibretroStatePathResolver,
    private val stateOwnershipTracker: StateOwnershipTracker,
) {
    suspend operator fun invoke(
        gameId: Long,
        channelName: String?,
        emulatorPackage: String,
        coreId: String? = null,
        skipAutoState: Boolean = false
    ): RestoreCachedStatesResult {
        val game = gameDao.getById(gameId)
        if (game == null) {
            Log.w(TAG, "Game not found: $gameId")
            return RestoreCachedStatesResult.Error(RestoreCachedStatesFailureReason.GameNotFound)
        }

        val romPath = game.localPath
        if (romPath == null) {
            Log.w(TAG, "Game has no local path: $gameId")
            return RestoreCachedStatesResult.Error(RestoreCachedStatesFailureReason.NoLocalPath)
        }

        val emulatorDef = emulatorDetector.getByPackage(emulatorPackage)
        if (emulatorDef == null) {
            Log.w(TAG, "Unknown emulator: $emulatorPackage")
            return RestoreCachedStatesResult.NotConfigured
        }
        val emulatorId = emulatorDef.id

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return RestoreCachedStatesResult.NotConfigured
        }

        val effectiveCoreId = coreId ?: coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)

        val romFile = File(romPath)
        val romBaseName = romFile.nameWithoutExtension
        val contentDir = romFile.parentFile?.absolutePath
        val contentDirName = romFile.parentFile?.name

        val userStateOverride = emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserStateOverride }
            ?.statePathPattern

        val statePaths = when {
            com.nendo.argosy.data.emulator.RetroArchPathResolver.isRetroArch(emulatorId) -> {
                val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                    emulatorId = emulatorId,
                    coreName = effectiveCoreId,
                    romPath = romPath,
                )
                retroArchPathResolver.resolveStateDirectories(req)
            }
            userStateOverride != null -> listOf(userStateOverride)
            emulatorId == "builtin" -> listOf(libretroStatePathResolver.liveStateBaseDir(gameId).absolutePath)
            else -> StatePathRegistry.resolvePath(config, game.platformSlug)
        }

        val stateDir = statePaths.map { File(it) }.firstOrNull { it.exists() && it.isDirectory }
        if (stateDir == null) {
            Log.d(TAG, "No existing state directory found, will create: ${statePaths.firstOrNull()}")
        }
        val targetDir = stateDir ?: statePaths.firstOrNull()?.let { File(it) }
        if (targetDir == null) {
            return RestoreCachedStatesResult.Error(RestoreCachedStatesFailureReason.StateDirectoryUnresolved)
        }

        val cachedStates = stateCacheManager.getStatesForChannelAndCore(gameId, channelName, effectiveCoreId)
            .let { states ->
                if (skipAutoState) states.filter { it.slotNumber != -1 } else states
            }

        if (cachedStates.isEmpty()) {
            Log.d(TAG, "No cached states for channel ${channelName ?: "default"} core ${effectiveCoreId ?: "unknown"}" +
                if (skipAutoState) " (auto-state skipped)" else "")
        }

        try {
            targetDir.mkdirs()

            val existingStates = targetDir.listFiles()?.filter { file ->
                val slotNumber = config.slotPattern.parseSlotNumber(file.name, romBaseName)
                slotNumber != null
            } ?: emptyList()

            for (existingFile in existingStates) {
                val existingPath = existingFile.absolutePath
                val claim = stateOwnershipTracker.claim(existingPath, emulatorId)
                if (claim is StateClaim.Foreign) {
                    val slotNumber = config.slotPattern.parseSlotNumber(existingFile.name, romBaseName)
                    val archived = slotNumber != null && stateCacheManager.cacheState(
                        gameId = gameId,
                        platformSlug = game.platformSlug,
                        emulatorId = emulatorId,
                        slotNumber = slotNumber,
                        statePath = existingPath,
                        coreId = effectiveCoreId,
                        channelName = channelName,
                        ownerUserIdOverride = claim.ownerUserId
                    ) != null
                    if (!archived) {
                        Log.w(TAG, "Leaving ${existingFile.name} in place: belongs to user ${claim.ownerUserId} and could not be archived")
                        continue
                    }
                }
                existingFile.delete()
                val screenshotFile = File("$existingPath.png")
                if (screenshotFile.exists()) {
                    screenshotFile.delete()
                }
                stateOwnershipTracker.clear(existingPath, emulatorId)
                Log.d(TAG, "Deleted existing state: ${existingFile.name}")
            }

            var restoredCount = 0
            for (state in cachedStates) {
                val cacheFile = stateCacheManager.getCacheFile(state)
                if (cacheFile == null) {
                    Log.w(TAG, "Cache file not found for state ${state.id}")
                    continue
                }

                val targetFile = File(targetDir, cacheFile.name)
                cacheFile.copyTo(targetFile, overwrite = true)

                val screenshotCacheFile = stateCacheManager.getScreenshotFile(state)
                if (screenshotCacheFile != null) {
                    val screenshotTarget = File(targetDir, screenshotCacheFile.name)
                    screenshotCacheFile.copyTo(screenshotTarget, overwrite = true)
                }

                stateOwnershipTracker.record(
                    statePath = targetFile.absolutePath,
                    emulatorId = emulatorId,
                    contentHash = stateCacheManager.calculateLiveStateHash(targetFile.absolutePath),
                    gameId = gameId,
                    slotNumber = state.slotNumber,
                    channelName = state.channelName,
                    coreId = state.coreId,
                    ownerUserIdOverride = state.ownerUserId
                )

                restoredCount++
                Log.d(TAG, "Restored state slot ${state.slotNumber} to ${targetFile.absolutePath}")
            }

            Log.d(TAG, "Restored $restoredCount states for channel ${channelName ?: "default"} core ${effectiveCoreId ?: "unknown"}")
            return RestoreCachedStatesResult.Success(restoredCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore states", e)
            return RestoreCachedStatesResult.Error(RestoreCachedStatesFailureReason.Unexpected(e.message))
        }
    }
}
