package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.StateCacheManager
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

private const val TAG = "SyncStatesOnSessionEnd"

sealed class StateSyncResult {
    data class Cached(val count: Int) : StateSyncResult()
    data object NoStatesFound : StateSyncResult()
    data object NotConfigured : StateSyncResult()
    data class Error(val message: String) : StateSyncResult()
}

class SyncStatesOnSessionEndUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val gameDao: GameDao,
    private val emulatorDetector: EmulatorDetector,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        gameId: Long,
        emulatorPackage: String
    ): StateSyncResult {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.stateCacheEnabled) {
            Log.d(TAG, "State caching disabled")
            return StateSyncResult.NotConfigured
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Log.w(TAG, "Game not found: $gameId")
            return StateSyncResult.Error("Game not found")
        }

        val romPath = game.localPath
        if (romPath == null) {
            Log.w(TAG, "Game has no local path: $gameId")
            return StateSyncResult.Error("Game has no local path")
        }

        val emulatorDef = emulatorDetector.getByPackage(emulatorPackage)
        if (emulatorDef == null) {
            Log.w(TAG, "Unknown emulator: $emulatorPackage")
            return StateSyncResult.NotConfigured
        }
        val emulatorId = emulatorDef.id

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return StateSyncResult.NotConfigured
        }

        val coreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)
        val coreVersion = if (coreId != null && emulatorId.startsWith("retroarch")) {
            coreVersionExtractor.getRetroArchCoreVersion(coreId, emulatorPackage)
        } else {
            null
        }

        val discoveredStates = stateCacheManager.discoverStatesForGame(
            gameId = gameId,
            emulatorId = emulatorId,
            romPath = romPath,
            platformId = game.platformSlug,
            emulatorPackage = emulatorPackage,
            coreName = coreId
        )

        if (discoveredStates.isEmpty()) {
            Log.d(TAG, "No states found for game $gameId")
            return StateSyncResult.NoStatesFound
        }

        var cachedCount = 0
        val channelName = game.activeSaveChannel

        for (state in discoveredStates) {
            val existingCache = stateCacheManager.getStateBySlot(
                gameId = gameId,
                emulatorId = emulatorId,
                slotNumber = state.slotNumber,
                channelName = channelName
            )

            val screenshotFile = File("${state.file.absolutePath}.png")
            val screenshotMissing = existingCache?.screenshotPath == null && screenshotFile.exists()

            val isNewer = existingCache != null && state.lastModified.isAfter(existingCache.cachedAt)
            Log.d(TAG, "Slot ${state.slotNumber}: fileModified=${state.lastModified}, cachedAt=${existingCache?.cachedAt}, isNewer=$isNewer, screenshotMissing=$screenshotMissing")

            val shouldCache = existingCache == null ||
                state.lastModified.isAfter(existingCache.cachedAt) ||
                screenshotMissing

            if (shouldCache) {
                val cacheId = stateCacheManager.cacheState(
                    gameId = gameId,
                    platformSlug = game.platformSlug,
                    emulatorId = emulatorId,
                    slotNumber = state.slotNumber,
                    statePath = state.file.absolutePath,
                    coreId = coreId,
                    coreVersion = coreVersion,
                    channelName = channelName,
                    isLocked = channelName != null
                )
                if (cacheId != null) {
                    cachedCount++
                    Log.d(TAG, "Cached state slot ${state.slotNumber} for game $gameId")
                }
            }
        }

        Log.d(TAG, "Cached $cachedCount states for game $gameId")
        return StateSyncResult.Cached(cachedCount)
    }
}
