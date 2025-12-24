package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.repository.StateCacheManager
import java.io.File
import javax.inject.Inject

private const val TAG = "RestoreCachedStates"

sealed class RestoreCachedStatesResult {
    data class Success(val restoredCount: Int) : RestoreCachedStatesResult()
    data class Error(val message: String) : RestoreCachedStatesResult()
    data object NotConfigured : RestoreCachedStatesResult()
    data object NoStates : RestoreCachedStatesResult()
}

class RestoreCachedStatesUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val stateCacheDao: StateCacheDao,
    private val gameDao: GameDao,
    private val emulatorDetector: EmulatorDetector,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val retroArchConfigParser: RetroArchConfigParser
) {
    suspend operator fun invoke(
        gameId: Long,
        channelName: String?,
        emulatorPackage: String,
        coreId: String? = null
    ): RestoreCachedStatesResult {
        val game = gameDao.getById(gameId)
        if (game == null) {
            Log.w(TAG, "Game not found: $gameId")
            return RestoreCachedStatesResult.Error("Game not found")
        }

        val romPath = game.localPath
        if (romPath == null) {
            Log.w(TAG, "Game has no local path: $gameId")
            return RestoreCachedStatesResult.Error("Game has no local path")
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

        val statePaths = if (emulatorId.startsWith("retroarch")) {
            retroArchConfigParser.resolveStatePaths(emulatorPackage, effectiveCoreId, contentDir)
        } else {
            StatePathRegistry.resolvePath(config, game.platformSlug)
        }

        val stateDir = statePaths.map { File(it) }.firstOrNull { it.exists() && it.isDirectory }
        if (stateDir == null) {
            Log.d(TAG, "No existing state directory found, will create: ${statePaths.firstOrNull()}")
        }
        val targetDir = stateDir ?: statePaths.firstOrNull()?.let { File(it) }
        if (targetDir == null) {
            return RestoreCachedStatesResult.Error("Could not determine state directory")
        }

        val cachedStates = stateCacheDao.getByChannelAndCore(gameId, channelName, effectiveCoreId)

        if (cachedStates.isEmpty()) {
            Log.d(TAG, "No cached states for channel ${channelName ?: "default"} core ${effectiveCoreId ?: "unknown"}")
        }

        try {
            targetDir.mkdirs()

            val existingStates = targetDir.listFiles()?.filter { file ->
                val slotNumber = config.slotPattern.parseSlotNumber(file.name, romBaseName)
                slotNumber != null
            } ?: emptyList()

            for (existingFile in existingStates) {
                existingFile.delete()
                val screenshotFile = File("${existingFile.absolutePath}.png")
                if (screenshotFile.exists()) {
                    screenshotFile.delete()
                }
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

                restoredCount++
                Log.d(TAG, "Restored state slot ${state.slotNumber} to ${targetFile.absolutePath}")
            }

            Log.d(TAG, "Restored $restoredCount states for channel ${channelName ?: "default"} core ${effectiveCoreId ?: "unknown"}")
            return RestoreCachedStatesResult.Success(restoredCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore states", e)
            return RestoreCachedStatesResult.Error(e.message ?: "Unknown error")
        }
    }
}
