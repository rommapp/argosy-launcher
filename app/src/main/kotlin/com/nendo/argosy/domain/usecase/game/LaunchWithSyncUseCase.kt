package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.domain.usecase.state.PreLaunchStateSyncUseCase
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val TAG = "LaunchWithSync"

class LaunchWithSyncUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val titleIdDownloadObserver: TitleIdDownloadObserver,
    private val preLaunchStateSyncUseCase: PreLaunchStateSyncUseCase
) {
    @Deprecated("Use invokeWithProgress instead", ReplaceWith("invokeWithProgress(gameId)"))
    fun invoke(gameId: Long): Flow<SyncState> = flow {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            emit(SyncState.Skipped)
            return@flow
        }

        if (!romMRepository.isConnected()) {
            emit(SyncState.Skipped)
            return@flow
        }

        val game = gameDao.getById(gameId)
        if (game == null || game.rommId == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val preferredEmulator = emulatorResolver.getPreferredEmulator(game.platformSlug)
        val emulatorPackage = emulatorConfig?.packageName ?: preferredEmulator?.def?.packageName
        if (emulatorPackage == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val emulatorId = emulatorResolver.resolveEmulatorId(emulatorPackage)
        if (emulatorId == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        if (!SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)) {
            emit(SyncState.Skipped)
            return@flow
        }

        emit(SyncState.CheckingConnection)

        val syncResult = saveSyncRepository.preLaunchSyncForGame(gameId, game.rommId, emulatorId, channelName = null)

        when (syncResult) {
            is PreLaunchSyncResult.NoConnection -> {
                emit(SyncState.Skipped)
            }
            is PreLaunchSyncResult.NoServerSave -> {
                emit(SyncState.Complete)
            }
            is PreLaunchSyncResult.LocalIsNewer -> {
                emit(SyncState.Complete)
            }
            is PreLaunchSyncResult.LocalModified -> {
                emit(SyncState.LocalModified(gameId, syncResult.localSavePath, syncResult.channelName, syncResult.serverSaveId))
            }
            is PreLaunchSyncResult.ServerIsNewer -> {
                emit(SyncState.Downloading)
                val downloadResult = saveSyncRepository.downloadSave(
                    gameId, emulatorId, syncResult.channelName,
                    knownServerSaveId = syncResult.serverSaveId
                )
                when (downloadResult) {
                    is SaveSyncResult.Success -> {
                        emit(SyncState.Complete)
                    }
                    is SaveSyncResult.Error -> {
                        emit(SyncState.Error(downloadResult.message))
                    }
                    is SaveSyncResult.NeedsHardcoreResolution -> {
                        emit(SyncState.HardcoreConflict(downloadResult.gameId, downloadResult.gameName))
                    }
                    else -> {
                        emit(SyncState.Complete)
                    }
                }
            }
        }
    }

    private suspend fun syncStatesQuietly(gameId: Long, emulatorPackage: String) {
        runCatching { preLaunchStateSyncUseCase(gameId, emulatorPackage) }
            .onFailure { Logger.error(TAG, "Pre-launch state sync failed for gameId=$gameId", it) }
    }

    fun invokeWithProgress(
        gameId: Long,
        channelName: String? = null,
        skipPreLaunchSync: Boolean = false
    ): Flow<SyncProgress> = flow {
        if (channelName != null) {
            val currentChannel = gameDao.getActiveSaveChannel(gameId)
            if (currentChannel != channelName) {
                gameDao.updateActiveSaveChannel(gameId, channelName)
            }
        }

        if (skipPreLaunchSync) {
            emit(SyncProgress.Skipped)
            return@flow
        }

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            emit(SyncProgress.Skipped)
            return@flow
        }

        emit(SyncProgress.PreLaunch.CheckingSave(channelName))

        val game = gameDao.getById(gameId)
        if (game == null || game.rommId == null) {
            emit(SyncProgress.PreLaunch.CheckingSave(channelName, found = false))
            emit(SyncProgress.Skipped)
            return@flow
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val preferredEmulator = emulatorResolver.getPreferredEmulator(game.platformSlug)
        val emulatorPackage = emulatorConfig?.packageName ?: preferredEmulator?.def?.packageName
        if (emulatorPackage == null) {
            emit(SyncProgress.PreLaunch.CheckingSave(channelName, found = false))
            emit(SyncProgress.Skipped)
            return@flow
        }

        val emulatorId = emulatorResolver.resolveEmulatorId(emulatorPackage)
        if (emulatorId == null) {
            emit(SyncProgress.PreLaunch.CheckingSave(channelName, found = false))
            emit(SyncProgress.Skipped)
            return@flow
        }

        if (!SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)) {
            if (romMRepository.isConnected()) {
                syncStatesQuietly(gameId, emulatorPackage)
            }
            emit(SyncProgress.Skipped)
            return@flow
        }

        emit(SyncProgress.PreLaunch.CheckingSave(channelName, found = true))
        emit(SyncProgress.PreLaunch.Connecting(channelName))

        if (!romMRepository.isConnected()) {
            emit(SyncProgress.PreLaunch.Connecting(channelName, success = false))
            emit(SyncProgress.Skipped)
            return@flow
        }

        emit(SyncProgress.PreLaunch.Connecting(channelName, success = true))

        titleIdDownloadObserver.extractTitleIdForGame(gameId)

        saveSyncRepository.crossEmulatorMigrateIfNeeded(gameId, emulatorId)

        val syncResult = coroutineScope {
            val stateSync = async { syncStatesQuietly(gameId, emulatorPackage) }
            val saveSync = saveSyncRepository.preLaunchSyncForGame(gameId, game.rommId, emulatorId, channelName)
            stateSync.await()
            saveSync
        }

        when (syncResult) {
            is PreLaunchSyncResult.NoConnection -> {
                emit(SyncProgress.PreLaunch.Connecting(channelName, success = false))
                emit(SyncProgress.Skipped)
            }
            is PreLaunchSyncResult.NoServerSave -> {
                emit(SyncProgress.PreLaunch.Downloading(channelName, success = true))
                emit(SyncProgress.PreLaunch.Launching(channelName))
            }
            is PreLaunchSyncResult.LocalIsNewer -> {
                emit(SyncProgress.PreLaunch.Downloading(channelName, success = true))
                emit(SyncProgress.PreLaunch.Launching(channelName))
            }
            is PreLaunchSyncResult.LocalModified -> {
                emit(SyncProgress.LocalModified(gameId, syncResult.localSavePath, syncResult.channelName, syncResult.serverSaveId))
            }
            is PreLaunchSyncResult.ServerIsNewer -> {
                emit(SyncProgress.PreLaunch.Downloading(channelName))
                val downloadResult = saveSyncRepository.downloadSave(
                    gameId, emulatorId, syncResult.channelName,
                    knownServerSaveId = syncResult.serverSaveId
                )
                when (downloadResult) {
                    is SaveSyncResult.Success -> {
                        emit(SyncProgress.PreLaunch.Downloading(channelName, success = true))
                        emit(SyncProgress.PreLaunch.Writing(channelName))
                        emit(SyncProgress.PreLaunch.Writing(channelName, success = true))
                        emit(SyncProgress.PreLaunch.Launching(channelName))
                    }
                    is SaveSyncResult.Error -> {
                        emit(SyncProgress.PreLaunch.Downloading(channelName, success = false))
                        emit(SyncProgress.Error(downloadResult.message))
                    }
                    is SaveSyncResult.NeedsHardcoreResolution -> {
                        emit(SyncProgress.PreLaunch.Downloading(channelName, success = false))
                        emit(SyncProgress.HardcoreConflict(
                            gameId = downloadResult.gameId,
                            gameName = downloadResult.gameName,
                            tempFilePath = downloadResult.tempFilePath,
                            emulatorId = downloadResult.emulatorId,
                            targetPath = downloadResult.targetPath,
                            isFolderBased = downloadResult.isFolderBased,
                            channelName = downloadResult.channelName
                        ))
                    }
                    else -> {
                        emit(SyncProgress.PreLaunch.Downloading(channelName, success = true))
                        emit(SyncProgress.PreLaunch.Launching(channelName))
                    }
                }
            }
        }
    }

}
