package com.nendo.argosy.ui.screens.common

import android.app.Application
import android.content.Intent
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SessionEndResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.HardcoreResolutionChoice
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchWithSyncUseCase
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HardcoreConflictChoice { KEEP_HARDCORE, DOWNGRADE_TO_CASUAL, KEEP_LOCAL }
enum class LocalModifiedChoice { KEEP_LOCAL, RESTORE_SELECTED, LAUNCH_WITHOUT_SYNC }

data class LaunchResultCallbacks(
    val start: GameStart,
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
    val onSkip: (() -> Unit)? = null
)

data class DiscPickerState(
    val gameId: Long,
    val discs: List<DiscOption>,
    val channelName: String? = null,
    val launchMode: LaunchMode? = null,
    val origin: LaunchOrigin = LaunchOrigin.INTERNAL,
    val start: GameStart
)

data class VariantPickerState(
    val gameId: Long,
    val variants: List<com.nendo.argosy.data.emulator.VariantOption>,
    val channelName: String? = null,
    val launchMode: LaunchMode? = null,
    val origin: LaunchOrigin = LaunchOrigin.INTERNAL,
    val start: GameStart
)

data class MemcardPickerState(
    val gameId: Long,
    val emulatorId: String,
    val platformName: String,
    val cards: List<com.nendo.argosy.data.sync.platform.MemcardInfo>,
    val channelName: String? = null,
    val launchMode: LaunchMode? = null,
    val origin: LaunchOrigin = LaunchOrigin.INTERNAL,
    val start: GameStart
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
    private val hapticManager: HapticFeedbackManager,
    private val notificationManager: NotificationManager,
    private val sessionEndCoordinator: SessionEndCoordinator,
    private val saveSyncRepository: SaveSyncRepository,
    private val saveCacheManager: SaveCacheManager,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val variantResolver: com.nendo.argosy.data.emulator.VariantResolver,
    private val emulatorSaveConfigRepository: com.nendo.argosy.data.repository.EmulatorSaveConfigRepository,
    private val retroAchievementsRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository,
    private val getUnifiedSavesUseCase: com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase,
    private val launchProgressTracker: LaunchProgressTracker,
    private val launchHandoff: LaunchHandoff,
    private val resolveSnapshotConflict: com.nendo.argosy.domain.usecase.savechannel.ResolveSnapshotConflictUseCase,
    private val connectedControllerTracker: com.nendo.argosy.core.input.ConnectedControllerTracker,
    private val preLaunchRecapStep: PreLaunchRecapStep,
    private val backgroundGameLaunch: BackgroundGameLaunch
) {
    companion object {
        private const val EMULATOR_KILL_DELAY_MS = 500L
        private val HARDCORE_CONFLICT_OPTIONS = listOf(
            LaunchPromptOption.KEEP_HARDCORE,
            LaunchPromptOption.DOWNGRADE_TO_CASUAL,
            LaunchPromptOption.SKIP_HARDCORE_SAVE
        )
        private val LOCAL_MODIFIED_OPTIONS = listOf(
            LaunchPromptOption.APPLY_LOCAL,
            LaunchPromptOption.RESTORE_SERVER
        )
        private val RESTORE_FAILED_OPTIONS = listOf(
            LaunchPromptOption.RESTORE_SERVER,
            LaunchPromptOption.LAUNCH_WITHOUT_SYNC
        )
        private val SNAPSHOT_CONFLICT_OPTIONS = listOf(
            LaunchPromptOption.KEEP_MINE,
            LaunchPromptOption.TAKE_THEIRS,
            LaunchPromptOption.BRANCH_LOCAL,
            LaunchPromptOption.REVERT_LOCAL
        )
    }

    /**
     * Whether the active save is hardcore, resolved over the unified cache+server view so a
     * server-only cloud save (the common freshly-synced case) is not missed. Requires an active row
     * for the current owner: the unified entry pool is not owner-scoped, so resolving with no
     * coordinates would fall through to the newest save across all owners. Feeds the launch-mode
     * [LaunchMode.RESUME_HARDCORE] decision.
     */
    private suspend fun isActiveSaveHardcore(gameId: Long): Boolean {
        val activeRow = activeSaveRepository.getActiveRow(gameId) ?: return false
        return getUnifiedSavesUseCase.resolveActive(
            gameId = gameId,
            activeChannel = activeRow.channelName,
            activeSaveTimestamp = activeRow.cachedAt.toEpochMilli(),
            includeServer = com.nendo.argosy.util.NetworkUtils.isOnline(application)
        )?.isHardcore == true
    }

    /**
     * "Default to Hardcore": a built-in game resumes in hardcore by default when the setting is on
     * and RetroAchievements is signed in. Centralized here so every launch entry point (home,
     * library, game detail, dual-screen) honors it uniformly. Requires the game to actually have
     * achievements: hardcore mode disables save states, rewind, and cheats regardless of whether an
     * RA session resolves, so forcing it on an achievement-less game only removes features (and its
     * hardcore-tagged save would then ratchet future resumes into hardcore).
     */
    private suspend fun shouldDefaultToHardcore(
        emulatorPackage: String?,
        game: com.nendo.argosy.data.local.entity.GameEntity
    ): Boolean =
        emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE &&
            game.achievementCount > 0 &&
            preferencesRepository.getBuiltinEmulatorSettings().first().defaultToHardcore == "hardcore" &&
            retroAchievementsRepository.isLoggedIn()

    val syncOverlayState: StateFlow<SyncOverlayState?> = sessionEndCoordinator.syncOverlayState

    private val _discPickerState = MutableStateFlow<DiscPickerState?>(null)
    val discPickerState: StateFlow<DiscPickerState?> = _discPickerState.asStateFlow()

    private val _variantPickerState = MutableStateFlow<VariantPickerState?>(null)
    val variantPickerState: StateFlow<VariantPickerState?> = _variantPickerState.asStateFlow()


    private val _memcardPickerState = MutableStateFlow<MemcardPickerState?>(null)
    val memcardPickerState: StateFlow<MemcardPickerState?> = _memcardPickerState.asStateFlow()

    val isSyncing: Boolean get() = syncOverlayState.value != null

    private var _onLaunchFailed: (() -> Unit)? = null

    private suspend fun emulatorClearedForLaunch(
        ticket: LaunchProgressTracker.Ticket,
        emulatorPackage: String?,
        alreadyStopped: String?,
        noTrackedSession: Boolean
    ): Boolean {
        if (emulatorPackage == null || emulatorPackage == alreadyStopped) return true
        if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE || emulatorPackage == application.packageName) return true
        val requiresKill = noTrackedSession && emulatorResolver.resolveEmulatorId(emulatorPackage)
            ?.let { EmulatorRegistry.getById(it) }?.launchConfig?.requiresEmulatorKill == true
        if (!requiresKill && !withContext(Dispatchers.IO) { RootShell.isAvailable }) return true
        if (!requiresKill) {
            when (backgroundGameLaunch.clearForLaunch(ticket, emulatorPackage)) {
                BackgroundGameLaunch.Clear.CANCELLED -> return false
                BackgroundGameLaunch.Clear.CLOSED -> {
                    delay(EMULATOR_KILL_DELAY_MS)
                    return true
                }
                BackgroundGameLaunch.Clear.NOT_RUNNING -> Unit
            }
        }
        android.util.Log.d("GameLaunchDelegate", "Force-stopping $emulatorPackage before launch (requiresKill=$requiresKill)")
        gameLauncher.forceStopEmulator(emulatorPackage)
        delay(EMULATOR_KILL_DELAY_MS)
        return true
    }

    private suspend fun evictSessionReturningStopped(session: com.nendo.argosy.data.emulator.ActiveSession): String? {
        endSessionAndAwaitConflictAnswer()
        if (backgroundGameLaunch.keepsAlive(session.emulatorPackage)) {
            backgroundGameLaunch.remember(session)
            return null
        }
        gameLauncher.forceStopEmulator(session.emulatorPackage)
        delay(EMULATOR_KILL_DELAY_MS)
        return session.emulatorPackage
    }

    private suspend fun endSessionAndAwaitConflictAnswer(): SessionEndResult {
        val result = playSessionTracker.endSession()
        playSessionTracker.pendingSessionConflict.first { it == null }
        return result
    }

    fun launchGame(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        channelName: String? = null,
        skipPreLaunchSync: Boolean = false,
        overrideLaunchMode: LaunchMode? = null,
        allowVariantPrompt: Boolean = true,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        start: GameStart,
        onLaunchFailed: () -> Unit = {}
    ) {
        if (isSyncing) {
            notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_busy_syncing))
            onLaunchFailed()
            return
        }
        _onLaunchFailed = onLaunchFailed

        scope.launch {
            val game = gameRepository.getById(gameId) ?: run {
                onLaunchFailed()
                return@launch
            }
            val ticket = launchProgressTracker.begin(gameId, game.title) ?: run {
                android.util.Log.d("GameLaunchDelegate", "launchGame: another launch is in progress")
                notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_busy_launching))
                onLaunchFailed()
                return@launch
            }
            try {
                val activeSession = playSessionTracker.activeSession.value
                val sessionRequiresKill = activeSession?.let { session ->
                    val emuId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
                    emuId?.let { EmulatorRegistry.getById(it) }?.launchConfig?.requiresEmulatorKill == true
                } ?: false

                val resolvedVariantId = variantResolver.resolveVariant(game)?.id

                val canResume = !sessionRequiresKill && playSessionTracker.canResumeSession(gameId, resolvedVariantId)
                val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
                val evictsOwnSessionAlive = !canResume && !sessionRequiresKill && activeSession != null &&
                    activeSession.gameId == gameId && activeSession.emulatorPackage == emulatorPackage &&
                    backgroundGameLaunch.keepsAlive(activeSession.emulatorPackage)
                var liveInBackground = if (canResume || evictsOwnSessionAlive) null
                else backgroundGameLaunch.findLive(gameId, resolvedVariantId, emulatorPackage)

                if (!canResume && !evictsOwnSessionAlive && liveInBackground == null &&
                    !preLaunchRecapStep.run(ticket, gameId, game.title)
                ) {
                    onLaunchFailed()
                    return@launch
                }

                if (sessionRequiresKill) {
                    android.util.Log.d("GameLaunchDelegate", "Session requires emulator kill, ending before fresh launch")
                    endSessionAndAwaitConflictAnswer()
                    delay(EMULATOR_KILL_DELAY_MS)
                }

                var stoppedPackage: String? = null
                if (!canResume && activeSession != null && !sessionRequiresKill) {
                    android.util.Log.d("GameLaunchDelegate", "Evicting stale session for game ${activeSession.gameId} in ${activeSession.emulatorPackage}")
                    stoppedPackage = evictSessionReturningStopped(activeSession)
                }
                if (evictsOwnSessionAlive) {
                    liveInBackground = backgroundGameLaunch.findLive(gameId, resolvedVariantId, emulatorPackage)
                }

                if (canResume) {
                    val result = launchGameUseCase(gameId, discId, forResume = true, variantFileId = resolvedVariantId, allowVariantPrompt = false, prefetchedGame = game, sessionAtWindow = true)
                    dispatchPrimaryLaunchResult(result, ticket, channelName, discId, overrideLaunchMode, origin, start, onLaunchFailed)
                    return@launch
                }

                if (liveInBackground != null) {
                    when (backgroundGameLaunch.resume(ticket, game, liveInBackground, channelName, origin)) {
                        BackgroundGameLaunch.Resume.RESUMED -> return@launch
                        BackgroundGameLaunch.Resume.CANCELLED -> {
                            onLaunchFailed()
                            return@launch
                        }
                        BackgroundGameLaunch.Resume.FRESH_LAUNCH -> delay(EMULATOR_KILL_DELAY_MS)
                    }
                }

                val emulatorId = emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
                if (!emulatorClearedForLaunch(ticket, emulatorPackage, stoppedPackage, noTrackedSession = activeSession == null)) {
                    onLaunchFailed()
                    return@launch
                }
                val prefs = preferencesRepository.preferences.first()
                val canSync = resolvedVariantId == null && emulatorId != null && SavePathRegistry.canSyncWithSettings(
                    emulatorId,
                    prefs.saveSyncEnabled
                )
                android.util.Log.d("GameLaunchDelegate", "launchGame: emulatorPackage=$emulatorPackage, emulatorId=$emulatorId, canSync=$canSync")

                if (canSync) {
                    ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(channelName)))
                }

                var hardcoreConflictInfo: SyncProgress.HardcoreConflict? = null
                var hardcoreConflictChoice: HardcoreConflictChoice? = null
                var localModifiedInfo: SyncProgress.LocalModified? = null
                var localModifiedChoice: LocalModifiedChoice? = null
                var snapshotConflictChoice: SnapshotConflictChoice? = null

                launchWithSyncUseCase.invokeWithProgress(gameId, channelName, skipPreLaunchSync || resolvedVariantId != null).collect { progress ->
                    if (canSync && progress != SyncProgress.Skipped && progress != SyncProgress.Idle) {
                        when (progress) {
                            is SyncProgress.HardcoreConflict -> {
                                hardcoreConflictInfo = progress
                                hardcoreConflictChoice = when (ticket.ask(progress, HARDCORE_CONFLICT_OPTIONS)) {
                                    LaunchPromptOption.KEEP_HARDCORE -> HardcoreConflictChoice.KEEP_HARDCORE
                                    LaunchPromptOption.DOWNGRADE_TO_CASUAL -> HardcoreConflictChoice.DOWNGRADE_TO_CASUAL
                                    LaunchPromptOption.SKIP_HARDCORE_SAVE -> HardcoreConflictChoice.KEEP_LOCAL
                                    else -> null
                                }
                                android.util.Log.d("GameLaunchDelegate", "Hardcore conflict resolved: $hardcoreConflictChoice")
                            }
                            is SyncProgress.LocalModified -> if (progress.snapshotConflict) {
                                localModifiedInfo = progress
                                snapshotConflictChoice = when (ticket.ask(progress, SNAPSHOT_CONFLICT_OPTIONS)) {
                                    LaunchPromptOption.KEEP_MINE -> SnapshotConflictChoice.MINE
                                    LaunchPromptOption.TAKE_THEIRS -> SnapshotConflictChoice.THEIRS
                                    LaunchPromptOption.BRANCH_LOCAL -> SnapshotConflictChoice.BRANCH
                                    LaunchPromptOption.REVERT_LOCAL -> SnapshotConflictChoice.REVERT
                                    else -> null
                                }
                            } else {
                                localModifiedInfo = progress
                                val options = if (progress.restoreFailed) RESTORE_FAILED_OPTIONS else LOCAL_MODIFIED_OPTIONS
                                localModifiedChoice = when (ticket.ask(progress, options)) {
                                    LaunchPromptOption.APPLY_LOCAL -> LocalModifiedChoice.KEEP_LOCAL
                                    LaunchPromptOption.RESTORE_SERVER -> LocalModifiedChoice.RESTORE_SELECTED
                                    LaunchPromptOption.LAUNCH_WITHOUT_SYNC -> LocalModifiedChoice.LAUNCH_WITHOUT_SYNC
                                    else -> null
                                }
                                android.util.Log.d("GameLaunchDelegate", "LocalModified resolved: $localModifiedChoice")
                            }
                            else -> ticket.step(LaunchStep.Save(progress))
                        }
                    }
                }
                if (ticket.isCancelled) {
                    onLaunchFailed()
                    return@launch
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
                        HardcoreConflictChoice.KEEP_HARDCORE -> HardcoreResolutionChoice.KEEP_HARDCORE
                        HardcoreConflictChoice.DOWNGRADE_TO_CASUAL -> HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
                        HardcoreConflictChoice.KEEP_LOCAL -> HardcoreResolutionChoice.KEEP_LOCAL
                    }
                    val resolveResult = saveSyncRepository.resolveHardcoreConflict(resolution, repoChoice)
                    android.util.Log.d("GameLaunchDelegate", "Resolution result: $resolveResult")
                }

                val snapshotChoice = snapshotConflictChoice
                if (localModifiedInfo != null && snapshotChoice != null && emulatorId != null) {
                    resolveSnapshotConflict(gameId, emulatorId, localModifiedInfo!!.channelName, snapshotChoice)
                }

                if (localModifiedInfo != null && localModifiedChoice != null) {
                    val info = localModifiedInfo!!
                    when (localModifiedChoice!!) {
                        LocalModifiedChoice.KEEP_LOCAL -> {
                            android.util.Log.d("GameLaunchDelegate", "User chose to keep local save - caching and uploading as new authoritative version")
                            if (emulatorId != null) {
                                val cacheResult = saveCacheManager.cacheCurrentSave(
                                    gameId = gameId,
                                    emulatorId = emulatorId,
                                    savePath = info.localSavePath,
                                    channelName = info.channelName
                                )
                                android.util.Log.d("GameLaunchDelegate", "Cache result after LocalModified keep: $cacheResult")
                                if (cacheResult is SaveCacheManager.CacheResult.Created) {
                                    activeSaveRepository.activateCache(gameId, cacheResult.cacheId)
                                }
                                activeSaveRepository.setActiveSaveApplied(gameId, true)
                                val uploadResult = saveSyncRepository.uploadSave(
                                    gameId = gameId,
                                    emulatorId = emulatorId,
                                    channelName = info.channelName,
                                    forceOverwrite = true
                                )
                                android.util.Log.d("GameLaunchDelegate", "Upload after LocalModified keep: $uploadResult")
                            }
                        }
                        LocalModifiedChoice.RESTORE_SELECTED -> {
                            android.util.Log.d("GameLaunchDelegate", "User chose to restore selected save - backing up local first")
                            if (emulatorId != null) {
                                saveCacheManager.cacheAsRollback(gameId, emulatorId, info.localSavePath)
                                val downloadResult = saveSyncRepository.downloadSave(
                                    gameId, emulatorId, info.channelName,
                                    knownServerSaveId = info.serverSaveId
                                )
                                android.util.Log.d("GameLaunchDelegate", "Download result after LocalModified restore: $downloadResult")
                                activeSaveRepository.setActiveSaveApplied(gameId, true)
                            }
                        }
                        LocalModifiedChoice.LAUNCH_WITHOUT_SYNC ->
                            android.util.Log.d("GameLaunchDelegate", "User chose to launch with the save on disk; nothing uploaded")
                    }
                }

                if (ticket.isCancelled) {
                    onLaunchFailed()
                    return@launch
                }

                val launchMode = when {
                    !prefs.secureSaves -> overrideLaunchMode?.takeUnless { it.isHardcore }
                    hardcoreConflictChoice == HardcoreConflictChoice.KEEP_HARDCORE -> LaunchMode.RESUME_HARDCORE
                    overrideLaunchMode != null -> overrideLaunchMode
                    isActiveSaveHardcore(gameId) -> LaunchMode.RESUME_HARDCORE
                    shouldDefaultToHardcore(emulatorPackage, game) -> LaunchMode.RESUME_HARDCORE
                    else -> null
                }

                val result = launchGameUseCase(gameId, discId, variantFileId = resolvedVariantId, allowVariantPrompt = allowVariantPrompt, prefetchedGame = game, origin = origin, savesPrepared = true, sessionAtWindow = true)
                dispatchPrimaryLaunchResult(result, ticket, channelName, discId, launchMode, origin, start, onLaunchFailed)
            } finally {
                if (!ticket.isFinished) launchProgressTracker.finish(ticket, launched = false)
            }
        }
    }

    private suspend fun dispatchPrimaryLaunchResult(
        result: LaunchResult,
        ticket: LaunchProgressTracker.Ticket,
        channelName: String?,
        discId: Long?,
        launchMode: LaunchMode?,
        origin: LaunchOrigin,
        start: GameStart,
        onLaunchFailed: () -> Unit
    ) {
        when (result) {
            is LaunchResult.Success -> launchHandoff.handOff(
                ticket,
                applyLaunchExtras(result.intent, launchMode, origin),
                start,
                result.session
            )
            is LaunchResult.SelectDisc -> {
                _discPickerState.value = DiscPickerState(
                    gameId = result.gameId,
                    discs = result.discs,
                    channelName = channelName,
                    launchMode = launchMode,
                    origin = origin,
                    start = start
                )
            }
            is LaunchResult.SelectVariant -> {
                val launchable = result.variants
                    .filter { it.fileId == null || it.isDownloaded }
                    .sortedBy { com.nendo.argosy.data.model.VariantCategory.fromKey(it.category).sortOrder }
                if (launchable.size <= 1) {
                    val retry = launchGameUseCase(result.gameId, discId, allowVariantPrompt = false, origin = origin, savesPrepared = true, sessionAtWindow = true)
                    dispatchPrimaryLaunchResult(retry, ticket, channelName, discId, launchMode, origin, start, onLaunchFailed)
                } else {
                    _variantPickerState.value = VariantPickerState(
                        gameId = result.gameId,
                        variants = launchable,
                        channelName = channelName,
                        launchMode = launchMode,
                        origin = origin,
                        start = start
                    )
                }
            }
            is LaunchResult.SelectMemcard -> {
                _memcardPickerState.value = MemcardPickerState(
                    gameId = result.gameId,
                    emulatorId = result.emulatorId,
                    platformName = result.platformName,
                    cards = result.cards,
                    channelName = channelName,
                    launchMode = launchMode,
                    origin = origin,
                    start = start
                )
            }
            else -> dispatchErrorResult(result, onLaunchFailed)
        }
    }

    private fun dispatchErrorResult(result: LaunchResult, onLaunchFailed: () -> Unit) {
        if (result is LaunchResult.Cancelled) {
            onLaunchFailed()
            return
        }
        hapticManager.vibrate(HapticPattern.ERROR)
        when (result) {
            is LaunchResult.NoEmulator -> notificationManager.showError(
                NotificationText.Res(R.string.notif_gamelaunch_no_emulator)
            )
            is LaunchResult.NoRomFile -> notificationManager.showError(
                NotificationText.Res(R.string.notif_gamelaunch_no_rom_file)
            )
            is LaunchResult.NoSteamLauncher -> notificationManager.showError(
                NotificationText.Res(when (result.reason) {
                    LaunchResult.NoSteamLauncher.Reason.CONFIGURED_MISSING -> R.string.notif_gamelaunch_no_steam_launcher
                    LaunchResult.NoSteamLauncher.Reason.NONE_INSTALLED -> R.string.notif_gamelaunch_no_steam_launcher_installed
                    LaunchResult.NoSteamLauncher.Reason.NO_REPORTED_REPLACEMENT -> R.string.notif_gamelaunch_steam_launcher_unavailable
                    LaunchResult.NoSteamLauncher.Reason.UPDATING -> R.string.notif_gamelaunch_steam_launcher_updating
                })
            )
            is LaunchResult.NoCore -> {
                val message = result.reason?.let {
                    NotificationText.Res(R.string.notif_gamelaunch_no_core_with_reason, listOf(result.platformSlug, it))
                } ?: NotificationText.Res(R.string.notif_gamelaunch_no_core_base, listOf(result.platformSlug))
                notificationManager.showError(message)
            }
            is LaunchResult.MissingDiscs -> {
                val discText = result.missingDiscNumbers.joinToString(", ")
                notificationManager.showError(
                    NotificationText.Res(R.string.notif_gamelaunch_missing_discs, listOf(discText))
                )
            }
            is LaunchResult.MissingBios -> notificationManager.showError(
                NotificationText.Res(R.string.notif_gamelaunch_missing_bios, listOf(result.platformSlug))
            )
            is LaunchResult.NoScummVMGameId -> notificationManager.showError(
                NotificationText.Res(R.string.notif_gamelaunch_no_scummvm_id, listOf(result.gameName))
            )
            is LaunchResult.NoAndroidApp -> notificationManager.showError(
                NotificationText.Res(R.string.notif_gamelaunch_no_android_app, listOf(result.packageName))
            )
            is LaunchResult.Error -> notificationManager.showError(NotificationText.Raw(result.message))
            else -> { /* Success/SelectDisc/SelectVariant handled elsewhere */ }
        }
        onLaunchFailed()
    }

    private fun applyLaunchExtras(intent: Intent, launchMode: LaunchMode?, origin: LaunchOrigin): Intent {
        if (launchMode != null) intent.putExtra(LaunchMode.EXTRA_LAUNCH_MODE, launchMode.name)
        if (origin != LaunchOrigin.INTERNAL) intent.putExtra(LaunchOrigin.EXTRA_LAUNCH_ORIGIN, origin.name)
        if (intent.component?.className == LibretroActivity::class.java.name) {
            connectedControllerTracker.activeDevice()
                ?.takeIf { it.isPhysicalGamepad() }
                ?.let { pad ->
                    intent.putExtra(LibretroActivity.EXTRA_LAUNCH_CONTROLLER_ID, controllerIdOf(pad))
                }
        }
        return intent
    }

    fun selectDisc(scope: CoroutineScope, discPath: String) {
        val state = _discPickerState.value ?: return
        _discPickerState.value = null
        val onRefused = _onLaunchFailed ?: {}
        _onLaunchFailed = null

        scope.launch {
            continuePickedLaunch(state.gameId, onRefused = onRefused) { ticket ->
                val result = launchGameUseCase(
                    gameId = state.gameId,
                    selectedDiscPath = discPath,
                    origin = state.origin,
                    savesPrepared = true,
                    sessionAtWindow = true
                )
                when (result) {
                    is LaunchResult.Success -> launchHandoff.handOff(
                        ticket,
                        applyLaunchExtras(result.intent, state.launchMode, state.origin),
                        state.start,
                        result.session
                    )
                    LaunchResult.Cancelled -> Unit
                    is LaunchResult.Error -> notificationManager.showError(NotificationText.Raw(result.message))
                    else -> notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_disc_launch_failed))
                }
            }
        }
    }

    private suspend fun continuePickedLaunch(
        gameId: Long,
        onRefused: () -> Unit,
        block: suspend (LaunchProgressTracker.Ticket) -> Unit
    ) {
        val ticket = launchProgressTracker.begin(gameId, gameRepository.getById(gameId)?.title) ?: run {
            onRefused()
            return
        }
        try {
            block(ticket)
        } finally {
            if (!ticket.isFinished) launchProgressTracker.finish(ticket, launched = false)
        }
    }

    fun dismissDiscPicker() {
        _discPickerState.value = null
        _onLaunchFailed?.invoke()
        _onLaunchFailed = null
    }

    fun selectVariant(scope: CoroutineScope, variantFileId: Long?) {
        val state = _variantPickerState.value ?: return
        _variantPickerState.value = null
        val onFailed = _onLaunchFailed ?: {}
        _onLaunchFailed = null

        scope.launch {
            continuePickedLaunch(state.gameId, onRefused = onFailed) { ticket ->
                val result = if (variantFileId != null) {
                    launchGameUseCase(gameId = state.gameId, variantFileId = variantFileId, origin = state.origin, savesPrepared = true, sessionAtWindow = true)
                } else {
                    launchGameUseCase(gameId = state.gameId, skipVariantPrompt = true, origin = state.origin, savesPrepared = true, sessionAtWindow = true)
                }
                dispatchPrimaryLaunchResult(result, ticket, state.channelName, null, state.launchMode, state.origin, state.start, onFailed)
            }
        }
    }

    fun dismissVariantPicker() {
        _variantPickerState.value = null
        _onLaunchFailed?.invoke()
        _onLaunchFailed = null
    }

    fun selectMemcard(scope: CoroutineScope, cardPath: String) {
        val state = _memcardPickerState.value ?: return
        _memcardPickerState.value = null
        val onRefused = _onLaunchFailed ?: {}
        _onLaunchFailed = null

        scope.launch {
            emulatorSaveConfigRepository.setMemcardPath(state.emulatorId, cardPath)
            continuePickedLaunch(state.gameId, onRefused = onRefused) { ticket ->
                val result = launchGameUseCase(
                    gameId = state.gameId,
                    origin = state.origin,
                    savesPrepared = true,
                    sessionAtWindow = true
                )
                when (result) {
                    is LaunchResult.Success -> launchHandoff.handOff(
                        ticket,
                        applyLaunchExtras(result.intent, state.launchMode, state.origin),
                        state.start,
                        result.session
                    )
                    LaunchResult.Cancelled -> Unit
                    is LaunchResult.Error -> notificationManager.showError(NotificationText.Raw(result.message))
                    else -> notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
                }
            }
        }
    }

    fun dismissMemcardPicker() {
        _memcardPickerState.value = null
        _onLaunchFailed?.invoke()
        _onLaunchFailed = null
    }

    fun launchSimple(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        launchMode: LaunchMode? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        callbacks: LaunchResultCallbacks
    ) {
        scope.launch {
            val game = gameRepository.getById(gameId)
            val ticket = launchProgressTracker.begin(game?.id, game?.title) ?: run {
                android.util.Log.d("GameLaunchDelegate", "launchSimple: another launch is in progress")
                callbacks.onLaunchFailed()
                return@launch
            }
            try {
                val continuesPickedLaunch = selectedDiscPath != null || variantFileId != null || skipVariantPrompt
                if (game != null && !continuesPickedLaunch && !preLaunchRecapStep.run(ticket, gameId, game.title)) {
                    callbacks.onLaunchFailed()
                    return@launch
                }
                val activeSession = playSessionTracker.activeSession.value
                val stoppedPackage = activeSession?.let {
                    android.util.Log.d("GameLaunchDelegate", "launchSimple: ending session for game ${it.gameId} before fresh launch")
                    evictSessionReturningStopped(it)
                }
                val cleared = game == null || emulatorClearedForLaunch(
                    ticket,
                    emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug),
                    stoppedPackage,
                    noTrackedSession = activeSession == null
                )
                if (!cleared) {
                    callbacks.onLaunchFailed()
                    return@launch
                }
                val rememberedVariantId = if (variantFileId == null && !allowVariantPrompt) {
                    game?.let { variantResolver.resolveVariant(it)?.id }
                } else {
                    null
                }
                if (ticket.isCancelled) {
                    callbacks.onLaunchFailed()
                    return@launch
                }
                val result = launchGameUseCase(
                    gameId = gameId,
                    discId = discId,
                    selectedDiscPath = selectedDiscPath,
                    variantFileId = variantFileId ?: rememberedVariantId,
                    skipVariantPrompt = skipVariantPrompt,
                    allowVariantPrompt = allowVariantPrompt,
                    prefetchedGame = game,
                    origin = origin,
                    sessionAtWindow = true
                )
                dispatchSimpleResult(result, ticket, launchMode, origin, callbacks)
            } finally {
                if (!ticket.isFinished) launchProgressTracker.finish(ticket, launched = false)
            }
        }
    }

    private suspend fun dispatchSimpleResult(
        result: LaunchResult,
        ticket: LaunchProgressTracker.Ticket,
        launchMode: LaunchMode?,
        origin: LaunchOrigin,
        callbacks: LaunchResultCallbacks
    ) {
        when (result) {
            is LaunchResult.Success -> launchHandoff.handOff(
                ticket,
                applyLaunchExtras(result.intent, launchMode, origin),
                callbacks.start,
                result.session
            )
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
            is LaunchResult.SelectMemcard -> {
                _memcardPickerState.value = MemcardPickerState(
                    gameId = result.gameId,
                    emulatorId = result.emulatorId,
                    platformName = result.platformName,
                    cards = result.cards,
                    launchMode = launchMode,
                    origin = origin,
                    start = callbacks.start
                )
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
