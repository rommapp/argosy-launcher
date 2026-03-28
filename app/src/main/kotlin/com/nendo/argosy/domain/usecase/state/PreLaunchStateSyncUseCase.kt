package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

private const val TAG = "PreLaunchStateSync"

class PreLaunchStateSyncUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val preferencesRepository: UserPreferencesRepository
) {
    sealed class Result {
        data object Ready : Result()
        data object NoConnection : Result()
        data object NotConfigured : Result()
        data class Downloaded(val count: Int) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        Log.d(TAG, "Pre-launch state sync check for gameId=$gameId, emulator=$emulatorPackage")

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            Log.d(TAG, "Save sync disabled, skipping pre-launch state sync")
            return Result.NotConfigured
        }

        val api = saveSyncRepository.getApi()
        if (api == null) {
            Log.d(TAG, "No API connection, skipping pre-launch state sync")
            return Result.NoConnection
        }

        val game = gameDao.getById(gameId) ?: return Result.Ready
        val rommId = game.rommId ?: run {
            Log.d(TAG, "Game ${game.title} has no rommId, skipping pre-launch state sync")
            return Result.Ready
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val packageToResolve = emulatorConfig?.packageName ?: emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(packageToResolve)
            ?: run {
                Log.d(TAG, "Cannot resolve emulator for package: $packageToResolve")
                return Result.Ready
            }

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return Result.NotConfigured
        }

        val coreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)

        Log.d(TAG, "Checking server states for ${game.title}")

        val serverStates = stateCacheManager.checkServerStates(rommId, api)
        if (serverStates.isEmpty()) {
            Log.d(TAG, "No server states found for ${game.title}")
            return Result.Ready
        }

        Log.d(TAG, "Found ${serverStates.size} server states for ${game.title}")

        val localStates = stateCacheManager.getByGameAndEmulator(gameId, emulatorId)
        val localByRommId = localStates.filter { it.rommSaveId != null }.associateBy { it.rommSaveId }

        var downloadedCount = 0

        for (serverState in serverStates) {
            val localState = localByRommId[serverState.id]
            val parsed = stateCacheManager.parseStateFileName(serverState.fileName)
            val slotNumber = parsed.slotNumber

            val shouldDownload = when {
                localState == null -> {
                    Log.d(TAG, "Server state ${serverState.fileName} (slot $slotNumber) not cached locally")
                    true
                }
                localState.syncStatus == StateCacheEntity.STATUS_SERVER_NEWER -> {
                    Log.d(TAG, "Server state ${serverState.fileName} marked as newer")
                    true
                }
                else -> {
                    false
                }
            }

            if (shouldDownload) {
                val result = stateCacheManager.downloadStateFromRomM(
                    rommStateId = serverState.id,
                    fileName = serverState.fileName,
                    api = api,
                    gameId = gameId,
                    platformSlug = game.platformSlug,
                    emulatorId = emulatorId,
                    coreId = coreId,
                    serverState = serverState
                )

                when (result) {
                    is StateCacheManager.StateCloudResult.Success -> {
                        downloadedCount++
                        Log.d(TAG, "Downloaded state ${serverState.fileName} for ${game.title}")
                    }
                    is StateCacheManager.StateCloudResult.Error -> {
                        Log.e(TAG, "Failed to download state ${serverState.fileName}: ${result.message}")
                    }
                    else -> {
                        Log.w(TAG, "Download result for ${serverState.fileName}: $result")
                    }
                }
            }
        }

        return if (downloadedCount > 0) {
            Log.i(TAG, "Downloaded $downloadedCount states for ${game.title}")
            Result.Downloaded(downloadedCount)
        } else {
            Result.Ready
        }
    }
}
