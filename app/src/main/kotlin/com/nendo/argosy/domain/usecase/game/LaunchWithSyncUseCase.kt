package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.EffectiveLibretroSettingsResolver
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.SiblingGroupRepository
import com.nendo.argosy.data.sync.CrossEmulatorSaveMigrator
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.domain.usecase.state.PreLaunchStateSyncUseCase
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "LaunchWithSync"
private const val PRE_LAUNCH_SYNC_BUDGET_MS = 5_000L

class LaunchWithSyncUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val activateSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.ActivateSaveChannelUseCase,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val titleIdDownloadObserver: TitleIdDownloadObserver,
    private val preLaunchStateSyncUseCase: PreLaunchStateSyncUseCase,
    private val n3dsSaveCaseRepair: com.nendo.argosy.data.sync.N3dsSaveCaseRepair,
    private val syncStatesOnSessionEndUseCase:
        com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase,
    private val siblingGroupRepository: SiblingGroupRepository,
    private val stateCacheManager: StateCacheManager,
    private val effectiveLibretroSettingsResolver: EffectiveLibretroSettingsResolver,
    private val crossEmulatorSaveMigrator: CrossEmulatorSaveMigrator
) {
    private val backgroundScope = SafeCoroutineScope(Dispatchers.IO, TAG)

    @Deprecated("Use invokeWithProgress instead", ReplaceWith("invokeWithProgress(gameId)"))
    fun invoke(gameId: Long): Flow<SyncState> = flow {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            emit(SyncState.Skipped)
            return@flow
        }

        if (!romMRepository.isReachable()) {
            emit(SyncState.Skipped)
            return@flow
        }

        val game = gameDao.getById(gameId)
        if (game == null || game.rommId == null) {
            emit(SyncState.Skipped)
            return@flow
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
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

        val syncResult = saveSyncRepository.preLaunchSyncForGame(gameId, game.rommId, emulatorId, channelName = null, secureSaves = prefs.secureSaves)

        when (syncResult) {
            is PreLaunchSyncResult.NoConnection, is PreLaunchSyncResult.TimedOut -> {
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

    private suspend fun <T> withinConnectionBudget(gameId: Long, block: suspend () -> T): T? = coroutineScope {
        val mark = romMRepository.responseMark()
        val work = async { block() }
        withTimeoutOrNull(PRE_LAUNCH_SYNC_BUDGET_MS) { work.await() } ?: run {
            if (romMRepository.answeredSince(mark)) {
                Logger.info(TAG, "Pre-launch sync for gameId=$gameId passed ${PRE_LAUNCH_SYNC_BUDGET_MS}ms with the server answering; waiting for it to finish")
                work.await()
            } else {
                Logger.warn(TAG, "Pre-launch sync for gameId=$gameId got no server answer in ${PRE_LAUNCH_SYNC_BUDGET_MS}ms, launching with local data")
                work.cancel()
                null
            }
        }
    }

    private suspend fun syncStatesQuietly(gameId: Long, emulatorPackage: String, channelName: String?) {
        runCatching { syncStatesOnSessionEndUseCase.adoptOffSessionStates(gameId, emulatorPackage, queueUploads = false) }
            .onFailure { Logger.error(TAG, "Off-session state adoption failed for gameId=$gameId", it) }
        runCatching {
            val activeChannel = channelName ?: activeSaveRepository.getActiveChannel(gameId)
            preLaunchStateSyncUseCase(gameId, emulatorPackage, activeChannel)
        }
            .onFailure { Logger.error(TAG, "Pre-launch state sync failed for gameId=$gameId", it) }
    }

    private suspend fun dropAutoStatesOlderThanSave(
        game: GameEntity,
        emulatorId: String,
        serverTimestamp: java.time.Instant?
    ) {
        if (emulatorId != EmulatorRegistry.BUILTIN_ID || serverTimestamp == null) return
        val romPath = game.localPath ?: return
        val settings = effectiveLibretroSettingsResolver.getEffectiveSettings(game.platformId, game.platformSlug)
        if (!settings.autoRestoreState || !settings.preferNewerServerSave) return
        val dropped = runCatching {
            stateCacheManager.deleteAutoResumeStatesOlderThan(
                emulatorId = emulatorId,
                romPath = romPath,
                platformSlug = game.platformSlug,
                coreId = null,
                serials = emptySet(),
                gameId = game.id,
                cutoff = serverTimestamp
            )
        }.onFailure { Logger.warn(TAG, "Could not drop stale auto states for gameId=${game.id}: ${it.message}") }
            .getOrDefault(false)
        if (dropped) {
            Logger.info(TAG, "[SaveSync] PRE_LAUNCH gameId=${game.id} | Dropped the auto-resume state older than the downloaded save | serverTimestamp=$serverTimestamp")
        }
    }

    private fun refreshMainSiblingInBackground(gameId: Long) {
        backgroundScope.launch {
            runCatching { siblingGroupRepository.refreshRommMainSibling(gameId) }
                .onFailure { Logger.warn(TAG, "Main-sibling refresh failed for gameId=$gameId: ${it.message}") }
        }
    }

    fun invokeWithProgress(
        gameId: Long,
        channelName: String? = null,
        skipPreLaunchSync: Boolean = false
    ): Flow<SyncProgress> = flow {
        if (channelName != null) {
            val currentChannel = activeSaveRepository.getActiveChannel(gameId)
            if (currentChannel != channelName) {
                activateSaveChannelUseCase(gameId, channelName)
            }
        }

        crossEmulatorSaveMigrator.migrateBeforeLaunch(gameId)

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

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
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
            if (romMRepository.isReachable()) {
                refreshMainSiblingInBackground(gameId)
                withinConnectionBudget(gameId) { syncStatesQuietly(gameId, emulatorPackage, channelName) }
            }
            emit(SyncProgress.Skipped)
            return@flow
        }

        emit(SyncProgress.PreLaunch.CheckingSave(channelName, found = true))
        emit(SyncProgress.PreLaunch.Connecting(channelName))

        if (!romMRepository.isReachable()) {
            emit(SyncProgress.PreLaunch.Connecting(channelName, success = false))
            emit(SyncProgress.Skipped)
            return@flow
        }

        emit(SyncProgress.PreLaunch.Connecting(channelName, success = true))

        refreshMainSiblingInBackground(gameId)

        titleIdDownloadObserver.extractTitleIdForGame(gameId)

        n3dsSaveCaseRepair.repairIfNeeded(gameId, emulatorId, emulatorPackage)

        val syncResult = withinConnectionBudget(gameId) {
            coroutineScope {
                val stateSync = async { syncStatesQuietly(gameId, emulatorPackage, channelName) }
                val saveSync = saveSyncRepository.preLaunchSyncForGame(gameId, game.rommId, emulatorId, channelName, secureSaves = prefs.secureSaves)
                stateSync.await()
                saveSync
            }
        } ?: PreLaunchSyncResult.TimedOut

        when (syncResult) {
            is PreLaunchSyncResult.NoConnection -> {
                emit(SyncProgress.PreLaunch.Connecting(channelName, success = false))
                emit(SyncProgress.Skipped)
            }
            is PreLaunchSyncResult.TimedOut -> {
                emit(SyncProgress.PreLaunch.TimedOut(channelName))
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
                emit(
                    SyncProgress.LocalModified(
                        gameId, syncResult.localSavePath, syncResult.channelName, syncResult.serverSaveId,
                        restoreFailed = syncResult.restoreFailed,
                        snapshotConflict = syncResult.snapshotConflict
                    )
                )
            }
            is PreLaunchSyncResult.ServerIsNewer -> {
                emit(SyncProgress.PreLaunch.Downloading(channelName))
                val downloadResult = saveSyncRepository.downloadSave(
                    gameId, emulatorId, syncResult.channelName,
                    knownServerSaveId = syncResult.serverSaveId
                )
                when (downloadResult) {
                    is SaveSyncResult.Success -> {
                        if (!downloadResult.noOp) {
                            dropAutoStatesOlderThanSave(game, emulatorId, downloadResult.serverTimestamp)
                        }
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
