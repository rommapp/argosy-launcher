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
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SessionEndResult
import com.nendo.argosy.data.emulator.SavePathValidator
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
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HardcoreConflictChoice { KEEP_HARDCORE, DOWNGRADE_TO_CASUAL, KEEP_LOCAL }
enum class LocalModifiedChoice { KEEP_LOCAL, RESTORE_SELECTED }

data class LaunchResultCallbacks(
    val onLaunch: (Intent) -> Unit,
    val onSelectDisc: ((List<DiscOption>) -> Unit)? = null,
    val onSelectVariant: ((List<com.nendo.argosy.data.emulator.VariantOption>) -> Unit)? = null,
    val onNoEmulator: (() -> Unit)? = null,
    val onNoCore: (() -> Unit)? = null,
    val onMissingDiscs: ((List<Int>) -> Unit)? = null,
    val onLaunchFailed: () -> Unit = {}
)

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

data class VariantPickerState(
    val gameId: Long,
    val variants: List<com.nendo.argosy.data.emulator.VariantOption>,
    val channelName: String? = null,
    val launchMode: LaunchMode? = null,
    val onLaunch: (Intent) -> Unit
)

class GameLaunchDelegate @Inject constructor(
    private val application: Application,
    private val gameRepository: GameRepository,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val launchWithSyncUseCase: LaunchWithSyncUseCase,
    private val playSessionTracker: PlaySessionTracker,
    private val gameLauncher: GameLauncher,
    private val soundManager: SoundFeedbackManager,
    private val notificationManager: NotificationManager,
    private val savePathValidator: SavePathValidator,
    private val saveSyncRepository: SaveSyncRepository,
    private val saveCacheManager: SaveCacheManager,
    private val variantResolver: com.nendo.argosy.data.emulator.VariantResolver
) {
    companion object {
        private const val EMULATOR_KILL_DELAY_MS = 500L
    }

    private suspend fun isActiveSaveHardcore(gameId: Long): Boolean {
        val activeChannel = gameRepository.getActiveSaveChannel(gameId) ?: return false
        val save = saveCacheManager.getMostRecentInChannel(gameId, activeChannel)
        return save?.isHardcore == true
    }

    private val _syncOverlayState = MutableStateFlow<SyncOverlayState?>(null)
    val syncOverlayState: StateFlow<SyncOverlayState?> = _syncOverlayState.asStateFlow()

    private val _discPickerState = MutableStateFlow<DiscPickerState?>(null)
    val discPickerState: StateFlow<DiscPickerState?> = _discPickerState.asStateFlow()

    private val _variantPickerState = MutableStateFlow<VariantPickerState?>(null)
    val variantPickerState: StateFlow<VariantPickerState?> = _variantPickerState.asStateFlow()

    val isSyncing: Boolean get() = _syncOverlayState.value != null

    private var _onLaunchFailed: (() -> Unit)? = null

    fun launchGame(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        channelName: String? = null,
        onLaunch: (Intent) -> Unit,
        onLaunchFailed: () -> Unit = {}
    ) {
        if (isSyncing) {
            onLaunchFailed()
            return
        }
        _onLaunchFailed = onLaunchFailed

        scope.launch {
            try {
                val activeSession = playSessionTracker.activeSession.value
                val sessionRequiresKill = activeSession?.let { session ->
                    val emuId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
                    emuId?.let { EmulatorRegistry.getById(it) }?.launchConfig?.requiresEmulatorKill == true
                } ?: false

                // Emulators flagged requiresEmulatorKill (e.g. Vita3K) don't support resume -- always end stale sessions
                if (sessionRequiresKill) {
                    android.util.Log.d("GameLaunchDelegate", "Session requires emulator kill, ending before fresh launch")
                    playSessionTracker.endSession()
                    delay(EMULATOR_KILL_DELAY_MS)
                }

                val canResume = !sessionRequiresKill && playSessionTracker.canResumeSession(gameId)

                // Stale session active - end it and kill emulator before fresh launch
                if (!canResume && activeSession != null && !sessionRequiresKill) {
                    android.util.Log.d("GameLaunchDelegate", "Evicting stale session for game ${activeSession.gameId}, killing ${activeSession.emulatorPackage}")
                    playSessionTracker.endSession()
                    gameLauncher.forceStopEmulator(activeSession.emulatorPackage)
                    delay(EMULATOR_KILL_DELAY_MS)
                }

                val game = gameRepository.getById(gameId)
                if (game == null) {
                    onLaunchFailed()
                    return@launch
                }
                val resolvedVariantId = variantResolver.resolveVariant(game)?.id

                if (canResume) {
                    val result = launchGameUseCase(gameId, discId, forResume = true, variantFileId = resolvedVariantId, prefetchedGame = game)
                    dispatchPrimaryLaunchResult(result, channelName, launchMode = null, onLaunch, onLaunchFailed)
                    return@launch
                }

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

                val result = launchGameUseCase(gameId, discId, variantFileId = resolvedVariantId, prefetchedGame = game)
                dispatchPrimaryLaunchResult(result, channelName, launchMode, onLaunch, onLaunchFailed)
            } finally {
                _syncOverlayState.value = null
            }
        }
    }

    private fun dispatchPrimaryLaunchResult(
        result: LaunchResult,
        channelName: String?,
        launchMode: LaunchMode?,
        onLaunch: (Intent) -> Unit,
        onLaunchFailed: () -> Unit
    ) {
        when (result) {
            is LaunchResult.Success -> {
                soundManager.play(SoundType.LAUNCH_GAME)
                onLaunch(applyLaunchMode(result.intent, launchMode))
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
            is LaunchResult.SelectVariant -> {
                _variantPickerState.value = VariantPickerState(
                    gameId = result.gameId,
                    variants = result.variants,
                    channelName = channelName,
                    launchMode = launchMode,
                    onLaunch = onLaunch
                )
            }
            else -> dispatchErrorResult(result, onLaunchFailed)
        }
    }

    private fun dispatchErrorResult(result: LaunchResult, onLaunchFailed: () -> Unit) {
        when (result) {
            is LaunchResult.NoEmulator -> notificationManager.showError("No emulator installed for this platform")
            is LaunchResult.NoRomFile -> notificationManager.showError("ROM file not found")
            is LaunchResult.NoSteamLauncher -> notificationManager.showError("Steam launcher not installed")
            is LaunchResult.NoCore -> {
                val base = "No core available for ${result.platformSlug}"
                val message = result.reason?.let { "$base: $it" } ?: base
                notificationManager.showError(message)
            }
            is LaunchResult.MissingDiscs -> {
                val discText = result.missingDiscNumbers.joinToString(", ")
                notificationManager.showError("Missing discs: $discText. View game details to repair.")
            }
            is LaunchResult.NoScummVMGameId -> notificationManager.showError("Missing .scummvm file for ${result.gameName}")
            is LaunchResult.NoAndroidApp -> notificationManager.showError("Android app not installed: ${result.packageName}")
            is LaunchResult.Error -> notificationManager.showError(result.message)
            else -> { /* Success/SelectDisc/SelectVariant handled elsewhere */ }
        }
        onLaunchFailed()
    }

    private fun applyLaunchMode(intent: Intent, launchMode: LaunchMode?): Intent {
        if (launchMode == null) return intent
        return intent.apply { putExtra(LaunchMode.EXTRA_LAUNCH_MODE, launchMode.name) }
    }

    private fun forceStopIfVita3K(scope: CoroutineScope, session: ActiveSession) {
        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage) ?: return
        val emulatorDef = EmulatorRegistry.getById(emulatorId) ?: return
        if (emulatorDef.launchConfig.requiresEmulatorKill) {
            scope.launch {
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
                // Prior sync coroutine may have been cancelled (e.g. config change).
                // Clear stale overlay to avoid permanent stuck state.
                _syncOverlayState.value = null
            }
            playSessionTracker.forceStopService()
            onSyncComplete()
            return
        }
        val sessionDuration = playSessionTracker.getSessionDuration()

        if (sessionDuration != null && sessionDuration.seconds < 30) {
            android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: short session (${sessionDuration.seconds}s), cancelling without backup")
            playSessionTracker.cancelSession()
            onSyncComplete()
            return
        }

        android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: proceeding with session end for gameId=${session.gameId}")

        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
        if (emulatorId == null) {
            android.util.Log.d("GameLaunchDelegate", "handleSessionEnd: cannot resolve emulatorId, ending session without sync")
            scope.launch { playSessionTracker.endSession() }
            forceStopIfVita3K(scope, session)
            onSyncComplete()
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
                    onSyncComplete()
                    return@launch
                }

                val game = gameRepository.getById(session.gameId)
                val gameTitle = game?.title ?: "Game"
                val emulatorName = EmulatorRegistry.getById(emulatorId)?.displayName

                val validationResult = savePathValidator.validateAccess(emulatorId, session.emulatorPackage)
                when (validationResult) {
                    is SavePathValidator.Result.PermissionRequired -> {
                        showBlockedOverlay(
                            gameTitle = gameTitle,
                            progress = SyncProgress.BlockedReason.PermissionRequired(emulatorName),
                            scope = scope,
                            onSyncComplete = onSyncComplete
                        )
                        return@launch
                    }
                    is SavePathValidator.Result.AccessDenied -> {
                        showBlockedOverlay(
                            gameTitle = gameTitle,
                            progress = SyncProgress.BlockedReason.AccessDenied(
                                emulatorName,
                                validationResult.path,
                                platformSlug = game?.platformSlug
                            ),
                            scope = scope,
                            onSyncComplete = onSyncComplete
                        )
                        return@launch
                    }
                    is SavePathValidator.Result.SavePathNotFound,
                    is SavePathValidator.Result.Valid,
                    is SavePathValidator.Result.NotFolderBased,
                    is SavePathValidator.Result.NoConfig -> {
                        // Proceed to actual sync
                    }
                }

                android.util.Log.d("GameLaunchDelegate", "[DualSync] Starting post-session sync | game=$gameTitle, channel=${session.channelName}, emulator=$emulatorId, sessionStart=${session.startTime}")

                _syncOverlayState.value = SyncOverlayState(
                    gameTitle,
                    SyncProgress.PostSession.CheckingSave(session.channelName)
                )

                val result = playSessionTracker.endSession()
                android.util.Log.d("GameLaunchDelegate", "[DualSync] endSession result: ${result::class.simpleName}")

                when (result) {
                    is SessionEndResult.Success -> {
                        _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.PostSession.Complete)
                        delay(800)
                        _syncOverlayState.value = null
                    }
                    is SessionEndResult.Duplicate, is SessionEndResult.Skipped -> {
                        android.util.Log.d("GameLaunchDelegate", "[DualSync] Session end was ${result::class.simpleName}, clearing overlay")
                        _syncOverlayState.value = null
                    }
                    is SessionEndResult.Error -> {
                        android.util.Log.w("GameLaunchDelegate", "[DualSync] Session end error: ${result.message}")
                        _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.Error(result.message))
                        delay(1500)
                        _syncOverlayState.value = null
                    }
                }

                forceStopIfVita3K(scope, session)
                onSyncComplete()
            } catch (e: Exception) {
                android.util.Log.e("GameLaunchDelegate", "handleSessionEnd failed", e)
                _syncOverlayState.value = null
                playSessionTracker.endSessionInBackground()
                onSyncComplete()
            }
        }
    }

    private fun showBlockedOverlay(
        gameTitle: String,
        progress: SyncProgress.BlockedReason,
        scope: CoroutineScope,
        onSyncComplete: () -> Unit
    ) {
        val isSwitchAccessDenied = progress is SyncProgress.BlockedReason.AccessDenied &&
            progress.platformSlug == "switch"

        _syncOverlayState.value = SyncOverlayState(
            gameTitle = gameTitle,
            syncProgress = progress,
            onGrantPermission = {
                openAllFilesAccessSettings()
                dismissBlockedOverlay(scope, onSyncComplete)
            },
            onOpenSettings = if (isSwitchAccessDenied) {
                { dismissBlockedOverlay(scope, onSyncComplete) }
            } else null,
            onDisableSync = {
                scope.launch {
                    preferencesRepository.setSaveSyncEnabled(false)
                }
                dismissBlockedOverlay(scope, onSyncComplete)
            },
            onSkip = {
                dismissBlockedOverlay(scope, onSyncComplete)
            }
        )
    }

    private fun dismissBlockedOverlay(scope: CoroutineScope, onSyncComplete: () -> Unit) {
        _syncOverlayState.value = null
        scope.launch {
            try {
                playSessionTracker.endSession()
            } finally {
                onSyncComplete()
            }
        }
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
        _onLaunchFailed = null

        scope.launch {
            val result = launchGameUseCase(
                gameId = state.gameId,
                selectedDiscPath = discPath
            )
            when (result) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    state.onLaunch(applyLaunchMode(result.intent, state.launchMode))
                }
                is LaunchResult.Error -> notificationManager.showError(result.message)
                else -> notificationManager.showError("Failed to launch disc")
            }
        }
    }

    fun dismissDiscPicker() {
        _discPickerState.value = null
        _onLaunchFailed?.invoke()
        _onLaunchFailed = null
    }

    fun launchSimple(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        launchMode: LaunchMode? = null,
        callbacks: LaunchResultCallbacks
    ) {
        scope.launch {
            val result = launchGameUseCase(
                gameId = gameId,
                discId = discId,
                selectedDiscPath = selectedDiscPath,
                variantFileId = variantFileId
            )
            dispatchSimpleResult(result, launchMode, callbacks)
        }
    }

    private fun dispatchSimpleResult(
        result: LaunchResult,
        launchMode: LaunchMode?,
        callbacks: LaunchResultCallbacks
    ) {
        when (result) {
            is LaunchResult.Success -> {
                soundManager.play(SoundType.LAUNCH_GAME)
                callbacks.onLaunch(applyLaunchMode(result.intent, launchMode))
            }
            is LaunchResult.SelectDisc -> {
                val handler = callbacks.onSelectDisc
                if (handler != null) handler(result.discs)
                else dispatchErrorResult(result, callbacks.onLaunchFailed)
            }
            is LaunchResult.SelectVariant -> {
                val handler = callbacks.onSelectVariant
                if (handler != null) handler(result.variants)
                else dispatchErrorResult(result, callbacks.onLaunchFailed)
            }
            is LaunchResult.NoEmulator -> {
                val handler = callbacks.onNoEmulator
                if (handler != null) handler() else dispatchErrorResult(result, callbacks.onLaunchFailed)
            }
            is LaunchResult.NoCore -> {
                val handler = callbacks.onNoCore
                if (handler != null) handler() else dispatchErrorResult(result, callbacks.onLaunchFailed)
            }
            is LaunchResult.MissingDiscs -> {
                val handler = callbacks.onMissingDiscs
                if (handler != null) handler(result.missingDiscNumbers)
                else dispatchErrorResult(result, callbacks.onLaunchFailed)
            }
            else -> dispatchErrorResult(result, callbacks.onLaunchFailed)
        }
    }
}
