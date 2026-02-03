package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.util.Logger
import java.time.Instant
import javax.inject.Inject

class PreLaunchSyncUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver
) {
    companion object {
        private const val TAG = "PreLaunchSync"
    }

    sealed class Result {
        data object Ready : Result()
        data object NoConnection : Result()
        data class ServerNewer(val serverTimestamp: Instant) : Result()
        data class LocalModified(val localSavePath: String, val channelName: String?) : Result()
    }

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        Logger.debug(TAG, "Pre-launch sync check for gameId=$gameId, emulator=$emulatorPackage")

        val game = gameDao.getById(gameId) ?: return Result.Ready
        val rommId = game.rommId ?: run {
            Logger.debug(TAG, "Game ${game.title} has no rommId, skipping pre-launch sync")
            return Result.Ready
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val packageToResolve = emulatorConfig?.packageName ?: emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(packageToResolve)
            ?: run {
                Logger.debug(TAG, "Cannot resolve emulator for package: $packageToResolve")
                return Result.Ready
            }

        Logger.debug(TAG, "Checking server saves for ${game.title}")

        return when (val syncResult = saveSyncRepository.preLaunchSync(gameId, rommId, emulatorId)) {
            is SaveSyncRepository.PreLaunchSyncResult.NoConnection -> {
                Logger.debug(TAG, "No connection for pre-launch sync")
                Result.NoConnection
            }
            is SaveSyncRepository.PreLaunchSyncResult.NoServerSave -> {
                Logger.debug(TAG, "No server save found for ${game.title}")
                Result.Ready
            }
            is SaveSyncRepository.PreLaunchSyncResult.LocalIsNewer -> {
                Logger.debug(TAG, "Local save is newer for ${game.title}")
                Result.Ready
            }
            is SaveSyncRepository.PreLaunchSyncResult.LocalModified -> {
                Logger.info(TAG, "Local save modified for ${game.title}, prompting user")
                Result.LocalModified(syncResult.localSavePath, syncResult.channelName)
            }
            is SaveSyncRepository.PreLaunchSyncResult.ServerIsNewer -> {
                Logger.info(TAG, "Server save is newer for ${game.title} (${syncResult.serverTimestamp})")
                Result.ServerNewer(syncResult.serverTimestamp)
            }
        }
    }
}
