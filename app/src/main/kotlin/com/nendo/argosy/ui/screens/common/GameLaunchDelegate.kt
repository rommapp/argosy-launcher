package com.nendo.argosy.ui.screens.common

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SessionEndResult
import com.nendo.argosy.data.emulator.TitleIdDetector
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchWithSyncUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

enum class HardcoreConflictChoice { KEEP_HARDCORE, DOWNGRADE_TO_CASUAL, KEEP_LOCAL }
enum class LocalModifiedChoice { KEEP_LOCAL, RESTORE_SELECTED }

data class SyncOverlayState(
    val gameTitle: String,
    val syncProgress: SyncProgress,
    @Deprecated("Use syncProgress instead")
    val syncState: SyncState = SyncState.Idle,
    val onGrantPermission: (() -> Unit)? = null,
    val onDisableSync: (() -> Unit)? = null,
    val onOpenSettings: (() -> Unit)? = null,
    val onSkip: (() -> Unit)? = null,
    val onKeepHardcore: (() -> Unit)? = null,
    val onDowngradeToCasual: (() -> Unit)? = null,
    val onKeepLocal: (() -> Unit)? = null,
    val onKeepLocalModified: (() -> Unit)? = null,
    val onRestoreSelected: (() -> Unit)? = null
)

data class DiscPickerState(
    val gameId: Long,
    val discs: List<DiscOption>,
    val channelName: String? = null,
    val launchMode: LaunchMode? = null,
    val onLaunch: (Intent) -> Unit
)

class GameLaunchDelegate @Inject constructor(
    private val application: Application,
    private val gameRepository: GameRepository,
    private val saveCacheDao: com.nendo.argosy.data.local.dao.SaveCacheDao,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val launchWithSyncUseCase: LaunchWithSyncUseCase,
    private val playSessionTracker: PlaySessionTracker,
    private val gameLauncher: GameLauncher,
    private val soundManager: SoundFeedbackManager,
    private val notificationManager: NotificationManager,
    private val titleIdDetector: TitleIdDetector,
    private val saveSyncRepository: SaveSyncRepository,
    private val saveCacheManager: SaveCacheManager
) {
    companion object {
        private const val EMULATOR_KILL_DELAY_MS = 500L
    }

    private suspend fun isActiveSaveHardcore(gameId: Long): Boolean {
        val activeChannel = gameRepository.getActiveSaveChannel(gameId) ?: return false
        val save = saveCacheDao.getMostRecentInChannel(gameId, activeChannel)
        return save?.isHardcore == true
    }

    private val _syncOverlayState = MutableStateFlow<SyncOverlayState?>(null)
    val syncOverlayState: StateFlow<SyncOverlayState?> = _syncOverlayState.asStateFlow()

    private val _discPickerState = MutableStateFlow<DiscPickerState?>(null)
    val discPickerState: StateFlow<DiscPickerState?> = _discPickerState.asStateFlow()

    private val syncMutex = Mutex()
    val isSyncing: Boolean get() = _syncOverlayState.value != null

    fun launchGame(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        channelName: String? = null,
        onLaunch: (Intent) -> Unit
    ) {
        if (!syncMutex.tryLock()) return

        scope.launch {
            try {
                val activeSession = playSessionTracker.activeSession.value
                val isVita3KSession = activeSession?.let { session ->
                    val emuId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
                    emuId?.let { EmulatorRegistry.getById(it) }?.launchConfig is LaunchConfig.Vita3K
                } ?: false

                // Vita3K doesn't support resume - always end stale sessions
                if (isVita3KSession && activeSession != null) {
                    android.util.Log.d("GameLaunchDelegate", "Vita3K session active, ending before fresh launch")
                    playSessionTracker.endSession()
                    delay(EMULATOR_KILL_DELAY_MS)
                }

                val canResume = !isVita3KSession && playSessionTracker.canResumeSession(gameId)

                // Different game is active - end that session and kill emulator first
                if (!canResume && activeSession != null && activeSession.gameId != gameId && !isVita3KSession) {
                    android.util.Log.d("GameLaunchDelegate", "Ending session for game ${activeSession.gameId}, killing ${activeSession.emulatorPackage}")
                    playSessionTracker.endSession()
                    gameLauncher.forceStopEmulator(activeSession.emulatorPackage)
                    delay(EMULATOR_KILL_DELAY_MS)
                }

                if (canResume) {
                    when (val result = launchGameUseCase(gameId, discId, forResume = true)) {
                        is LaunchResult.Success -> {
                            soundManager.play(SoundType.LAUNCH_GAME)
                            onLaunch(result.intent)
                        }
                        is LaunchResult.SelectDisc -> {
                            _discPickerState.value = DiscPickerState(
                                gameId = result.gameId,
                                discs = result.discs,
                                channelName = channelName,
                                onLaunch = onLaunch
                            )
                        }
                        is LaunchResult.NoEmulator -> {
                            notificationManager.showError("No emulator installed for this platform")
                        }
                        is LaunchResult.NoRomFile -> {
                            notificationManager.showError("ROM file not found")
                        }
                        is LaunchResult.NoSteamLauncher -> {
                            notificationManager.showError("Steam launcher not installed")
                        }
                        is LaunchResult.NoCore -> {
                            notificationManager.showError("No core available for ${result.platformSlug}")
                        }
                        is LaunchResult.MissingDiscs -> {
                            val discText = result.missingDiscNumbers.joinToString(", ")
                            notificationManager.showError("Missing discs: $discText. View game details to repair.")
                        }
                        is LaunchResult.NoScummVMGameId -> {
                            notificationManager.showError("Missing .scummvm file for ${result.gameName}")
                        }
                        is LaunchResult.Error -> {
                            notificationManager.showError(result.message)
                        }
                        is LaunchResult.NoAndroidApp -> {
                            notificationManager.showError("Android app not installed: ${result.packageName}")
                        }
                    }
                    return@launch
                }

                val game = gameRepository.getById(gameId) ?: return@launch
                val gameTitle = game.title

                val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
                val emulatorId = emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
                val prefs = preferencesRepository.preferences.first()
                val canSync = emulatorId != null && SavePathRegistry.canSyncWithSettings(
                    emulatorId,
                    prefs.saveSyncEnabled,
                    prefs.experimentalFolderSaveSync
                )
                android.util.Log.d("GameLaunchDelegate", "launchGame: emulatorPackage=$emulatorPackage, emulatorId=$emulatorId, canSync=$canSync")

                val syncStartTime = if (canSync) {
                    _syncOverlayState.value = SyncOverlayState(
                        gameTitle,
                        SyncProgress.PreLaunch.CheckingSave(channelName)
                    )
                    System.currentTimeMillis()
                } else null

                var hardcoreConflictInfo: SyncProgress.HardcoreConflict? = null
                var hardcoreConflictChoice: HardcoreConflictChoice? = null
                var localModifiedInfo: SyncProgress.LocalModified? = null
                var localModifiedChoice: LocalModifiedChoice? = null

                launchWithSyncUseCase.invokeWithProgress(gameId, channelName).collect { progress ->
                    if (canSync && progress != SyncProgress.Skipped && progress != SyncProgress.Idle) {
                        when (progress) {
                            is SyncProgress.HardcoreConflict -> {
                                android.util.Log.d("GameLaunchDelegate", "HardcoreConflict received - showing dialog and waiting for user choice")
                                hardcoreConflictInfo = progress
                                val choiceDeferred = CompletableDeferred<HardcoreConflictChoice>()
                                _syncOverlayState.value = SyncOverlayState(
                                    gameTitle = gameTitle,
                                    syncProgress = progress,
                                    onKeepHardcore = { choiceDeferred.complete(HardcoreConflictChoice.KEEP_HARDCORE) },
                                    onDowngradeToCasual = { choiceDeferred.complete(HardcoreConflictChoice.DOWNGRADE_TO_CASUAL) },
                                    onKeepLocal = { choiceDeferred.complete(HardcoreConflictChoice.KEEP_LOCAL) }
                                )
                                hardcoreConflictChoice = choiceDeferred.await()
                                android.util.Log.d("GameLaunchDelegate", "Hardcore conflict resolved: $hardcoreConflictChoice")
                            }
                            is SyncProgress.LocalModified -> {
                                android.util.Log.d("GameLaunchDelegate", "LocalModified received - showing dialog and waiting for user choice")
                                localModifiedInfo = progress
                                val choiceDeferred = CompletableDeferred<LocalModifiedChoice>()
                                _syncOverlayState.value = SyncOverlayState(
                                    gameTitle = gameTitle,
                                    syncProgress = progress,
                                    onKeepLocalModified = { choiceDeferred.complete(LocalModifiedChoice.KEEP_LOCAL) },
                                    onRestoreSelected = { choiceDeferred.complete(LocalModifiedChoice.RESTORE_SELECTED) }
                                )
                                localModifiedChoice = choiceDeferred.await()
                                android.util.Log.d("GameLaunchDelegate", "LocalModified resolved: $localModifiedChoice")
                            }
                            else -> {
                                _syncOverlayState.value = SyncOverlayState(gameTitle, progress)
                            }
                        }
                    }
                }

                if (hardcoreConflictInfo != null && hardcoreConflictChoice != null) {
                    val resolution = SaveSyncResult.NeedsHardcoreResolution(
                        tempFilePath = hardcoreConflictInfo!!.tempFilePath,
                        gameId = hardcoreConflictInfo!!.gameId,
                        gameName = hardcoreConflictInfo!!.gameName,
                        emulatorId = hardcoreConflictInfo!!.emulatorId,
                        targetPath = hardcoreConflictInfo!!.targetPath,
                        isFolderBased = hardcoreConflictInfo!!.isFolderBased,
                        channelName = hardcoreConflictInfo!!.channelName
                    )
                    val repoChoice = when (hardcoreConflictChoice!!) {
                        HardcoreConflictChoice.KEEP_HARDCORE -> SaveSyncRepository.HardcoreResolutionChoice.KEEP_HARDCORE
                        HardcoreConflictChoice.DOWNGRADE_TO_CASUAL -> SaveSyncRepository.HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
                        HardcoreConflictChoice.KEEP_LOCAL -> SaveSyncRepository.HardcoreResolutionChoice.KEEP_LOCAL
                    }
                    val resolveResult = saveSyncRepository.resolveHardcoreConflict(resolution, repoChoice)
                    android.util.Log.d("GameLaunchDelegate", "Resolution result: $resolveResult")
                }

                if (localModifiedInfo != null && localModifiedChoice != null) {
                    val info = localModifiedInfo!!
                    when (localModifiedChoice!!) {
                        LocalModifiedChoice.KEEP_LOCAL -> {
                            android.util.Log.d("GameLaunchDelegate", "User chose to keep local save - caching as new version")
                            if (emulatorId != null) {
                                val cacheResult = saveCacheManager.cacheCurrentSave(
                                    gameId = gameId,
                                    emulatorId = emulatorId,
                                    savePath = info.localSavePath,
                                    channelName = info.channelName
                                )
                                android.util.Log.d("GameLaunchDelegate", "Cache result after LocalModified keep: $cacheResult")
                                if (cacheResult is SaveCacheManager.CacheResult.Created) {
                                    gameRepository.updateActiveSaveTimestamp(gameId, cacheResult.timestamp)
                                }
                                gameRepository.updateActiveSaveApplied(gameId, true)
                            }
                        }
                        LocalModifiedChoice.RESTORE_SELECTED -> {
                            android.util.Log.d("GameLaunchDelegate", "User chose to restore selected save - backing up local first")
                            if (emulatorId != null) {
                                saveCacheManager.cacheAsRollback(gameId, emulatorId, info.localSavePath)
                                val downloadResult = saveSyncRepository.downloadSave(gameId, emulatorId, info.channelName)
                                android.util.Log.d("GameLaunchDelegate", "Download result after LocalModified restore: $downloadResult")
                                gameRepository.updateActiveSaveApplied(gameId, true)
                            }
                        }
                    }
                }

                syncStartTime?.let { startTime ->
                    val elapsed = System.currentTimeMillis() - startTime
                    val minDisplayTime = 1500L
                    if (elapsed < minDisplayTime) {
                        delay(minDisplayTime - elapsed)
                    }
                }

                _syncOverlayState.value = null

                val launchMode = when {
                    hardcoreConflictChoice == HardcoreConflictChoice.KEEP_HARDCORE -> LaunchMode.RESUME_HARDCORE
                    isActiveSaveHardcore(gameId) -> LaunchMode.RESUME_HARDCORE
                    else -> null
                }

                when (val result = launchGameUseCase(gameId, discId)) {
                    is LaunchResult.Success -> {
                        soundManager.play(SoundType.LAUNCH_GAME)
                        val intent = if (launchMode != null) {
                            result.intent.apply {
                                putExtra(LaunchMode.EXTRA_LAUNCH_MODE, launchMode.name)
                            }
                        } else {
                            result.intent
                        }
                        onLaunch(intent)
                    }
                    is LaunchResult.SelectDisc -> {
                        _discPickerState.value = DiscPickerState(
                            gameId = result.gameId,
                            discs = result.discs,
                            channelName = channelName,
                            launchMode = launchMode,
                            onLaunch = onLaunch
                        )
                    }
                    is LaunchResult.NoEmulator -> {
                        notificationManager.showError("No emulator installed for this platform")
                    }
                    is LaunchResult.NoRomFile -> {
                        notificationManager.showError("ROM file not found")
                    }
                    is LaunchResult.NoSteamLauncher -> {
                        notificationManager.showError("Steam launcher not installed")
                    }
                    is LaunchResult.NoCore -> {
                        notificationManager.showError("No core available for ${result.platformSlug}")
                    }
                    is LaunchResult.MissingDiscs -> {
                        val discText = result.missingDiscNumbers.joinToString(", ")
                        notificationManager.showError("Missing discs: $discText. View game details to repair.")
                    }
                    is LaunchResult.NoScummVMGameId -> {
                        notificationManager.showError("Missing .scummvm file for ${result.gameName}")
                    }
                    is LaunchResult.Error -> {
                        notificationManager.showError(result.message)
                    }
                    is LaunchResult.NoAndroidApp -> {
                        notificationManager.showError("Android app not installed: ${result.packageName}")
                    }
                }
            } finally {
                _syncOverlayState.value = null
                syncMutex.unlock()
            }
        }
    }

    private fun forceStopIfVita3K(session: ActiveSession) {
        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage) ?: return
        val emulatorDef = EmulatorRegistry.getById(emulatorId) ?: return
        if (emulatorDef.launchConfig is LaunchConfig.Vita3K) {
            kotlinx.coroutines.GlobalScope.launch {
                gameLauncher.forceStopEmulator(session.emulatorPackage)
            }
        }
    }

    fun handleSessionEnd(
        scope: CoroutineScope,
        onSyncComplete: () -> Unit = {}
    ) {
        val session = playSessionTracker.activeSession.value
        if (session == null) {
            if (isSyncing) {
                return
            }
            playSessionTracker.forceStopService()
            onSyncComplete()
            return
        }
        val isVita3K = run {
            val emuId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
            emuId?.let { EmulatorRegistry.getById(it) }?.launchConfig is LaunchConfig.Vita3K
        }

        // Vita3K doesn't support resume - always end session immediately
        if (isVita3K) {
            android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: Vita3K session, ending immediately")
            scope.launch { playSessionTracker.endSession() }
            onSyncComplete()
            return
        }

        val sessionDuration = playSessionTracker.getSessionDuration()

        if (sessionDuration != null) {
            val seconds = sessionDuration.seconds

            if (seconds < 10) {
                if (session.flashReturnCount > 0) {
                    android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: double flash return (${seconds}s), cancelling session")
                    playSessionTracker.cancelSession()
                    onSyncComplete()
                    return
                }
                android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: flash return (${seconds}s), keeping session alive")
                playSessionTracker.markFlashReturn()
                return
            }

            if (seconds < 30) {
                android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: short session (${seconds}s), cancelling without backup")
                playSessionTracker.cancelSession()
                forceStopIfVita3K(session)
                onSyncComplete()
                return
            }
        }

        android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: proceeding with session end for gameId=${session.gameId}")
        if (!syncMutex.tryLock()) {
            onSyncComplete()
            return
        }

        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
        if (emulatorId == null) {
            android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: cannot resolve emulatorId, ending session without sync")
            scope.launch { playSessionTracker.endSession() }
            forceStopIfVita3K(session)
            syncMutex.unlock()
            return
        }

        scope.launch {
            try {
                val prefs = preferencesRepository.preferences.first()
                if (!SavePathRegistry.canSyncWithSettings(
                        emulatorId,
                        prefs.saveSyncEnabled,
                        prefs.experimentalFolderSaveSync
                    )
                ) {
                    playSessionTracker.endSession()
                    return@launch
                }

                val game = gameRepository.getById(session.gameId)
                val gameTitle = game?.title ?: "Game"
                val emulatorName = EmulatorRegistry.getById(emulatorId)?.displayName

                val validationResult = titleIdDetector.validateSavePathAccess(emulatorId, session.emulatorPackage)
                when (validationResult) {
                    is TitleIdDetector.ValidationResult.PermissionRequired -> {
                        showBlockedOverlay(
                            gameTitle = gameTitle,
                            progress = SyncProgress.BlockedReason.PermissionRequired(emulatorName),
                            scope = scope,
                            onSyncComplete = onSyncComplete
                        )
                        return@launch
                    }
                    is TitleIdDetector.ValidationResult.AccessDenied -> {
                        showBlockedOverlay(
                            gameTitle = gameTitle,
                            progress = SyncProgress.BlockedReason.AccessDenied(
                                emulatorName,
                                validationResult.path
                            ),
                            scope = scope,
                            onSyncComplete = onSyncComplete
                        )
                        return@launch
                    }
                    is TitleIdDetector.ValidationResult.SavePathNotFound,
                    is TitleIdDetector.ValidationResult.Valid,
                    is TitleIdDetector.ValidationResult.NotFolderBased,
                    is TitleIdDetector.ValidationResult.NoConfig -> {
                        // Proceed to actual sync
                    }
                }

                _syncOverlayState.value = SyncOverlayState(
                    gameTitle,
                    SyncProgress.PostSession.CheckingSave(session.channelName)
                )

                val result = playSessionTracker.endSession()

                when (result) {
                    is SessionEndResult.Success -> {
                        _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.PostSession.Complete)
                        delay(800)
                        _syncOverlayState.value = null
                    }
                    is SessionEndResult.Duplicate, is SessionEndResult.Skipped -> {
                        _syncOverlayState.value = null
                    }
                    is SessionEndResult.Error -> {
                        _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.Error(result.message))
                        delay(1500)
                        _syncOverlayState.value = null
                    }
                }

                forceStopIfVita3K(session)
                onSyncComplete()
            } finally {
                syncMutex.unlock()
            }
        }
    }

    private fun showBlockedOverlay(
        gameTitle: String,
        progress: SyncProgress.BlockedReason,
        scope: CoroutineScope,
        onSyncComplete: () -> Unit
    ) {
        _syncOverlayState.value = SyncOverlayState(
            gameTitle = gameTitle,
            syncProgress = progress,
            onGrantPermission = {
                openAllFilesAccessSettings()
                dismissBlockedOverlay(onSyncComplete)
            },
            onDisableSync = {
                scope.launch {
                    preferencesRepository.setSaveSyncEnabled(false)
                }
                dismissBlockedOverlay(onSyncComplete)
            },
            onSkip = {
                dismissBlockedOverlay(onSyncComplete)
            }
        )
    }

    private fun dismissBlockedOverlay(onSyncComplete: () -> Unit) {
        kotlinx.coroutines.GlobalScope.launch { playSessionTracker.endSession() }
        _syncOverlayState.value = null
        syncMutex.unlock()
        onSyncComplete()
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
        }
    }

    fun selectDisc(scope: CoroutineScope, discPath: String) {
        val state = _discPickerState.value ?: return
        _discPickerState.value = null

        scope.launch {
            val result = launchGameUseCase(
                gameId = state.gameId,
                selectedDiscPath = discPath
            )
            when (result) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    val intent = if (state.launchMode != null) {
                        result.intent.apply {
                            putExtra(LaunchMode.EXTRA_LAUNCH_MODE, state.launchMode.name)
                        }
                    } else {
                        result.intent
                    }
                    state.onLaunch(intent)
                }
                is LaunchResult.Error -> {
                    notificationManager.showError(result.message)
                }
                else -> {
                    notificationManager.showError("Failed to launch disc")
                }
            }
        }
    }

    fun dismissDiscPicker() {
        _discPickerState.value = null
    }
}
