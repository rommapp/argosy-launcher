package com.nendo.argosy

import android.hardware.display.DisplayManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.hardware.CompanionGuardService
import com.nendo.argosy.util.DisplayRoleResolver
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.reportTitleIdRecheck
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualCollectionItem
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.toJsonString
import com.nendo.argosy.ui.dualscreen.gamedetail.toSaveEntryData
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileType
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.dualscreen.home.toShowcaseState
import com.nendo.argosy.ui.input.InputDedupBuffer
import com.nendo.argosy.ui.input.InputSignature
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.hardware.FocusAccessibilityService
import com.nendo.argosy.hardware.FocusDirectorActivity
import com.nendo.argosy.hardware.SecondaryHomeActivity
import com.nendo.argosy.hardware.withLiveQuickActionState
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.SecondaryDisplayType
import kotlinx.coroutines.CoroutineScope
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DualScreenManager"

class DualScreenManager(
    context: Context,
    private var scope: CoroutineScope,
    internal val gameDao: GameDao,
    internal val gameRepository: com.nendo.argosy.data.repository.GameRepository,
    internal val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    internal val prefetchGameSaveDataUseCase:
        com.nendo.argosy.domain.usecase.sync.PrefetchGameSaveDataUseCase,
    internal val platformRepository: PlatformRepository,
    internal val collectionRepository: CollectionRepository,
    internal val downloadQueueDao: DownloadQueueDao,
    internal val downloadQueueRepository: com.nendo.argosy.data.repository.DownloadQueueRepository,
    internal val gameFileDao: GameFileDao,
    private val downloadManager: DownloadManager,
    private val gameActionsDelegate: GameActionsDelegate,
    private val syncPlatformUseCase: com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val saveCacheManager: SaveCacheManager,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val getUnifiedStatesUseCase:
        com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase,
    private val stateCacheManager: com.nendo.argosy.data.repository.StateCacheManager,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val activateSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.ActivateSaveChannelUseCase,
    private val restoreSaveChannelPointUseCase:
        com.nendo.argosy.domain.usecase.savechannel.RestoreSaveChannelPointUseCase,
    private val createSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.CreateSaveChannelUseCase,
    private val copySaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.CopySaveChannelUseCase,
    private val restoreStateUseCase:
        com.nendo.argosy.domain.usecase.state.RestoreStateUseCase,
    private val emulatorResolver: EmulatorResolver,
    private val coreVersionExtractor: com.nendo.argosy.data.emulator.CoreVersionExtractor,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    internal val displayAffinityHelper: DisplayAffinityHelper,
    internal val sessionStateStore: SessionStateStore,
    internal val preferencesRepository: UserPreferencesRepository,
    internal val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    internal val resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase,
    internal val hapticManager: com.nendo.argosy.ui.input.HapticFeedbackManager,
    internal val soundManager: com.nendo.argosy.ui.input.SoundFeedbackManager,
    internal val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    internal val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    internal val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    internal val appsRepository: com.nendo.argosy.data.repository.AppsRepository,
    private val notificationManager: com.nendo.argosy.core.notification.NotificationManager,
    private val titleIdDownloadObserver: com.nendo.argosy.data.emulator.TitleIdDownloadObserver,
    internal val homeGridPageRepository: com.nendo.argosy.data.repository.HomeGridPageRepository,
    internal val pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource,
    internal val ambientAudioManager: com.nendo.argosy.ui.audio.AmbientAudioManager,
    internal val emulatorConfigDao: com.nendo.argosy.data.local.dao.EmulatorConfigDao,
    internal val configureEmulatorUseCase: com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase,
    internal val builtinCoreResolver: com.nendo.argosy.data.emulator.BuiltinCoreResolver,
    internal val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry,
    internal val steamDownloadQueueDao: com.nendo.argosy.data.local.dao.SteamDownloadQueueDao,
    internal val steamRepository: com.nendo.argosy.data.repository.SteamRepository,
    internal val playSessionTracker: com.nendo.argosy.data.emulator.PlaySessionTracker,
    internal val permissionHelper: com.nendo.argosy.util.PermissionHelper,
    internal val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager,
    internal val repairImageCacheUseCase: com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase? = null,
    internal val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
    internal val gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate,
    private val filePickerFlow: com.nendo.argosy.domain.usecase.download.FilePickerFlowUseCase,
    private val gameThemeAudioCoordinator: com.nendo.argosy.ui.audio.GameThemeAudioCoordinator,
    internal val getPinnedCollectionsUseCase: com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase? = null,
    internal val getGamesForPinnedCollectionUseCase: com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase? = null,
    internal val advanceCollectionFocusUseCase:
        com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase,
    internal val prepareCollectionQueueUseCase:
        com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase,
    internal val mediaRepository: com.nendo.argosy.data.repository.MediaRepository,
    private val mediaPlaybackTracker: com.nendo.argosy.data.media.MediaPlaybackTracker,
    initialRolesSwapped: Boolean = false
) {

    private val appContext: Context = context.applicationContext
    private var preGameRolesSwapped: Boolean? = null
    private var activityContext: Context = context
    private var lastStateEntries: Pair<Long, List<UnifiedStateEntry>>? = null

    private val _isRolesSwapped = MutableStateFlow(initialRolesSwapped)
    val isRolesSwapped: StateFlow<Boolean> = _isRolesSwapped

    fun setRolesSwapped(value: Boolean) {
        _isRolesSwapped.value = value
    }

    /**
     * The display the viewer is driving right now, or null on a single-screen device.
     *
     * Exactly one screen is interactive at a time and carries Home, Library and Media; the other
     * describes whatever that screen has focused. A role swap is the only thing that moves them,
     * so a surface asks which display holds its role rather than naming a display id, and keeps
     * landing correctly after a swap.
     */
    fun interactiveDisplayId(): Int? =
        displayAffinityHelper.getRoleDisplayIds(_isRolesSwapped.value)?.first

    /**
     * The display describing what the interactive one has focused, or null on a single screen.
     */
    fun showcaseDisplayId(): Int? =
        displayAffinityHelper.getRoleDisplayIds(_isRolesSwapped.value)?.second

    private val _isDualScreenDevice = MutableStateFlow(displayAffinityHelper.hasSecondaryDisplay)
    val isDualScreenDevice: StateFlow<Boolean> = _isDualScreenDevice

    fun setDualScreenDevice(value: Boolean) {
        _isDualScreenDevice.value = value
    }

    fun applyDualScreenEnabled(enabled: Boolean, isToggle: Boolean) {
        setSecondaryHomeComponentEnabled(enabled && displayAffinityHelper.secondaryDisplayUsable)
        if (!isToggle) return
        if (enabled) {
            reprobeSecondaryDisplay()
            ensureCompanionLaunched()
        } else {
            teardownCompanion()
        }
    }

    /**
     * Clears a recorded companion-initialization failure so the next launch attempt re-probes the
     * display. Called when the display topology changes or the user re-enables dual screen.
     */
    fun reprobeSecondaryDisplay() {
        companionLaunchAttempts = 0
        displayAffinityHelper.secondaryDisplayUsable = true
        sessionStateStore.setSecondaryDisplayUsable(true)
        setSecondaryHomeComponentEnabled(sessionStateStore.isDualScreenEnabled())
        _isDualScreenDevice.value = displayAffinityHelper.hasSecondaryDisplay
    }

    /**
     * Safety net for a companion that cannot initialize on the secondary display: releases the
     * display back to the OS, drops the launcher to single-screen, and stops the relaunch loop.
     * [persistent] records the refusal across restarts, for a display that structurally rejects
     * the companion rather than a transient launch failure.
     */
    fun fallbackToSingleScreen(persistent: Boolean) {
        if (!displayAffinityHelper.secondaryDisplayUsable) return
        Log.w(TAG, "Companion could not initialize on the secondary display, falling back to single screen (persistent=$persistent)")
        displayAffinityHelper.secondaryDisplayUsable = false
        if (persistent) sessionStateStore.setSecondaryDisplayUsable(false)
        companionLaunchAttempts = 0
        cleanupSwappedState()
        teardownCompanion()
        setSecondaryHomeComponentEnabled(false)
        _isDualScreenDevice.value = false
    }

    private fun setSecondaryHomeComponentEnabled(enabled: Boolean) {
        com.nendo.argosy.util.SecondaryHomeComponent.setEnabled(appContext, enabled)
    }

    /** Live in-memory session check; unlike SessionStateStore.hasActiveSession this flips false the moment session teardown begins, not after save sync completes. */
    fun hasLiveSession(): Boolean = playSessionTracker.activeSession.value != null

    fun teardownCompanion() {
        stopStartupGuard()
        companionLaunchJob?.cancel()
        companionLaunchJob = null
        companionWatchdogJob?.cancel()
        _isCompanionActive.value = false
        CompanionGuardService.stop(appContext)
        companionHost?.finishCompanion()
    }

    /**
     * Dedups raw Android input events seen across all dispatch paths (primary activity,
     * companion activity, libretro activity, and forwarded keys). The first path to
     * [claimInput] wins; parallel deliveries of the same physical event get dropped.
     */
    private val inputDedup = InputDedupBuffer()

    fun claimInput(event: android.view.KeyEvent): Boolean =
        inputDedup.claim(InputSignature.of(event)).also { if (it) notifyUserActivity() }

    fun claimInput(event: android.view.MotionEvent): Boolean =
        inputDedup.claim(InputSignature.of(event)).also { if (it) notifyUserActivity() }

    private val _userActive = MutableStateFlow(false)

    /**
     * Whether the person is currently using the device, from either screen's point of view.
     *
     * Android only credits user activity to the display an event landed on, so a pad press driving
     * the companion leaves the other screen idling towards its dim. Both windows hold themselves
     * awake while this is raised, which makes one input count for both.
     *
     * It falls again after the system's own screen-off timeout, so the setting the user chose still
     * decides when the screens go dark; this only decides what counts as having used them.
     */
    val userActive: StateFlow<Boolean> = _userActive

    private var userIdleJob: Job? = null

    private fun notifyUserActivity() {
        _userActive.value = true
        userIdleJob?.cancel()
        userIdleJob = scope.launch {
            delay(screenOffTimeoutMs())
            _userActive.value = false
        }
    }

    private fun screenOffTimeoutMs(): Long {
        val configured = android.provider.Settings.System.getInt(
            appContext.contentResolver,
            android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
            DEFAULT_SCREEN_OFF_TIMEOUT_MS.toInt()
        ).toLong()
        return configured.coerceIn(MIN_SCREEN_OFF_TIMEOUT_MS, MAX_SCREEN_OFF_TIMEOUT_MS)
    }

    fun claimInput(signature: InputSignature): Boolean = inputDedup.claim(signature)

    fun rebind(activity: android.app.Activity, newScope: CoroutineScope) {
        activityContext = activity
        scope = newScope
        companionWatchdogJob?.cancel()
        companionLaunchJob?.cancel()
        observeActiveAccount()
        observeMedia()
    }
    interface CompanionHost {
        fun onForegroundChanged(isForeground: Boolean)
        fun onWizardStateChanged(isActive: Boolean)
        fun onSaveDirtyChanged(isDirty: Boolean)
        fun onSessionStarted(gameId: Long, isHardcore: Boolean, channelName: String?)
        fun onSessionEnded()
        fun onHomeAppsChanged(apps: List<String>)
        fun onLibraryRefresh()
        fun onAccountSwitched()
        fun onOverlayRequested(eventName: String)
        fun onRoleSwapped(isSwapped: Boolean)
        fun onOverlayClosed()
        fun onBackgroundForward()
        /**
         * A key from the primary display. [action] and [repeatCount] come straight from the source
         * event because the companion cannot tell a tap from a hold without them, and a forwarded
         * down-only stream makes every press look instantaneous.
         */
        fun onForwardKey(
            keyCode: Int,
            action: Int,
            repeatCount: Int,
            swapAB: Boolean,
            swapXY: Boolean,
            swapStartSelect: Boolean
        )
        fun refocusSelf()
        fun onGameDetailOpened(gameId: Long)
        fun onGameDetailClosed()
        fun onScreenshotSelected(index: Int)
        fun onScreenshotCleared()
        fun onModalResult(dismissed: Boolean, type: String?, value: Int, statusSelected: String?, selectedIndex: Int, collectionToggleId: Long, collectionCreateName: String?)
        fun onDirectActionResult(type: String, gameId: Long)
        fun onSaveDataReceived(json: String, activeChannel: String?, activeTimestamp: Long?, syncing: Boolean = false)
        fun onStateEntriesReceived(entries: List<com.nendo.argosy.domain.model.UnifiedStateEntry>)
        fun onSavesSyncDone()
        fun onDownloadCompleted(gameId: Long)
        fun onSessionActionsChanged(available: Boolean)
        fun onHasQuickSaveChanged(hasQuickSave: Boolean)
        fun finishCompanion()
    }

    interface SessionQuickActions {
        fun quickSave()
        fun quickLoad()
        fun screenshot()
    }

    var sessionQuickActions: SessionQuickActions? = null
        set(value) {
            field = value
            _swappedCompanionState.update { it.copy(quickActionsAvailable = value != null) }
            companionHost?.onSessionActionsChanged(value != null)
        }

    fun updateCompanionHasQuickSave(hasQuickSave: Boolean) {
        _swappedCompanionState.update { it.copy(hasQuickSave = hasQuickSave) }
        companionHost?.onHasQuickSaveChanged(hasQuickSave)
    }

    var sessionRefocus: (() -> Unit)? = null

    var companionHost: CompanionHost? = null
    var onEmulatorDispatcherChanged: (() -> Unit)? = null
    var emulatorKeyDispatcher: ((android.view.KeyEvent) -> Boolean)? = null
        set(value) {
            field = value
            onEmulatorDispatcherChanged?.invoke()
        }
    var emulatorMotionDispatcher: ((android.view.MotionEvent) -> Boolean)? = null

    /**
     * The display the running game occupies, mirrored to disk so it survives the process.
     *
     * A launcher killed under a running game comes back with this field null, and null reads as
     * "the game is on my display" at every site that exempts a cross-display session - which is how
     * a restart ends up tearing down, and taking the screen from, a game running on the other panel.
     * The persisted value is only consulted while the field is empty and only while a session is
     * recorded behind it, so a live launch always wins and a finished game never leaves a display
     * claim behind it.
     */
    var emulatorDisplayId: Int? = null
        get() = field ?: persistedEmulatorDisplayId()
        set(value) {
            field = value
            sessionStateStore.setEmulatorDisplayId(value)
        }

    private fun persistedEmulatorDisplayId(): Int? =
        if (sessionStateStore.hasActiveSession()) sessionStateStore.getEmulatorDisplayId() else null

    var isLaunchingGame = false
        private set
    private var launchGuardJob: Job? = null

    fun onFocusLostToEmulator() {
        if (isLaunchingGame) {
            isLaunchingGame = false
            launchGuardJob?.cancel()
            launchGuardJob = null
        }
    }

    val isExternalDisplay: Boolean
        get() = displayAffinityHelper.secondaryDisplayType == SecondaryDisplayType.EXTERNAL

    var onRoleSwapped: ((Boolean) -> Unit)? = null

    /**
     * What a press on the companion display feels and sounds like. That screen routes its own key
     * events rather than going through the dispatcher, so without this it is silent and still while
     * the main screen is not.
     */
    val inputFeedback = com.nendo.argosy.ui.input.InputFeedbackPlayer(hapticManager, soundManager)

    private val _companionDetail = MutableStateFlow<CompanionDetail?>(null)

    /**
     * What the showcase screen shows while the driven screen is on Library or Media.
     *
     * Held here rather than pushed through a callback because the two surfaces live in different
     * activities and the callback route has already proved it can be implemented as an empty method
     * and never noticed. A flow that nobody collects renders nothing; a callback that nobody
     * implements looks exactly like one that works.
     *
     * Null means the driven screen is on Home, and the showcase falls back to the game it has
     * focused there.
     */
    val companionDetail: StateFlow<CompanionDetail?> = _companionDetail

    fun setCompanionDetail(detail: CompanionDetail?) {
        _companionDetail.value = detail
    }

    private val _dualScreenShowcase = MutableStateFlow(DualHomeShowcaseState())
    val dualScreenShowcase: StateFlow<DualHomeShowcaseState> = _dualScreenShowcase

    private val _dualGameDetailState = MutableStateFlow<DualGameDetailUpperState?>(null)
    val dualGameDetailState: StateFlow<DualGameDetailUpperState?> = _dualGameDetailState

    private val _isCompanionActive = MutableStateFlow(false)
    val isCompanionActive: StateFlow<Boolean> = _isCompanionActive

    private val _dualViewMode = MutableStateFlow("CAROUSEL")
    val dualViewMode: StateFlow<String> = _dualViewMode

    private val _dualAppBarFocused = MutableStateFlow(false)
    val dualAppBarFocused: StateFlow<Boolean> = _dualAppBarFocused

    private val _dualDrawerOpen = MutableStateFlow(false)
    val dualDrawerOpen: StateFlow<Boolean> = _dualDrawerOpen

    private val _dualCollectionShowcase = MutableStateFlow(
        DualCollectionShowcaseState()
    )
    val dualCollectionShowcase: StateFlow<DualCollectionShowcaseState> =
        _dualCollectionShowcase

    /**
     * What the player has open, mirrored here so the companion reads playback the same way it reads
     * every other cross-display fact. The player is still the only writer; this is the door the
     * companion comes through.
     */
    val mediaPlayback: StateFlow<com.nendo.argosy.data.media.ActiveMediaPlayback?> =
        mediaPlaybackTracker.activePlayback

    private val _mediaSignedIn = MutableStateFlow(false)

    /**
     * Whether a media account exists at all. The companion's media button is offered on this rather
     * than on a live playback, so the panel is reachable to pick something up from where it was left
     * as well as to look at what is already running.
     */
    val mediaSignedIn: StateFlow<Boolean> = _mediaSignedIn

    private val _companionMediaVisible = MutableStateFlow(false)

    /**
     * Whether the companion is showing the media panel rather than its home.
     *
     * Context decides it: a playback opening turns it on and a playback ending turns it off, so a
     * film starting on the main screen puts its episode list on the companion without anyone asking.
     * [toggleCompanionMediaView] is the manual override on top of that, which is why the flag is
     * held rather than derived - a derived flag could not be overridden.
     */
    val companionMediaVisible: StateFlow<Boolean> = _companionMediaVisible

    /**
     * The display the player window currently occupies, reported by the player itself. Held here
     * rather than in the activity because the companion has to be able to send an episode to a
     * window living on another display.
     *
     * It is an observation, not an assignment, so it is cleared when the roles swap: the position
     * it records was chosen under the previous arrangement, and sending the next episode there
     * would place it by a rule that no longer applies.
     */
    @Volatile var mediaPlayerDisplayId: Int? = null

    fun toggleCompanionMediaView() {
        _companionMediaVisible.value = !_companionMediaVisible.value &&
            (_mediaSignedIn.value || mediaPlaybackTracker.activePlayback.value != null)
    }

    fun setCompanionMediaVisible(visible: Boolean) {
        _companionMediaVisible.value = visible
    }

    /**
     * Where the player should move now that a game has taken a screen, or null when there is nowhere
     * to move it to. Null is the single-screen answer and also the answer when the game and the
     * player are already on different displays.
     */
    fun mediaPlayerRelocationDisplayId(): Int? =
        displayAffinityHelper.getMediaPlayerDisplayId(emulatorDisplayId)

    /**
     * Opens one media item in the player window wherever that window currently is.
     *
     * The companion is the caller: it lists the episodes of what is playing, and confirming one has
     * to reach a window on the other display. A live player states where it is, and that wins,
     * because resolution for a non-emulator activity answers "the secondary display"
     * unconditionally and would drag the film off the screen it is being watched on.
     *
     * Resolution is the fallback for when nothing has stated a position yet. Without it the first
     * play of a session lands on the default display, on top of the launcher, where the home UI
     * closes it the moment it comes forward.
     */
    fun playMediaItem(itemId: String, startOver: Boolean = false) {
        if (itemId.isBlank()) return
        val target = mediaPlayerDisplayId ?: mediaPlayerRelocationDisplayId()
        val options = target?.let {
            displayAffinityHelper.getActivityOptions(forEmulator = false, overrideDisplayId = it)
        }
        com.nendo.argosy.ui.screens.player.PlayerActivity.startOnDisplay(
            context = appContext,
            args = com.nendo.argosy.ui.screens.player.PlayerArgs(
                itemId = itemId,
                startPositionMs = if (startOver) 0L else
                    com.nendo.argosy.ui.screens.player.PlayerActivity.RESOLVE_RESUME
            ),
            options = options
        )
    }

    private var accountObserverJob: Job? = null
    private var mediaObserverJob: Job? = null

    init {
        scope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                menuWrapMode = prefs.menuWrapMode
                _dualScreenShowcase.update {
                    it.copy(
                        useGameBackground = prefs.useGameBackground,
                        customWallpaperPath = prefs.customBackgroundPath
                    )
                }
            }
        }
        observeActiveAccount()
        observeMedia()
    }

    /**
     * Turns the companion's media panel on and off with the playback itself.
     *
     * This is the whole of "automatic by context": nobody asks for the panel, it is there while
     * something is being watched and gone afterwards. A manual return to home during a playback
     * survives, because only the transitions write here - a viewer who went back to their library
     * mid-film is not sent to the film again on the next transport change.
     */
    private fun observeMedia() {
        mediaObserverJob?.cancel()
        mediaObserverJob = scope.launch {
            launch {
                mediaRepository.isSignedIn.collect { _mediaSignedIn.value = it }
            }
            var wasOpen = false
            mediaPlaybackTracker.activePlayback.collect { playback ->
                val isOpen = playback != null
                if (isOpen == wasOpen) return@collect
                wasOpen = isOpen
                _companionMediaVisible.value = isOpen
                if (!isOpen) mediaPlayerDisplayId = null
            }
        }
    }

    /**
     * Watches the stored RomM user id, which `RomMAccountRepository.activate` rewrites as the
     * identity half of an account switch.
     *
     * Nothing else moves the companion off the previous account: its home sections, games and
     * game-detail state are loaded with one-shot queries rather than DB flows, so recents,
     * last-played and the rest of the per-account overlay would keep rendering the outgoing
     * account's library until something pushed a refresh.
     */
    private fun observeActiveAccount() {
        accountObserverJob?.cancel()
        accountObserverJob = scope.launch {
            var lastUserId: Long? = null
            var seeded = false
            preferencesRepository.userPreferences.collect { prefs ->
                val userId = prefs.rommUserId
                if (!seeded) {
                    seeded = true
                    lastUserId = userId
                    return@collect
                }
                if (userId == lastUserId) return@collect
                lastUserId = userId
                onActiveAccountChanged()
            }
        }
    }

    private fun onActiveAccountChanged() {
        Log.i(TAG, "Active RomM account changed, resetting companion-visible state")
        _dualGameDetailState.value = null
        _swappedGameDetailViewModel = null
        _swappedCurrentScreen.value = com.nendo.argosy.hardware.CompanionScreen.HOME
        _dualSyncOverlay.value = null
        _dualSaveConflict.value = null
        swappedDualHomeViewModel?.refresh()
        companionHost?.onAccountSwitched()
    }

    @Volatile private var menuWrapMode: com.nendo.argosy.data.preferences.MenuWrapMode =
        com.nendo.argosy.data.preferences.MenuWrapMode.HARD_STOP

    private val _dualSyncOverlay = MutableStateFlow<com.nendo.argosy.ui.screens.common.SyncOverlayState?>(null)
    val dualSyncOverlay: StateFlow<com.nendo.argosy.ui.screens.common.SyncOverlayState?> = _dualSyncOverlay

    private val _dualSyncOverlayFocusIndex = MutableStateFlow(0)
    val dualSyncOverlayFocusIndex: StateFlow<Int> = _dualSyncOverlayFocusIndex

    fun moveSyncConflictFocus(direction: Int) {
        val state = _dualSyncOverlay.value ?: return
        val maxIndex = when (state.syncProgress) {
            is com.nendo.argosy.domain.model.SyncProgress.HardcoreConflict -> 2
            is com.nendo.argosy.domain.model.SyncProgress.LocalModified -> 1
            is com.nendo.argosy.domain.model.SyncProgress.PostSessionConflict -> 1
            else -> return
        }
        _dualSyncOverlayFocusIndex.value = (_dualSyncOverlayFocusIndex.value + direction).coerceIn(0, maxIndex)
    }

    fun handleConflictInput(keyCode: Int, swapAB: Boolean, swapXY: Boolean, swapStartSelect: Boolean): Boolean {
        if (!_isDualScreenDevice.value) return false
        if (_dualSyncOverlay.value == null && _dualSaveConflict.value == null) return false
        val event = com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
            ?: return true
        if (_dualSyncOverlay.value != null) {
            when (event) {
                com.nendo.argosy.ui.input.GamepadEvent.Up -> moveSyncConflictFocus(-1)
                com.nendo.argosy.ui.input.GamepadEvent.Down -> moveSyncConflictFocus(1)
                com.nendo.argosy.ui.input.GamepadEvent.Confirm -> confirmSyncConflict()
                com.nendo.argosy.ui.input.GamepadEvent.Back -> dismissSyncConflict()
                else -> {}
            }
            return true
        }
        if (_dualSaveConflict.value != null) {
            when (event) {
                com.nendo.argosy.ui.input.GamepadEvent.Left,
                com.nendo.argosy.ui.input.GamepadEvent.Up -> moveSaveConflictFocus(-1)
                com.nendo.argosy.ui.input.GamepadEvent.Right,
                com.nendo.argosy.ui.input.GamepadEvent.Down -> moveSaveConflictFocus(1)
                com.nendo.argosy.ui.input.GamepadEvent.Confirm -> confirmSaveConflict()
                com.nendo.argosy.ui.input.GamepadEvent.Back -> dismissSaveConflict()
                else -> {}
            }
            return true
        }
        return false
    }

    fun confirmSyncConflict() {
        val state = _dualSyncOverlay.value ?: return
        val wasPostSession = state.syncProgress is com.nendo.argosy.domain.model.SyncProgress.PostSessionConflict
        val index = _dualSyncOverlayFocusIndex.value
        when (state.syncProgress) {
            is com.nendo.argosy.domain.model.SyncProgress.HardcoreConflict -> when (index) {
                0 -> state.onKeepHardcore?.invoke()
                1 -> state.onDowngradeToCasual?.invoke()
                2 -> state.onKeepLocal?.invoke()
            }
            is com.nendo.argosy.domain.model.SyncProgress.LocalModified -> when (index) {
                0 -> state.onKeepLocalModified?.invoke()
                1 -> state.onRestoreSelected?.invoke()
            }
            is com.nendo.argosy.domain.model.SyncProgress.PostSessionConflict -> when (index) {
                0 -> state.syncProgress.onSkipSync?.invoke()
                1 -> state.syncProgress.onOverwrite?.invoke()
            }
            else -> {}
        }
        _dualSyncOverlay.value = null
        _dualSyncOverlayFocusIndex.value = 0
    }

    fun dismissSyncConflict() {
        val state = _dualSyncOverlay.value ?: return
        when (state.syncProgress) {
            is com.nendo.argosy.domain.model.SyncProgress.HardcoreConflict -> state.onKeepLocal?.invoke()
            is com.nendo.argosy.domain.model.SyncProgress.LocalModified -> state.onKeepLocalModified?.invoke()
            is com.nendo.argosy.domain.model.SyncProgress.PostSessionConflict -> state.syncProgress.onSkipSync?.invoke()
            else -> {}
        }
        _dualSyncOverlay.value = null
        _dualSyncOverlayFocusIndex.value = 0
    }

    fun setDualSyncConflictFromSaveConflict(state: com.nendo.argosy.ui.screens.common.SyncOverlayState) {
        _dualSyncOverlayFocusIndex.value = 0
        _dualSyncOverlay.value = state
    }

    fun clearDualSyncConflictIfPostSession() {
        if (_dualSyncOverlay.value?.syncProgress is com.nendo.argosy.domain.model.SyncProgress.PostSessionConflict) {
            _dualSyncOverlay.value = null
            _dualSyncOverlayFocusIndex.value = 0
            resyncShowcaseFromHome()
        }
    }

    private val _dualSaveConflict = MutableStateFlow<com.nendo.argosy.ui.components.SaveConflictInfo?>(null)
    val dualSaveConflict: StateFlow<com.nendo.argosy.ui.components.SaveConflictInfo?> = _dualSaveConflict

    private val _dualSaveConflictFocusIndex = MutableStateFlow(0)
    val dualSaveConflictFocusIndex: StateFlow<Int> = _dualSaveConflictFocusIndex

    var onSaveConflictDismiss: (() -> Unit)? = null
    var onSaveConflictOverwrite: (() -> Unit)? = null

    fun setSaveConflict(info: com.nendo.argosy.ui.components.SaveConflictInfo?) {
        _dualSaveConflict.value = info
        _dualSaveConflictFocusIndex.value = 0
    }

    fun moveSaveConflictFocus(direction: Int) {
        _dualSaveConflictFocusIndex.value = (_dualSaveConflictFocusIndex.value + direction).coerceIn(0, 1)
    }

    fun confirmSaveConflict() {
        _dualSaveConflict.value ?: return
        val idx = _dualSaveConflictFocusIndex.value
        if (idx == 0) onSaveConflictDismiss?.invoke() else onSaveConflictOverwrite?.invoke()
        _dualSaveConflict.value = null
        _dualSaveConflictFocusIndex.value = 0
    }

    fun dismissSaveConflict() {
        onSaveConflictDismiss?.invoke()
        _dualSaveConflict.value = null
        _dualSaveConflictFocusIndex.value = 0
    }

    private val _pendingOverlayEvent = MutableStateFlow<String?>(null)
    val pendingOverlayEvent: StateFlow<String?> = _pendingOverlayEvent

    var onOverlayFocusChanged: ((Boolean) -> Unit)? = null
    var isOverlayFocused = false
        set(value) {
            field = value
            onOverlayFocusChanged?.invoke(value)
        }
    var swappedDualHomeViewModel: DualHomeViewModel? = null
        private set

    private val _swappedCurrentScreen = MutableStateFlow(
        com.nendo.argosy.hardware.CompanionScreen.HOME
    )
    val swappedCurrentScreen: StateFlow<com.nendo.argosy.hardware.CompanionScreen> =
        _swappedCurrentScreen

    private var _swappedGameDetailViewModel: com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel? = null
    val swappedGameDetailViewModel: com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel?
        get() = _swappedGameDetailViewModel

    private val _swappedIsGameActive = MutableStateFlow(false)
    val swappedIsGameActive: StateFlow<Boolean> = _swappedIsGameActive

    private val _swappedCompanionState = MutableStateFlow(
        com.nendo.argosy.hardware.CompanionInGameState()
    )
    val swappedCompanionState: StateFlow<com.nendo.argosy.hardware.CompanionInGameState> =
        _swappedCompanionState
    /**
     * Canonical in both companion modes; updateCompanionHasQuickSave
     * maintains it regardless of screen mode.
     */
    val companionHasQuickSave: Boolean
        get() = _swappedCompanionState.value.hasQuickSave

    var swappedSessionTimer: com.nendo.argosy.hardware.CompanionSessionTimer? = null
        private set

    private var companionWatchdogJob: Job? = null
    private var companionLaunchJob: Job? = null
    private var startupGuardJob: Job? = null
    private var companionPausedPending = false
    private var companionLaunchAttempts = 0

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (!displayAffinityHelper.isPhysicalDisplay(displayId)) return
            reprobeSecondaryDisplay()
            val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
            val newSwapped = resolver.isSwapped
            if (newSwapped != _isRolesSwapped.value) {
                _isRolesSwapped.value = newSwapped
                sessionStateStore.setRolesSwapped(newSwapped)
                onRoleSwapped?.invoke(newSwapped)
                companionHost?.onRoleSwapped(newSwapped)
            }
            _isDualScreenDevice.value = true
            CompanionGuardService.start(appContext)
            ensureCompanionLaunched()
        }

        override fun onDisplayRemoved(displayId: Int) {
            companionLaunchJob?.cancel()
            companionLaunchJob = null
            _isCompanionActive.value = false
            CompanionGuardService.stop(appContext)
            _isDualScreenDevice.value = displayAffinityHelper.hasSecondaryDisplay
            cleanupSwappedState()
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    private fun cleanupSwappedState() {
        if (!_isRolesSwapped.value) return

        val hadEmulatorOnSecondary = emulatorDisplayId != null &&
            emulatorDisplayId != android.view.Display.DEFAULT_DISPLAY

        emulatorDisplayId = null
        sessionStateStore.setDisplayRoleOverride("AUTO")
        scope.launch {
            preferencesRepository.setDisplayRoleOverride(DisplayRoleOverride.AUTO)
        }
        _isRolesSwapped.value = false
        sessionStateStore.setRolesSwapped(false)

        _swappedGameDetailViewModel = null
        swappedDualHomeViewModel = null
        _swappedCurrentScreen.value = com.nendo.argosy.hardware.CompanionScreen.HOME
        _swappedIsGameActive.value = false
        _swappedCompanionState.value = com.nendo.argosy.hardware.CompanionInGameState()
        swappedSessionTimer?.stop(appContext)
        swappedSessionTimer = null

        companionHost?.onRoleSwapped(false)

        if (hadEmulatorOnSecondary && sessionStateStore.hasActiveSession()) {
            Log.d(TAG, "HDMI disconnected with active session on secondary display - ending session")
            playSessionTracker.endSessionInBackground()
            broadcastSessionCleared()
        }

        Log.d(TAG, "HDMI disconnected: cleaned up swapped state")
    }

    /**
     * Whether the running session's emulator is still on a screen. Both the launcher and the
     * companion tear a session down when they come back to the front, and neither of them can tell
     * from that alone whether the game ended or merely stopped being the focused thing, so both ask
     * here. Answering false without evidence would end a live session and archive its save mid-play,
     * so an emulator that cannot be observed is treated as still running.
     */
    fun isEmulatorStillOnScreen(context: Context): Boolean {
        val emulatorPackage = sessionStateStore.getEmulatorPackage() ?: return false
        return permissionHelper.isPackageOnScreenOrRecent(context, emulatorPackage)
    }

    val homeAppsList: List<String>
        get() = sessionStateStore.getHomeApps()?.toList() ?: emptyList()

    fun clearPendingOverlay() {
        _pendingOverlayEvent.value = null
    }

    fun initSwappedViewModel() {
        swappedDualHomeViewModel = DualHomeViewModel(
            gameRepository = gameRepository,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            downloadQueueRepository = downloadQueueRepository,
            displayAffinityHelper = displayAffinityHelper,
            context = appContext,
            steamContentManager = steamContentManager,
            preferencesRepository = preferencesRepository,
            repairImageCacheUseCase = repairImageCacheUseCase,
            downloadFileStatusRepository = downloadFileStatusRepository,
            gradientExtractionDelegate = gradientExtractionDelegate,
            getPinnedCollectionsUseCase = getPinnedCollectionsUseCase,
            getGamesForPinnedCollectionUseCase = getGamesForPinnedCollectionUseCase,
            advanceCollectionFocusUseCase = advanceCollectionFocusUseCase,
            prepareCollectionQueueUseCase = prepareCollectionQueueUseCase,
            sessionStateStore = sessionStateStore,
            homeTileRepository = homeTileRepository,
            homeGridPageRepository = homeGridPageRepository,
            homeTilePromptQueue = homeTilePromptQueue,
            appsRepository = appsRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            pageChooserEntrySource = pageChooserEntrySource,
            ambientAudioManager = ambientAudioManager
        )
        swappedDualHomeViewModel?.observeHomeTiles()
        swappedDualHomeViewModel?.observeTilePrompts()
        restoreSwappedNavContext()
    }

    /**
     * The swapped role persists where the carousel is, so it has to read that back on the way in.
     * Writing without restoring would let a single move in swapped mode overwrite the saved
     * position with a section-zero context the user never chose.
     */
    private fun restoreSwappedNavContext() {
        swappedDualHomeViewModel?.restoreNavContextIfPresent(sessionStateStore.getCarouselNavContext())
    }

    // --- Public methods for companion -> DSM direction ---

    fun onCompanionResumed() {
        companionPausedPending = false
        companionLaunchAttempts = 0
        companionWatchdogJob?.cancel()
        _isCompanionActive.value = true
        if (!_isDualScreenDevice.value) _isDualScreenDevice.value = true
        resyncCompanionState()
    }

    fun onCompanionPaused() {
        _isCompanionActive.value = false
        companionPausedPending = false
        companionWatchdogJob?.cancel()
        companionWatchdogJob = scope.launch {
            delay(COMPANION_WATCHDOG_TIMEOUT_MS)
            val state = _dualGameDetailState.value
            if (state?.modalType != null && state.modalType != ActiveModal.NONE) {
                _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
                companionHost?.onModalResult(
                    dismissed = true, type = null, value = 0,
                    statusSelected = null, selectedIndex = -1,
                    collectionToggleId = -1, collectionCreateName = null
                )
                Log.w(TAG, "Companion watchdog: auto-dismissed stale modal")
            }
        }
    }

    fun onViewModeChanged(mode: String, appBarFocused: Boolean, drawerOpen: Boolean) {
        _dualViewMode.value = mode
        _dualAppBarFocused.value = appBarFocused
        _dualDrawerOpen.value = drawerOpen
    }

    /**
     * A collection is what the other screen has under its cursor. The flag travels with the state so
     * the showcase can be raised outside the collections browser too: a curated grid stays in the
     * carousel view mode while pointing at a collection, and the upper screen has no other way to
     * know the difference.
     */
    fun onCollectionFocused(state: DualCollectionShowcaseState) {
        _dualCollectionShowcase.value = state.copy(focused = true)
    }

    fun onGameSelected(showcase: DualHomeShowcaseState) {
        if (_dualCollectionShowcase.value.focused) {
            _dualCollectionShowcase.value = _dualCollectionShowcase.value.copy(focused = false)
        }
        val withWallpaper = showcase.copy(
            useGameBackground = _dualScreenShowcase.value.useGameBackground,
            customWallpaperPath = _dualScreenShowcase.value.customWallpaperPath
        )
        val gameId = withWallpaper.gameId
        if (gameId > 0) {
            scope.launch(Dispatchers.IO) {
                val validated = validateShowcaseImagePaths(withWallpaper)
                _dualScreenShowcase.value = validated
                val entity = gameDao.getById(gameId) ?: return@launch
                val rommId = entity.rommId
                val raId = entity.effectiveRaId
                if (rommId == null && raId == null && !RAConsoleIds.isSupported(entity.platformSlug)) return@launch
                fetchAchievementsUseCase(gameId = gameId, rommId = rommId, raId = raId)
            }
        } else {
            _dualScreenShowcase.value = withWallpaper
        }
    }

    private suspend fun validateShowcaseImagePaths(showcase: DualHomeShowcaseState): DualHomeShowcaseState {
        var result = showcase
        val cover = showcase.coverPath
        if (cover?.startsWith("/") == true && gameRepository.isPathGenuinelyAbsent(cover)) {
            gameDao.clearCoverPath(showcase.gameId)
            result = result.copy(coverPath = null)
        }
        val bg = showcase.backgroundPath
        if (bg?.startsWith("/") == true && gameRepository.isPathGenuinelyAbsent(bg)) {
            gameDao.clearBackgroundPath(showcase.gameId)
            result = result.copy(backgroundPath = null)
        }
        return result
    }

    internal fun handleGameDetailOpened(gameId: Long) {
        if (gameId == -1L) return
        gameThemeAudioCoordinator.enter(gameId)
        val current = _dualGameDetailState.value
        if (current != null && current.gameId == gameId && current.modalType != ActiveModal.NONE) {
            return
        }
        val showcase = _dualScreenShowcase.value
        if (showcase.gameId == gameId) {
            _dualGameDetailState.value = DualGameDetailUpperState(
                gameId = gameId,
                title = showcase.title,
                coverPath = showcase.coverPath,
                backgroundPath = showcase.backgroundPath,
                boxBackPath = showcase.boxBackPath,
                boxSpinePath = showcase.boxSpinePath,
                platformName = showcase.platformName,
                developer = showcase.developer,
                releaseYear = showcase.releaseYear,
                description = showcase.description,
                playTimeMinutes = showcase.playTimeMinutes,
                lastPlayedAt = showcase.lastPlayedAt,
                status = showcase.status,
                rating = showcase.userRating.takeIf { it > 0 },
                userDifficulty = showcase.userDifficulty,
                communityRating = showcase.communityRating,
                titleId = showcase.titleId
            )
        } else {
            _dualGameDetailState.value = DualGameDetailUpperState(gameId = gameId)
        }
        broadcastUnifiedSaves(gameId)
        broadcastUnifiedStates(gameId)
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (showcase.gameId != gameId) {
                val platform = platformRepository.getById(game.platformId)
                _dualGameDetailState.update { state ->
                    state?.copy(
                        title = game.title,
                        coverPath = game.coverPath,
                        backgroundPath = game.backgroundPath,
                        boxBackPath = game.boxBackPath?.takeIf { it.startsWith("/") },
                        boxSpinePath = game.boxSpinePath?.takeIf { it.startsWith("/") },
                        platformName = platform?.name ?: "",
                        developer = game.developer,
                        releaseYear = game.releaseYear,
                        description = game.description,
                        playTimeMinutes = game.playTimeMinutes,
                        lastPlayedAt = game.lastPlayed?.toEpochMilli() ?: 0,
                        status = game.status,
                        rating = game.userRating.takeIf { it > 0 },
                        userDifficulty = game.userDifficulty,
                        communityRating = game.rating,
                        titleId = game.displayTitleId
                    )
                }
            }
            val remoteUrls = game.screenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val cachedPaths = game.cachedScreenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val screenshots = remoteUrls.mapIndexed { i, url ->
                cachedPaths.getOrNull(i)
                    ?.takeIf { it.startsWith("/") }
                    ?: url
            }
            _dualGameDetailState.update { state ->
                state?.copy(screenshots = screenshots)
            }
        }
    }

    fun onGameDetailClosed() {
        Log.d("UpdatesDLC", "onGameDetailClosed, currentModal=${_dualGameDetailState.value?.modalType}")
        _dualGameDetailState.value?.gameId?.let { gameThemeAudioCoordinator.exit(it) }
        _dualGameDetailState.value = null
        resyncShowcaseFromHome()
    }

    private fun resyncShowcaseFromHome() {
        val game = swappedDualHomeViewModel?.uiState?.value?.selectedGame
        if (game != null) {
            onGameSelected(game.toShowcaseState())
        }
    }

    fun onScreenshotSelected(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(viewerScreenshotIndex = index.takeIf { it >= 0 })
        }
    }

    fun onScreenshotCleared() {
        _dualGameDetailState.update { state ->
            state?.copy(viewerScreenshotIndex = null)
        }
    }

    fun openModal(type: ActiveModal, value: Int = 0, statusSelected: String? = null, statusCurrent: String? = null) {
        when (type) {
            ActiveModal.EMULATOR, ActiveModal.CORE, ActiveModal.COLLECTION,
            ActiveModal.SAVE_PATH, ActiveModal.DISPLAY_TARGET,
            ActiveModal.SAVE_NAME,
            ActiveModal.DISC_PICKER, ActiveModal.VARIANT_PICKER,
            ActiveModal.STEAM_INSTALL -> return
            else -> handleDualModalOpen(type, value, statusSelected, statusCurrent)
        }
        refocusMain()
    }

    fun openEmulatorModal(names: List<String>, versions: List<String>, current: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.EMULATOR,
                emulatorNames = names,
                emulatorVersions = versions,
                emulatorFocusIndex = 0,
                emulatorCurrentName = current
            )
        }
        refocusMain()
    }

    fun openCollectionModal(ids: List<Long>, names: List<String>, checked: List<Boolean>) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.COLLECTION,
                collectionItems = ids.mapIndexed { i, id ->
                    DualCollectionItem(id, names.getOrElse(i) { "" }, checked.getOrElse(i) { false })
                },
                collectionFocusIndex = 0
            )
        }
        refocusMain()
    }

    fun openSaveNameModal(actionType: String, cacheId: Long?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.SAVE_NAME,
                saveNamePromptAction = actionType,
                saveNameCacheId = cacheId,
                saveNameText = ""
            )
        }
        refocusMain()
    }

    fun openDiscModal(discs: List<DiscOption>) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.DISC_PICKER,
                discPickerOptions = discs,
                discPickerFocusIndex = 0
            )
        }
        refocusMain()
    }

    fun openSteamInstallModal(names: List<String>, packages: List<String>) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.STEAM_INSTALL,
                steamInstallOptionNames = names,
                steamInstallOptionPackages = packages,
                steamInstallFocusIndex = 0
            )
        }
        refocusMain()
    }

    fun openSteamChooserForHome(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (game.steamAppId == null) return@launch
            val options = com.nendo.argosy.data.launcher.SteamLaunchers.getMarkOptions(appContext)
            _dualGameDetailState.value = DualGameDetailUpperState(
                gameId = gameId,
                title = game.title,
                coverPath = game.coverPath,
                modalType = ActiveModal.STEAM_INSTALL,
                steamInstallOptionNames = options.map { it.displayName },
                steamInstallOptionPackages = options.map { it.launcherPackage },
                steamInstallFocusIndex = 0,
                isHomeChooser = true
            )
            refocusMain()
        }
    }

    fun onModalClose() {
        companionHost?.onModalResult(
            dismissed = true,
            type = _dualGameDetailState.value?.modalType?.name,
            value = 0, statusSelected = null, selectedIndex = -1,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update { state -> state?.copy(modalType = ActiveModal.NONE) }
    }

    fun onModalConfirmResult(modal: ActiveModal, value: Int, statusValue: String?) {
        when (modal) {
            ActiveModal.EMULATOR -> {
                confirmDualEmulatorSelection()
                return
            }
            ActiveModal.CORE -> {
                confirmDualCoreSelection()
                return
            }
            ActiveModal.SAVE_PATH -> {
                confirmDualSavePathSelection()
                return
            }
            ActiveModal.DISPLAY_TARGET -> {
                confirmDualDisplayTargetSelection()
                return
            }
            ActiveModal.MEMORY_CARD -> {
                confirmDualMemoryCardSelection()
                return
            }
            ActiveModal.VARIANT_PICKER -> {
                confirmDualVariantSelection()
                return
            }
            ActiveModal.COLLECTION -> {
                toggleDualCollectionAtFocus()
                return
            }
            ActiveModal.STEAM_INSTALL -> {
                confirmDualSteamInstallSelection()
                return
            }
            else -> {}
        }
        companionHost?.onModalResult(
            dismissed = false, type = modal.name, value = value,
            statusSelected = statusValue, selectedIndex = -1,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update { s ->
            when (modal) {
                ActiveModal.RATING -> s?.copy(modalType = ActiveModal.NONE, rating = value.takeIf { it > 0 })
                ActiveModal.STATUS -> s?.copy(modalType = ActiveModal.NONE, status = statusValue)
                else -> s?.copy(modalType = ActiveModal.NONE)
            }
        }
        if (!_isRolesSwapped.value) {
            companionHost?.refocusSelf()
        }
    }

    fun handleDirectAction(type: String, gameId: Long, channelName: String? = null, timestamp: Long? = null) {
        if (gameId < 0) return
        when (type) {
            "PLAY" -> handleDualPlay(gameId, channelName)
            "DOWNLOAD" -> handleDualDownload(gameId)
            "REFRESH_METADATA" -> handleDualRefresh(gameId)
            "REFRESH_TITLE_ID" -> handleTitleIdRecheck(gameId)
            "RESYNC_PLATFORM" -> handleDualResyncPlatform(gameId)
            "DELETE" -> handleDualDelete(gameId)
            "HIDE" -> handleDualHide(gameId)
            "UNHIDE" -> handleDualUnhide(gameId)
            "SAVE_SWITCH_CHANNEL" -> handleSaveSwitchChannel(gameId, channelName)
            "SAVE_SET_RESTORE_POINT" -> handleSaveSetRestorePoint(gameId, channelName, timestamp ?: 0L)
            "DOWNLOAD_UPDATE_FILE" -> {
                val fileId = channelName?.toLongOrNull()
                if (fileId != null) {
                    scope.launch(Dispatchers.IO) {
                        val gameFile = gameFileDao.getById(fileId) ?: return@launch
                        val game = gameDao.getById(gameId) ?: return@launch
                        val rommFileId = gameFile.rommFileId ?: return@launch
                        downloadManager.enqueueGameFileDownload(
                            gameId = gameId, gameFileId = fileId, rommFileId = rommFileId,
                            fileName = gameFile.fileName, category = gameFile.category,
                            gameTitle = game.title, platformSlug = game.platformSlug,
                            coverPath = game.coverPath, expectedSizeBytes = gameFile.fileSize,
                            gameFolderName = game.rommFileName
                        )
                    }
                }
            }
            "STATE_RESTORE" -> handleStateRestore(gameId, channelName)
            "STATE_DELETE" -> handleStateDelete(gameId, channelName)
            "STATE_COPY" -> handleStateCopy(gameId, channelName)
            "SELECT_DISC" -> handleSelectDisc(gameId)
            "PLAY_DISC" -> handleDualPlayDisc(gameId, channelName)
            "FILES" -> promptDualManageFilePicker(gameId)
        }
    }

    fun handleInlineUpdate(field: String, intValue: Int = 0, stringValue: String? = null) {
        when (field) {
            "rating" -> _dualGameDetailState.update { s -> s?.copy(rating = intValue.takeIf { it > 0 }) }
            "difficulty" -> _dualGameDetailState.update { s -> s?.copy(userDifficulty = intValue) }
            "status" -> _dualGameDetailState.update { s -> s?.copy(status = stringValue) }
            "modal_rating" -> _dualGameDetailState.update { s -> s?.copy(modalRatingValue = intValue) }
            "modal_status" -> _dualGameDetailState.update { s -> s?.copy(modalStatusSelected = stringValue) }
            "emulator_focus" -> _dualGameDetailState.update { s -> s?.copy(emulatorFocusIndex = intValue) }
            "emulator_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        emulatorCurrentName = if (intValue == 0) null else s.emulatorNames.getOrNull(intValue - 1)
                    )
                }
            }
            "core_focus" -> _dualGameDetailState.update { s -> s?.copy(coreFocusIndex = intValue) }
            "core_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        coreCurrentName = if (intValue == 0) null else s.coreNames.getOrNull(intValue - 1)
                    )
                }
            }
            "save_path_focus" -> _dualGameDetailState.update { s -> s?.copy(savePathFocusIndex = intValue) }
            "save_path_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        savePathOverride = if (intValue == 0) null else s.savePathOverride
                    )
                }
                _swappedGameDetailViewModel?.confirmSavePathByIndex(intValue)
            }
            "display_target_focus" -> _dualGameDetailState.update { s -> s?.copy(displayTargetFocusIndex = intValue) }
            "display_target_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        displayTargetCurrentName = if (intValue == 0) null
                        else s.displayTargetNames.getOrNull(intValue - 1)
                    )
                }
                _swappedGameDetailViewModel?.confirmDisplayTargetByIndex(intValue)
            }
            "memory_card_focus" -> _dualGameDetailState.update { s -> s?.copy(memoryCardFocusIndex = intValue) }
            "memory_card_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        memoryCardCurrentName = if (intValue == 0) null
                        else s.memoryCardNames.getOrNull(intValue - 1)
                    )
                }
                _swappedGameDetailViewModel?.confirmMemoryCardByIndex(intValue)
            }
            "variant_focus" -> _dualGameDetailState.update { s -> s?.copy(variantFocusIndex = intValue) }
            "variant_confirm" -> {
                _dualGameDetailState.update { s ->
                    s?.copy(
                        modalType = ActiveModal.NONE,
                        variantCurrentName = if (intValue == 0) null else s.variantNames.getOrNull(intValue - 1)
                    )
                }
            }
            "collection_focus" -> _dualGameDetailState.update { s -> s?.copy(collectionFocusIndex = intValue) }
            "collection_toggle" -> {
                val collectionId = intValue.toLong()
                _dualGameDetailState.update { s ->
                    s?.copy(collectionItems = s.collectionItems.map {
                        if (it.id == collectionId) it.copy(isInCollection = !it.isInCollection) else it
                    })
                }
            }
            "collection_create" -> _dualGameDetailState.update { s -> s?.copy(showCreateDialog = true) }
            "disc_focus" -> _dualGameDetailState.update { s -> s?.copy(discPickerFocusIndex = intValue) }
            "steam_install_focus" -> _dualGameDetailState.update { s -> s?.copy(steamInstallFocusIndex = intValue) }
            "steam_install_confirm" -> {
                setDualSteamInstallFocus(intValue)
                confirmDualSteamInstallSelection()
            }
        }
    }

    fun onOpenOverlayFromCompanion(eventName: String) {
        isOverlayFocused = true
        _pendingOverlayEvent.value = eventName ?: OVERLAY_MENU
        refocusMain()
    }

    fun onRefocusUpper() {
        refocusMain()
    }

    fun onCompanionHomeAppsChanged(apps: Set<String>) {
        scope.launch {
            preferencesRepository.setSecondaryHomeApps(apps)
        }
    }

    fun onSessionChanged(gameId: Long, isHardcore: Boolean = false, channelName: String? = null) {
        if (gameId > 0) {
            _swappedIsGameActive.value = true
            _swappedGameDetailViewModel = null
            _swappedCurrentScreen.value = com.nendo.argosy.hardware.CompanionScreen.HOME
            swappedSessionTimer?.stop(appContext)
            swappedSessionTimer = com.nendo.argosy.hardware.CompanionSessionTimer().also { it.start(appContext) }
            scope.launch(Dispatchers.IO) {
                val game = gameDao.getById(gameId) ?: return@launch
                val platform = platformRepository.getById(game.platformId)
                _swappedCompanionState.update { liveState ->
                    com.nendo.argosy.hardware.CompanionInGameState(
                        gameId = gameId,
                        title = game.title,
                        coverPath = game.coverPath,
                        platformName = platform?.getDisplayName() ?: game.platformSlug,
                        developer = game.developer,
                        releaseYear = game.releaseYear,
                        playTimeMinutes = game.playTimeMinutes,
                        playCount = game.playCount,
                        achievementCount = game.achievementCount,
                        earnedAchievementCount = game.earnedAchievementCount,
                        sessionStartTimeMillis = sessionStateStore.getSessionStartTimeMillis(),
                        channelName = channelName,
                        isHardcore = isHardcore,
                        isLoaded = true
                    ).withLiveQuickActionState(
                        quickActionsAvailable = sessionQuickActions != null,
                        hasQuickSave = liveState.hasQuickSave
                    )
                }
            }
            companionHost?.onSessionStarted(gameId, isHardcore, channelName)
        } else {
            if (!_swappedIsGameActive.value) return
            emulatorDisplayId = null
            _swappedIsGameActive.value = false
            _swappedCompanionState.value = com.nendo.argosy.hardware.CompanionInGameState()
            sessionStateStore.clearSession()
            swappedSessionTimer?.stop(appContext)
            swappedSessionTimer = null
            _dualGameDetailState.value = null
            val savedDetailGameId = sessionStateStore.getDetailGameId()
            if (savedDetailGameId > 0) selectGameSwapped(savedDetailGameId)
            else resyncShowcaseFromHome()
            val savedSwapped = preGameRolesSwapped
            if (savedSwapped != null) {
                _isRolesSwapped.value = savedSwapped
                preGameRolesSwapped = null
            }
            Handler(Looper.getMainLooper()).post {
                if (savedSwapped != null) onRoleSwapped?.invoke(savedSwapped)
                companionHost?.onSessionEnded()
                companionHost?.onRoleSwapped(_isRolesSwapped.value)
            }
        }
    }

    fun onDownloadCompleted(gameId: Long) {
        if (gameId > 0 && _dualScreenShowcase.value.gameId == gameId) {
            _dualScreenShowcase.update { it.copy(isDownloaded = true) }
        }
    }

    fun onRoleSwapReceived() {
        val resolver = com.nendo.argosy.util.DisplayRoleResolver(
            displayAffinityHelper, sessionStateStore
        )
        _isRolesSwapped.value = resolver.isSwapped
        onRoleSwapped?.invoke(_isRolesSwapped.value)
    }

    // --- Modal Operations ---

    private fun handleDualModalOpen(
        type: ActiveModal,
        value: Int,
        statusSelected: String?,
        statusCurrent: String?
    ) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = type,
                modalRatingValue = value,
                modalStatusSelected = statusSelected
                    ?: statusCurrent
                    ?: CompletionStatus.entries.first().apiValue,
                modalStatusCurrent = statusCurrent
            )
        }
    }

    fun adjustDualModalRating(delta: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalRatingValue = (state.modalRatingValue + delta)
                    .coerceIn(0, 10)
            )
        }
    }

    fun setDualModalRating(value: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(modalRatingValue = value.coerceIn(0, 10))
        }
    }

    fun moveDualModalStatus(delta: Int) {
        _dualGameDetailState.update { state ->
            if (state == null) return@update null
            val entries = CompletionStatus.entries
            val current = CompletionStatus.fromApiValue(
                state.modalStatusSelected
            ) ?: entries.first()
            val next = entries[
                (current.ordinal + delta).mod(entries.size)
            ]
            state.copy(modalStatusSelected = next.apiValue)
        }
    }

    fun setDualModalStatus(value: String) {
        _dualGameDetailState.update { state ->
            state?.copy(modalStatusSelected = value)
        }
    }

    fun confirmDualModal() {
        val state = _dualGameDetailState.value ?: return
        val type = state.modalType
        Log.d("UpdatesDLC", "confirmDualModal called, type=$type", Exception("stacktrace"))
        if (type == ActiveModal.NONE) return

        when (type) {
            ActiveModal.EMULATOR -> {
                confirmDualEmulatorSelection()
                return
            }
            ActiveModal.CORE -> {
                confirmDualCoreSelection()
                return
            }
            ActiveModal.SAVE_PATH -> {
                confirmDualSavePathSelection()
                return
            }
            ActiveModal.DISPLAY_TARGET -> {
                confirmDualDisplayTargetSelection()
                return
            }
            ActiveModal.MEMORY_CARD -> {
                confirmDualMemoryCardSelection()
                return
            }
            ActiveModal.VARIANT_PICKER -> {
                confirmDualVariantSelection()
                return
            }
            ActiveModal.COLLECTION -> {
                toggleDualCollectionAtFocus()
                return
            }
            else -> {}
        }

        companionHost?.onModalResult(
            dismissed = false, type = type.name,
            value = when (type) { ActiveModal.RATING, ActiveModal.DIFFICULTY -> state.modalRatingValue; else -> 0 },
            statusSelected = when (type) { ActiveModal.STATUS -> state.modalStatusSelected; else -> null },
            selectedIndex = -1, collectionToggleId = -1, collectionCreateName = null
        )

        _dualGameDetailState.update { s ->
            when (type) {
                ActiveModal.RATING -> s?.copy(
                    modalType = ActiveModal.NONE,
                    rating = state.modalRatingValue.takeIf { it > 0 }
                )
                ActiveModal.STATUS -> s?.copy(
                    modalType = ActiveModal.NONE,
                    status = state.modalStatusSelected
                )
                else -> s?.copy(modalType = ActiveModal.NONE)
            }
        }
    }

    fun dismissDualModal() {
        Log.d("UpdatesDLC", "dismissDualModal called, current modal=${_dualGameDetailState.value?.modalType}", Exception("stacktrace"))
        if (_dualGameDetailState.value?.isHomeChooser == true) {
            _dualGameDetailState.value = null
            companionHost?.refocusSelf()
            return
        }
        companionHost?.onModalResult(
            dismissed = true, type = _dualGameDetailState.value?.modalType?.name,
            value = 0, statusSelected = null, selectedIndex = -1,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update { state ->
            state?.copy(modalType = ActiveModal.NONE)
        }
    }

    fun setDualEmulatorFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(emulatorFocusIndex = index)
        }
    }

    fun setDualCollectionFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(collectionFocusIndex = index)
        }
    }

    fun moveDualEmulatorFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.emulatorNames?.size ?: 0
            state?.copy(
                emulatorFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.emulatorFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun confirmDualEmulatorSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.emulatorFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.EMULATOR.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                emulatorCurrentName = if (index == 0) null
                else state.emulatorNames.getOrNull(index - 1)
            )
        }
    }

    fun openCoreModal(names: List<String>, current: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.CORE,
                coreNames = names,
                coreFocusIndex = 0,
                coreCurrentName = current
            )
        }
        refocusMain()
    }

    fun moveDualCoreFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.coreNames?.size ?: 0
            state?.copy(
                coreFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.coreFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun confirmDualCoreSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.coreFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.CORE.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                coreCurrentName = if (index == 0) null
                else state.coreNames.getOrNull(index - 1)
            )
        }
    }

    fun setDualCoreFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(coreFocusIndex = index)
        }
    }

    fun openSavePathModal(overridePath: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.SAVE_PATH,
                savePathOverride = overridePath,
                savePathFocusIndex = 0
            )
        }
        refocusMain()
    }

    fun moveDualSavePathFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = if (state?.savePathOverride != null) 1 else 0
            state?.copy(
                savePathFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.savePathFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun setDualSavePathFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(savePathFocusIndex = index)
        }
    }

    fun confirmDualSavePathSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.savePathFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.SAVE_PATH.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                savePathOverride = if (index == 0) null else it.savePathOverride
            )
        }
    }

    fun openDisplayTargetModal(names: List<String>, current: String?, inherited: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.DISPLAY_TARGET,
                displayTargetNames = names,
                displayTargetFocusIndex = 0,
                displayTargetCurrentName = current,
                displayTargetInheritedName = inherited
            )
        }
        refocusMain()
    }

    fun moveDualDisplayTargetFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.displayTargetNames?.size ?: 0
            state?.copy(
                displayTargetFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.displayTargetFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun setDualDisplayTargetFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(displayTargetFocusIndex = index)
        }
    }

    fun confirmDualDisplayTargetSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.displayTargetFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.DISPLAY_TARGET.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                displayTargetCurrentName = if (index == 0) null
                else state.displayTargetNames.getOrNull(index - 1)
            )
        }
    }

    fun openMemoryCardModal(names: List<String>, current: String?, inherited: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.MEMORY_CARD,
                memoryCardNames = names,
                memoryCardFocusIndex = 0,
                memoryCardCurrentName = current,
                memoryCardInheritedName = inherited
            )
        }
        refocusMain()
    }

    fun moveDualMemoryCardFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.memoryCardNames?.size ?: 0
            state?.copy(
                memoryCardFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.memoryCardFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun setDualMemoryCardFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(memoryCardFocusIndex = index)
        }
    }

    fun confirmDualMemoryCardSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.memoryCardFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.MEMORY_CARD.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                memoryCardCurrentName = if (index == 0) null
                else state.memoryCardNames.getOrNull(index - 1)
            )
        }
    }

    fun openVariantModal(names: List<String>, current: String?) {
        _dualGameDetailState.update { state ->
            state?.copy(
                modalType = ActiveModal.VARIANT_PICKER,
                variantNames = names,
                variantFocusIndex = 0,
                variantCurrentName = current
            )
        }
        refocusMain()
    }

    fun moveDualVariantFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.variantNames?.size ?: 0
            state?.copy(
                variantFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.variantFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun confirmDualVariantSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.variantFocusIndex
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.VARIANT_PICKER.name,
            value = 0, statusSelected = null, selectedIndex = index,
            collectionToggleId = -1, collectionCreateName = null
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                variantCurrentName = if (index == 0) null else state.variantNames.getOrNull(index - 1)
            )
        }
    }

    fun setDualVariantFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(variantFocusIndex = index)
        }
    }

    fun moveDualCollectionFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.collectionItems?.size ?: 0
            state?.copy(
                collectionFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.collectionFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun toggleDualCollectionAtFocus() {
        val state = _dualGameDetailState.value ?: return
        if (state.collectionFocusIndex == state.collectionItems.size) {
            showDualCollectionCreateDialog()
            return
        }
        val item = state.collectionItems.getOrNull(
            state.collectionFocusIndex
        ) ?: return
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.COLLECTION.name,
            value = 0, statusSelected = null, selectedIndex = -1,
            collectionToggleId = item.id, collectionCreateName = null
        )
        _dualGameDetailState.update { s ->
            s?.copy(
                collectionItems = s.collectionItems.map {
                    if (it.id == item.id)
                        it.copy(isInCollection = !it.isInCollection)
                    else it
                }
            )
        }
    }

    fun showDualCollectionCreateDialog() {
        _dualGameDetailState.update { it?.copy(showCreateDialog = true) }
    }

    fun dismissDualCollectionCreateDialog() {
        _dualGameDetailState.update { it?.copy(showCreateDialog = false) }
    }

    fun confirmDualCollectionCreate(name: String) {
        _dualGameDetailState.update { it?.copy(showCreateDialog = false) }
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.COLLECTION.name,
            value = 0, statusSelected = null, selectedIndex = -1,
            collectionToggleId = -1, collectionCreateName = name
        )
    }

    fun updateDualSaveNameText(text: String) {
        _dualGameDetailState.update { it?.copy(saveNameText = text) }
    }

    private fun isReservedSaveSlotName(name: String): Boolean =
        com.nendo.argosy.data.repository.SaveSyncApiClient.equalsNormalized(
            name, com.nendo.argosy.data.repository.SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        ) || com.nendo.argosy.data.repository.SaveSyncApiClient.equalsNormalized(
            name, com.nendo.argosy.data.repository.SaveSyncApiClient.DEFAULT_SAVE_NAME
        )

    fun confirmDualSaveName() {
        val state = _dualGameDetailState.value ?: return
        val name = state.saveNameText.trim()
        if (name.isBlank()) return
        if (isReservedSaveSlotName(name)) {
            notificationManager.showError("'$name' is a reserved name")
            return
        }
        val gameId = state.gameId

        when (state.saveNamePromptAction) {
            "CREATE_SLOT" -> handleCreateSlot(gameId, name)
            "LOCK_AS_SLOT" -> handleLockAsSlot(
                gameId, state.saveNameCacheId, name
            )
        }

        _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        companionHost?.onModalResult(
            dismissed = false, type = ActiveModal.SAVE_NAME.name,
            value = 0, statusSelected = null, selectedIndex = -1,
            collectionToggleId = -1, collectionCreateName = null
        )
    }

    fun selectDualDisc(index: Int) {
        val state = _dualGameDetailState.value ?: return
        val disc = state.discPickerOptions.getOrNull(index) ?: return
        _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        handleDualPlayDisc(state.gameId, disc.filePath)
    }

    fun setDualSteamInstallFocus(index: Int) {
        _dualGameDetailState.update { state ->
            state?.copy(steamInstallFocusIndex = index)
        }
    }

    fun moveDualSteamInstallFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.steamInstallOptionNames?.size ?: 0
            state?.copy(
                steamInstallFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    state.steamInstallFocusIndex, delta, max, menuWrapMode
                )
            )
        }
    }

    fun confirmDualSteamInstallSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.steamInstallFocusIndex
        val gameId = state.gameId
        val homeChooser = state.isHomeChooser
        if (homeChooser) {
            _dualGameDetailState.value = null
        } else {
            companionHost?.onModalResult(
                dismissed = false, type = ActiveModal.STEAM_INSTALL.name,
                value = 0, statusSelected = null, selectedIndex = index,
                collectionToggleId = -1, collectionCreateName = null
            )
            _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        }
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val steamAppId = game.steamAppId ?: return@launch
            if (index == 0) {
                if (game.isExternallyManaged) gameDao.setSteamLauncher(gameId, null)
                steamContentManager.queueDownloadOptimistic(steamAppId, game.title, game.coverPath)
            } else {
                val launcherPackage = state.steamInstallOptionPackages.getOrNull(index - 1)
                    ?: return@launch
                gameDao.setSteamLauncher(gameId, launcherPackage)
            }
            if (homeChooser) {
                companionHost?.refocusSelf()
            } else {
                _swappedGameDetailViewModel?.loadGame(gameId)
            }
            companionHost?.onDirectActionResult("STEAM_INSTALL_DONE", gameId)
        }
    }

    // --- Game Actions ---

    private var syncConflictMirrorJob: kotlinx.coroutines.Job? = null
    private var discPickerObserverJob: kotlinx.coroutines.Job? = null

    private fun handleDualPlay(gameId: Long, channelName: String? = null) {
        Log.d(TAG, "handleDualPlay: gameId=$gameId")

        syncConflictMirrorJob?.cancel()
        syncConflictMirrorJob = scope.launch {
            gameLaunchDelegate.syncOverlayState.collect { state ->
                Log.d(TAG, "[DualSync] syncOverlayState changed: progress=${state?.syncProgress?.javaClass?.simpleName}, gameTitle=${state?.gameTitle}")
                val isConflict = state?.syncProgress is com.nendo.argosy.domain.model.SyncProgress.HardcoreConflict ||
                    state?.syncProgress is com.nendo.argosy.domain.model.SyncProgress.LocalModified
                if (isConflict) {
                    _dualSyncOverlayFocusIndex.value = 0
                }
                _dualSyncOverlay.value = state
            }
        }

        discPickerObserverJob?.cancel()
        discPickerObserverJob = scope.launch {
            gameLaunchDelegate.discPickerState.collect { pickerState ->
                if (pickerState != null) {
                    openDiscModal(pickerState.discs)
                }
            }
        }

        scope.launch {
            val platformId = _swappedGameDetailViewModel?.uiState?.value?.platformId
                ?: swappedDualHomeViewModel?.uiState?.value?.selectedGame?.platformId
                ?: gameDao.getById(gameId)?.platformId
            val effectiveSwapped = if (platformId != null) {
                resolveEmulatorDisplaySwapped(gameId, platformId)
            } else {
                _isRolesSwapped.value
            }

            gameLaunchDelegate.launchGame(
                scope = scope,
                gameId = gameId,
                channelName = channelName,
                allowVariantPrompt = false,
                onLaunch = { intent ->
                    syncConflictMirrorJob?.cancel()
                    discPickerObserverJob?.cancel()
                    _dualSyncOverlay.value = null
                    emulatorDisplayId = displayAffinityHelper.getEmulatorDisplayId(effectiveSwapped)
                    isLaunchingGame = true
                    launchGuardJob?.cancel()
                    launchGuardJob = scope.launch { delay(10_000); isLaunchingGame = false }
                    Log.d(TAG, "Game launching on display $emulatorDisplayId (swapped=$effectiveSwapped)")
                    val options = displayAffinityHelper.getActivityOptions(
                        forEmulator = true,
                        rolesSwapped = effectiveSwapped
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (options != null) activityContext.startActivity(intent, options)
                    else activityContext.startActivity(intent)

                    if (effectiveSwapped != _isRolesSwapped.value) {
                        preGameRolesSwapped = _isRolesSwapped.value
                        _isRolesSwapped.value = effectiveSwapped
                        onRoleSwapped?.invoke(effectiveSwapped)
                        companionHost?.onRoleSwapped(effectiveSwapped)
                    }
                }
            )
        }
    }

    private suspend fun resolveEmulatorDisplaySwapped(gameId: Long, platformId: Long): Boolean {
        if (!displayAffinityHelper.hasSecondaryDisplay) return _isRolesSwapped.value
        val target = EmulatorDisplayTarget.fromString(
            emulatorConfigDao.getDisplayTargetForGame(gameId)
                ?: emulatorConfigDao.getDisplayTargetForPlatform(platformId)
        )
        return when (target) {
            EmulatorDisplayTarget.HERO -> _isRolesSwapped.value
            EmulatorDisplayTarget.LIBRARY -> !_isRolesSwapped.value
            EmulatorDisplayTarget.TOP -> false
            EmulatorDisplayTarget.BOTTOM -> true
        }
    }

    private fun handleSelectDisc(gameId: Long) {
        handleDualPlay(gameId, null)
    }

    private fun handleDualPlayDisc(gameId: Long, discPath: String?) {
        if (discPath == null) return
        discPickerObserverJob?.cancel()
        _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        gameLaunchDelegate.selectDisc(scope, discPath)
    }

    private fun handleDualDownload(gameId: Long) {
        val detailOpen = _dualGameDetailState.value?.gameId == gameId
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (game.steamAppId != null) {
                if (isSteamGameInstalled(game)) {
                    handleDualPlay(gameId)
                    return@launch
                }
                steamContentManager.queueDownloadOptimistic(game.steamAppId, game.title, game.coverPath)
            } else if (detailOpen) {
                promptDualFilePicker(gameId)
            } else {
                gameActionsDelegate.queueDownload(gameId)
            }
        }
    }

    fun promptDualFilePicker(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val setup = filePickerFlow.buildRows(gameId)
            if (setup == null) {
                gameActionsDelegate.queueDownload(gameId)
                return@launch
            }
            _dualGameDetailState.update { state ->
                state?.takeIf { it.gameId == gameId }?.copy(
                    modalType = ActiveModal.FILE_PICKER,
                    filePickerRows = setup.rows,
                    filePickerSelected = setup.preselectedFileIds,
                    filePickerSelectedVersions = setup.preselectedVersionIds,
                    filePickerFocusIndex = 0,
                    filePickerCollapsed = emptySet(),
                    filePickerManageMode = false
                ) ?: state
            }
            refocusMain()
        }
    }

    fun promptDualManageFilePicker(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val setup = filePickerFlow.buildManageRows(gameId) ?: return@launch
            _dualGameDetailState.update { state ->
                state?.takeIf { it.gameId == gameId }?.copy(
                    modalType = ActiveModal.FILE_PICKER,
                    filePickerRows = setup.rows,
                    filePickerSelected = setup.preselectedFileIds,
                    filePickerSelectedVersions = setup.preselectedVersionIds,
                    filePickerFocusIndex = 0,
                    filePickerCollapsed = emptySet(),
                    filePickerManageMode = true
                ) ?: state
            }
            refocusMain()
        }
    }

    fun moveDualFilePickerFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            if (state == null) return@update null
            val maxIndex = state.visibleFilePickerRows.size + 1
            state.copy(filePickerFocusIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(state.filePickerFocusIndex, delta, maxIndex, menuWrapMode))
        }
    }

    fun moveDualFilePickerButtonFocus(delta: Int): Boolean {
        val state = _dualGameDetailState.value ?: return false
        val buttonStart = state.visibleFilePickerRows.size
        if (state.filePickerFocusIndex < buttonStart) return false
        _dualGameDetailState.update {
            it?.copy(filePickerFocusIndex = (it.filePickerFocusIndex + delta).coerceIn(buttonStart, buttonStart + 1))
        }
        return true
    }

    fun activateDualFilePickerFocused() {
        val state = _dualGameDetailState.value ?: return
        val rowCount = state.visibleFilePickerRows.size
        when {
            state.filePickerFocusIndex < rowCount -> toggleDualFilePickerRow()
            state.filePickerFocusIndex == rowCount -> dismissDualModal()
            else -> confirmDualFilePicker()
        }
    }

    fun jumpDualFilePickerGroup(direction: Int) {
        _dualGameDetailState.update { state ->
            if (state == null) return@update null
            val headers = state.visibleFilePickerRows.withIndex().filter { it.value.isHeader }.map { it.index }
            if (headers.isEmpty()) return@update state
            val target = if (direction > 0) {
                headers.firstOrNull { it > state.filePickerFocusIndex }
            } else {
                headers.lastOrNull { it < state.filePickerFocusIndex }
            } ?: return@update state
            state.copy(filePickerFocusIndex = target)
        }
    }

    fun toggleDualFilePickerGroupCollapse(groupKey: String) {
        _dualGameDetailState.update { state ->
            if (state == null) return@update null
            val oldVisible = state.visibleFilePickerRows
            val focusedRow = oldVisible.getOrNull(state.filePickerFocusIndex)
            val newCollapsed = if (groupKey in state.filePickerCollapsed) {
                state.filePickerCollapsed - groupKey
            } else {
                state.filePickerCollapsed + groupKey
            }
            val newVisible = state.filePickerRows.filter { it.isHeader || it.groupKey !in newCollapsed }
            val newIndex = when {
                state.filePickerFocusIndex >= oldVisible.size ->
                    newVisible.size + (state.filePickerFocusIndex - oldVisible.size)
                focusedRow != null ->
                    newVisible.indexOf(focusedRow).takeIf { it >= 0 }
                        ?: newVisible.indexOfFirst { it.isHeader && it.groupKey == focusedRow.groupKey }.coerceAtLeast(0)
                else -> 0
            }
            state.copy(filePickerCollapsed = newCollapsed, filePickerFocusIndex = newIndex.coerceAtLeast(0))
        }
    }

    fun setDualFocusedFilePickerGroupCollapsed(collapse: Boolean) {
        val state = _dualGameDetailState.value ?: return
        val row = state.visibleFilePickerRows.getOrNull(state.filePickerFocusIndex) ?: return
        if (!row.isHeader) return
        val isCollapsed = row.groupKey in state.filePickerCollapsed
        if (collapse == isCollapsed) return
        toggleDualFilePickerGroupCollapse(row.groupKey)
    }

    fun toggleDualFilePickerRow(row: com.nendo.argosy.data.model.FilePickerRow? = null) {
        _dualGameDetailState.update { state ->
            if (state == null) return@update null
            val target = row ?: state.visibleFilePickerRows.getOrNull(state.filePickerFocusIndex)
                ?: return@update state
            var selected = state.filePickerSelected
            var versions = state.filePickerSelectedVersions
            if (target.isHeader) {
                val members = state.filePickerRows.filter { !it.isHeader && it.groupKey == target.groupKey && !it.isLocked }
                val fileIds = members.mapNotNull { it.rommFileId }
                val versionIds = members.mapNotNull { it.versionRommId }
                val allSelected = fileIds.all { it in selected } && versionIds.all { it in versions }
                if (allSelected) {
                    selected = selected - fileIds.toSet()
                    versions = versions - versionIds.toSet()
                    if (versionIds.isNotEmpty() && versions.isEmpty()) versions = setOf(versionIds.first())
                } else {
                    selected = selected + fileIds
                    versions = versions + versionIds
                }
            } else if (target.versionRommId != null) {
                versions = if (target.versionRommId in versions) {
                    (versions - target.versionRommId).ifEmpty { versions }
                } else {
                    versions + target.versionRommId
                }
            } else if (target.rommFileId != null && !target.isLocked) {
                selected = if (target.rommFileId in selected) selected - target.rommFileId
                else selected + target.rommFileId
            }
            state.copy(filePickerSelected = selected, filePickerSelectedVersions = versions)
        }
    }

    fun confirmDualFilePicker() {
        val state = _dualGameDetailState.value ?: return
        if (state.modalType != ActiveModal.FILE_PICKER) return
        val gameId = state.gameId
        val files = state.filePickerSelected
        val versions = state.filePickerSelectedVersions
        val manageMode = state.filePickerManageMode
        val rows = state.filePickerRows
        _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        companionHost?.refocusSelf()
        scope.launch(Dispatchers.IO) {
            if (manageMode) {
                val (added, removed) = filePickerFlow.applyManagedSelection(gameId, rows, files)
                val parts = buildList {
                    if (added > 0) add("$added queued")
                    if (removed > 0) add("$removed removed")
                }
                if (parts.isNotEmpty()) notificationManager.showSuccess(parts.joinToString(", "))
            } else {
                val (queued, errors) = filePickerFlow.downloadSelection(gameId, files, versions)
                errors.forEach { notificationManager.showError(it) }
                if (queued > 1) notificationManager.showSuccess("Queued " + queued + " downloads")
            }
        }
    }

    private suspend fun isSteamGameInstalled(
        game: com.nendo.argosy.data.local.entity.GameEntity
    ): Boolean {
        val launcher = game.steamLauncher
            ?.let { com.nendo.argosy.data.launcher.SteamLaunchers.getByPackage(it) }
            ?: com.nendo.argosy.data.launcher.SteamLaunchers.getPreferred(appContext)
        if (launcher?.isInstalled(appContext) != true) return false
        if (game.isExternallyManaged) return true
        val localPath = game.localPath ?: return false
        return downloadFileStatusRepository.pathExists(localPath) &&
            downloadFileStatusRepository.isDownloadComplete(localPath)
    }

    /**
     * Resyncs the platform a game belongs to. Named by game rather than by platform because that is
     * what the companion has in hand; the platform is looked up here where the row already lives.
     */
    private fun handleDualResyncPlatform(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformRepository.getById(game.platformId) ?: return@launch
            syncPlatformUseCase(platform.id, platform.name)
        }
    }

    private fun handleDualRefresh(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val isAndroid = game.source == GameSource.ANDROID_APP
            if (isAndroid) gameActionsDelegate.refreshAndroidGameData(gameId)
            else gameActionsDelegate.refreshGameData(gameId)
            companionHost?.onDirectActionResult("REFRESH_DONE", gameId)
            val updated = gameDao.getById(gameId) ?: return@launch
            _dualGameDetailState.update { s ->
                s?.copy(
                    description = updated.description,
                    developer = updated.developer,
                    releaseYear = updated.releaseYear,
                    title = updated.title
                )
            }
        }
    }

    private fun handleTitleIdRecheck(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val result = titleIdDownloadObserver.recheckTitleId(gameId)
            if (result is com.nendo.argosy.data.emulator.TitleIdRecheck.Found) {
                _dualGameDetailState.update { s ->
                    if (s?.gameId == gameId) s.copy(titleId = result.titleId) else s
                }
                companionHost?.onDirectActionResult("REFRESH_DONE", gameId)
                _swappedGameDetailViewModel?.loadGame(gameId)
            }
            notificationManager.reportTitleIdRecheck(result)
        }
    }

    private fun handleDualDelete(gameId: Long) {
        companionHost?.onDirectActionResult("DELETE_START", gameId)
        _swappedGameDetailViewModel?.onDeleteStarted()
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            when {
                game.source == GameSource.ANDROID_APP -> {
                    val uninstall = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${game.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityContext.startActivity(uninstall)
                }
                game.isExternallyManaged -> gameDao.setSteamLauncher(gameId, null)
                else -> gameActionsDelegate.deleteLocalFile(gameId)
            }
            companionHost?.onDirectActionResult("DELETE_DONE", gameId)
            _swappedGameDetailViewModel?.loadGame(gameId)
        }
    }

    private fun handleDualHide(gameId: Long) {
        companionHost?.onDirectActionResult("DELETE_START", gameId)
        _swappedGameDetailViewModel?.onDeleteStarted()
        scope.launch(Dispatchers.IO) {
            gameActionsDelegate.deleteLocalFile(gameId)
            gameActionsDelegate.hideGame(gameId)
            _dualGameDetailState.value = null
            companionHost?.onDirectActionResult("HIDE_DONE", -1)
        }
    }

    private fun handleDualUnhide(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            gameActionsDelegate.unhideGame(gameId)
            companionHost?.onDirectActionResult("UNHIDE_DONE", gameId)
        }
    }

    // --- Save Operations ---

    private fun handleSaveSwitchChannel(gameId: Long, channelName: String?) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, game.platformId, game.platformSlug
            )

            activateSaveChannelUseCase(gameId, channelName)

            if (emulatorId != null) {
                val entries = getUnifiedSavesUseCase(gameId, expandHistory = true)
                val latestForChannel = entries
                    .filter { it.channelName == channelName }
                    .maxByOrNull { it.timestamp }

                if (latestForChannel != null) {
                    val result = restoreCachedSaveUseCase(
                        latestForChannel, gameId, emulatorId, false
                    )
                    when (result) {
                        is RestoreCachedSaveUseCase.Result.Restored,
                        is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                            activeSaveRepository.setActiveSaveApplied(gameId, true)
                        }
                        is RestoreCachedSaveUseCase.Result.Error -> {
                            Log.w(TAG, "Channel switch restore failed: ${result.message}")
                        }
                    }
                } else {
                    restoreCachedSaveUseCase.clearActiveSave(gameId, emulatorId)
                }
            }

            broadcastSaveActionResult("SAVE_SWITCH_DONE", gameId)
            broadcastUnifiedSaves(gameId)
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleSaveSetRestorePoint(
        gameId: Long,
        channelName: String?,
        timestamp: Long
    ) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, game.platformId, game.platformSlug
            )

            activeSaveRepository.activateTimestamp(gameId, timestamp)

            if (emulatorId != null) {
                val entries = getUnifiedSavesUseCase(gameId, expandHistory = true)
                val targetEntry = entries.find {
                    it.channelName == channelName &&
                        it.timestamp.toEpochMilli() == timestamp
                }

                if (targetEntry != null) {
                    restoreSaveChannelPointUseCase(
                        gameId = gameId,
                        channelName = channelName,
                        isLatest = targetEntry.isLatest
                    )
                    val result = restoreCachedSaveUseCase(
                        targetEntry, gameId, emulatorId, false
                    )
                    when (result) {
                        is RestoreCachedSaveUseCase.Result.Restored,
                        is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                            activeSaveRepository.setActiveSaveApplied(gameId, true)
                        }
                        is RestoreCachedSaveUseCase.Result.Error -> {
                            Log.w(TAG, "Restore point apply failed: ${result.message}")
                        }
                    }
                }
            }

            broadcastSaveActionResult("SAVE_RESTORE_DONE", gameId)
            broadcastUnifiedSaves(gameId)
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleCreateSlot(gameId: Long, name: String) {
        scope.launch(Dispatchers.IO) {
            createSaveChannelUseCase(gameId, name)

            broadcastSaveActionResult("SAVE_CREATE_DONE", gameId)
            broadcastUnifiedSaves(gameId)
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleLockAsSlot(gameId: Long, cacheId: Long?, name: String) {
        if (cacheId == null) return
        scope.launch(Dispatchers.IO) {
            val sourceChannel = getUnifiedSavesUseCase(gameId, expandHistory = true)
                .firstOrNull { it.localCacheId == cacheId }
                ?.channelName

            copySaveChannelUseCase(
                gameId = gameId,
                sourceChannel = sourceChannel,
                targetChannel = name,
                localCacheId = cacheId,
                serverSaveId = null,
                emulatorId = null
            )

            broadcastSaveActionResult("SAVE_LOCK_DONE", gameId)
            broadcastUnifiedSaves(gameId)
            broadcastUnifiedStates(gameId)
        }
    }

    private fun broadcastSaveActionResult(type: String, gameId: Long) {
        companionHost?.onDirectActionResult(type, gameId)
    }

    private fun broadcastUnifiedSaves(gameId: Long) {
        if (!sessionStateStore.isSaveSyncEnabled()) return
        scope.launch(Dispatchers.Default) {
            try {
                val activeSave = activeSaveRepository.getActiveRow(gameId)
                val localEntries = getUnifiedSavesUseCase.localOnly(gameId)
                val localData = localEntries.map { it.toSaveEntryData() }
                deliverSaves(
                    gameId,
                    localData,
                    activeSave?.channelName,
                    activeSave?.cachedAt?.toEpochMilli(),
                    syncing = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load local saves", e)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val activeSave = activeSaveRepository.getActiveRow(gameId)
                val fullEntries = getUnifiedSavesUseCase(gameId, expandHistory = true)
                val fullData = fullEntries.map { it.toSaveEntryData() }
                deliverSaves(
                    gameId,
                    fullData,
                    activeSave?.channelName,
                    activeSave?.cachedAt?.toEpochMilli(),
                    syncing = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync remote saves", e)
                deliverSyncingDone(gameId)
            }
        }
    }

    private fun deliverSaves(
        gameId: Long,
        entryData: List<com.nendo.argosy.ui.dualscreen.gamedetail.SaveEntryData>,
        activeChannel: String?,
        activeTimestamp: Long?,
        syncing: Boolean
    ) {
        _swappedGameDetailViewModel?.let { vm ->
            if (vm.uiState.value.gameId == gameId) {
                vm.loadUnifiedSaves(entryData, activeChannel, activeTimestamp)
                vm.setSyncing(syncing)
            }
        }

        val json = entryData.toJsonString()
        companionHost?.onSaveDataReceived(json, activeChannel, activeTimestamp, syncing)
    }

    /**
     * Sends the game's states to whichever screen is showing its detail.
     *
     * The states tab reads what is delivered here; without it the tab draws its slots over an
     * empty list however many states are cached or synced.
     */
    private fun stateEntriesFor(gameId: Long): List<UnifiedStateEntry> =
        lastStateEntries?.takeIf { it.first == gameId }?.second ?: emptyList()

    private fun stateSlotLabel(slot: Int): String =
        if (slot < 0) "auto state" else "state slot $slot"

    /**
     * A state the companion restores goes through the same use case the handheld uses, so a
     * version mismatch is refused here too. The companion has nowhere to ask the user to override,
     * so a mismatch is reported rather than forced: restoring a state a different core wrote is
     * how a save file gets corrupted.
     */
    private fun handleStateRestore(gameId: Long, slotArg: String?) {
        val slot = slotArg?.toIntOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val entry = stateEntriesFor(gameId).firstOrNull { it.slotNumber == slot } ?: return@launch
            val cacheId = entry.localCacheId ?: return@launch
            val game = gameDao.getById(gameId) ?: return@launch
            val romPath = game.localPath
            if (romPath == null) {
                notificationManager.showError("Game has no local path")
                return@launch
            }
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, game.platformId, game.platformSlug
            ) ?: return@launch

            when (restoreStateUseCase(
                cacheId = cacheId,
                emulatorId = emulatorId,
                platformId = game.platformSlug,
                romPath = romPath,
                currentCoreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)
            )) {
                is com.nendo.argosy.domain.usecase.state.RestoreStateResult.Success ->
                    notificationManager.showSuccess("Restored ${stateSlotLabel(slot)}")
                is com.nendo.argosy.domain.usecase.state.RestoreStateResult.VersionMismatch ->
                    notificationManager.showError(
                        "${stateSlotLabel(slot)} was made by a different core version"
                    )
                else ->
                    notificationManager.showError("Could not restore ${stateSlotLabel(slot)}")
            }
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleStateDelete(gameId: Long, slotArg: String?) {
        val slot = slotArg?.toIntOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val entry = stateEntriesFor(gameId).firstOrNull { it.slotNumber == slot } ?: return@launch
            stateCacheManager.purgeState(gameId, entry.localCacheId, entry.serverStateId)
            notificationManager.showSuccess("Deleted ${stateSlotLabel(slot)}")
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleStateCopy(gameId: Long, slotsArg: String?) {
        val parts = slotsArg?.split(':') ?: return
        val sourceSlot = parts.getOrNull(0)?.toIntOrNull() ?: return
        val targetSlot = parts.getOrNull(1)?.toIntOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val cacheId = stateEntriesFor(gameId)
                .firstOrNull { it.slotNumber == sourceSlot }?.localCacheId ?: return@launch
            val copied = stateCacheManager.copyStateToSlot(cacheId, targetSlot)
            if (copied) {
                notificationManager.showSuccess(
                    "Copied ${stateSlotLabel(sourceSlot)} to ${stateSlotLabel(targetSlot)}"
                )
                broadcastUnifiedStates(gameId)
            } else {
                notificationManager.showError("Could not copy ${stateSlotLabel(sourceSlot)}")
            }
        }
    }

    private fun broadcastUnifiedStates(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val channelName = activeSaveRepository.getActiveRow(gameId)?.channelName
                val entries = getUnifiedStatesUseCase(gameId, channelName = channelName)
                Log.i(
                    TAG,
                    "[StateSync] states for gameId=$gameId channel=$channelName | entries=${entries.size}"
                )
                lastStateEntries = gameId to entries
                withContext(Dispatchers.Main) {
                    _swappedGameDetailViewModel?.let { vm ->
                        if (vm.uiState.value.gameId == gameId) vm.loadStateEntries(entries)
                    }
                    companionHost?.onStateEntriesReceived(entries)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load states for gameId=$gameId", e)
            }
        }
    }

    private fun deliverSyncingDone(gameId: Long) {
        _swappedGameDetailViewModel?.let { vm ->
            if (vm.uiState.value.gameId == gameId) vm.setSyncing(false)
        }
        companionHost?.onSavesSyncDone()
    }

    // --- Companion Sync ---

    fun resyncCompanionState() {
        broadcastForegroundState(true)
        if (_dualGameDetailState.value?.isHomeChooser == true) {
            _dualGameDetailState.value = null
            companionHost?.onOverlayClosed()
            return
        }
        val detailState = _dualGameDetailState.value
        if (detailState?.modalType != null &&
            detailState.modalType != ActiveModal.NONE
        ) {
            _dualGameDetailState.update {
                it?.copy(modalType = ActiveModal.NONE)
            }
            companionHost?.onModalResult(
                dismissed = true, type = null, value = 0,
                statusSelected = null, selectedIndex = -1,
                collectionToggleId = -1, collectionCreateName = null
            )
        }
        companionHost?.onOverlayClosed()
        if (detailState != null && detailState.gameId > 0) {
            companionHost?.onGameDetailOpened(detailState.gameId)
            broadcastUnifiedSaves(detailState.gameId)
        }
    }

    fun broadcastForegroundState(isForeground: Boolean) {
        sessionStateStore.setArgosyForeground(isForeground)
        companionHost?.onForegroundChanged(isForeground)
        if (isForeground) {
            if (!_isCompanionActive.value && displayAffinityHelper.hasSecondaryDisplay) {
                ensureCompanionLaunched()
            }
            val isWizard = sessionStateStore.isWizardActive()
            if (isWizard) companionHost?.onWizardStateChanged(true)
            scope.launch {
                val prefs = preferencesRepository.preferences.first()
                updateHomeApps(prefs.secondaryHomeApps)
            }
        }
    }

    fun broadcastWizardState(isActive: Boolean) {
        sessionStateStore.setWizardActive(isActive)
        if (!isActive) sessionStateStore.setFirstRunComplete(true)
        companionHost?.onWizardStateChanged(isActive)
    }

    private var lastSwapTimeMs = 0L

    fun swapRoles() {
        val now = System.currentTimeMillis()
        if (now - lastSwapTimeMs < SWAP_DEBOUNCE_MS) return
        lastSwapTimeMs = now

        if (sessionStateStore.hasActiveSession()) return

        val current = sessionStateStore.getDisplayRoleOverride()
        val newOverride = when (current) {
            "SWAPPED" -> "STANDARD"
            "STANDARD" -> "SWAPPED"
            else -> {
                if (_isRolesSwapped.value) "STANDARD" else "SWAPPED"
            }
        }
        sessionStateStore.setDisplayRoleOverride(newOverride)
        scope.launch {
            preferencesRepository.setDisplayRoleOverride(DisplayRoleOverride.fromString(newOverride))
        }
        val newSwapped = newOverride == "SWAPPED" ||
            (newOverride == "AUTO" && displayAffinityHelper.secondaryDisplayType == SecondaryDisplayType.EXTERNAL)
        if (!newSwapped) {
            commitRoleSwap(newSwapped)
            return
        }

        if (swappedDualHomeViewModel == null) initSwappedViewModel()
        restoreSwappedNavContext()
        val incoming = swappedDualHomeViewModel
        if (incoming == null) {
            commitRoleSwap(newSwapped)
            return
        }
        scope.launch {
            withTimeoutOrNull(SWAP_PREPARE_TIMEOUT_MS) {
                incoming.restorePending.first { !it }
            }
            commitRoleSwap(newSwapped)
        }
    }

    /**
     * Hands the roles over, once the screen about to be revealed is already sitting where it should.
     *
     * The flip itself is what makes the incoming surface visible, so it happens last. A restore that
     * has not landed leaves that surface showing the position it was left on, which reads as the
     * swap arriving in the wrong place and then correcting itself.
     */
    private fun commitRoleSwap(newSwapped: Boolean) {
        _isRolesSwapped.value = newSwapped
        sessionStateStore.setRolesSwapped(newSwapped)
        mediaPlayerDisplayId = null
        onRoleSwapped?.invoke(newSwapped)
        companionHost?.onRoleSwapped(newSwapped)
        if (!newSwapped) companionHost?.refocusSelf()
    }

    fun selectGameSwapped(gameId: Long) {
        val vm = com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel(
            gameRepository = gameRepository,
            activeSaveRepository = activeSaveRepository,
            prefetchGameSaveDataUseCase = prefetchGameSaveDataUseCase,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            emulatorConfigDao = emulatorConfigDao,
            downloadQueueRepository = downloadQueueRepository,
            steamRepository = steamRepository,
            configureEmulatorUseCase = configureEmulatorUseCase,
            builtinCoreResolver = builtinCoreResolver,
            saveHandlerRegistry = saveHandlerRegistry,
            steamContentManager = steamContentManager,
            displayAffinityHelper = displayAffinityHelper,
            downloadFileStatusRepository = downloadFileStatusRepository,
            sessionStateStore = sessionStateStore,
            preferencesRepository = preferencesRepository,
            resolveGameEmulatorContext = resolveGameEmulatorContext,
            context = appContext
        )
        vm.loadGame(gameId)
        _swappedGameDetailViewModel = vm
        _swappedCurrentScreen.value = com.nendo.argosy.hardware.CompanionScreen.GAME_DETAIL
        sessionStateStore.setCompanionScreen("GAME_DETAIL", gameId)
        handleGameDetailOpened(gameId)
    }

    fun returnToHomeSwapped() {
        _swappedGameDetailViewModel = null
        _swappedCurrentScreen.value = com.nendo.argosy.hardware.CompanionScreen.HOME
        sessionStateStore.setCompanionScreen("HOME")
        onGameDetailClosed()
        swappedDualHomeViewModel?.refresh()
    }

    fun broadcastOpenOverlay(eventName: String) {
        companionHost?.onOverlayRequested(eventName)
    }

    // WIP: Focus Recovery for External Displays
    // -----------------------------------------
    // FocusDirector (setLaunchDisplayId) is blocked by SafeActivityOptions.checkPermissions
    // on external HDMI displays (Odin 3). Only SECONDARY_HOME activities get display launch
    // permission on external screens.
    //
    // Current approach: FocusAccessibilityService uses dispatchGesture() with
    // GestureDescription.Builder.setDisplayId() to inject a touch on the emulator display,
    // which should update Android's FocusedDisplayId (tracks "most recent touch display").
    //
    // Status: Service is registered in manifest + config XML but UNTESTED.
    // User must enable it in Settings > Accessibility > Argosy Launcher.
    //
    // Fallback: FocusDirector still works on BUILT_IN secondary displays (Thor).
    //
    // Triggers: MainActivity.onWindowFocusChanged(false), MainActivity.onResume
    // Guard: isLaunchingGame flag prevents firing during game launch (cleared on focus loss, 10s ceiling)
    //
    // Next steps:
    //   1. Test accessibility tap on Odin 3 external display
    //   2. If dispatchGesture works, add auto-prompt for accessibility permission
    //   3. Test on Thor to verify FocusDirector still works for built-in displays
    //   4. Investigate game session being cleared on resume in swapped mode
    fun restoreEmulatorFocus() {
        val displayId = emulatorDisplayId ?: return
        if (!sessionStateStore.hasActiveSession()) return
        if (isLaunchingGame) return
        scope.launch {
            delay(200)
            if (isLaunchingGame) return@launch
            val a11y = FocusAccessibilityService.instance
            if (a11y != null) {
                Log.d(TAG, "Restoring emulator focus via accessibility tap on display $displayId")
                a11y.tapOnDisplay(displayId)
            } else {
                Log.d(TAG, "Restoring emulator focus via FocusDirector on display $displayId")
                try {
                    FocusDirectorActivity.launchOnDisplay(appContext, displayId)
                } catch (e: SecurityException) {
                    Log.w(TAG, "FocusDirector blocked on display $displayId (device restriction)")
                }
            }
        }
    }

    fun setEmulatorDisplay(displayId: Int?) {
        emulatorDisplayId = displayId
    }

    /** Fallback for launch paths that never assigned a display; the launch path's write wins. */
    fun assignEmulatorDisplayForSessionStart() {
        if (emulatorDisplayId != null) return
        emulatorDisplayId = displayAffinityHelper.getEmulatorDisplayId(_isRolesSwapped.value)
    }

    fun startStartupGuard() {
        startupGuardJob?.cancel()
        startupGuardJob = scope.launch {
            while (isActive) {
                delay(1500)
                if (!_isCompanionActive.value &&
                    displayAffinityHelper.hasSecondaryDisplay &&
                    !sessionStateStore.hasActiveSession()
                ) {
                    ensureCompanionLaunched()
                }
            }
        }
    }

    fun stopStartupGuard() {
        startupGuardJob?.cancel()
        startupGuardJob = null
    }

    fun ensureCompanionLaunched(allowDuringSession: Boolean = false) {
        if (!displayAffinityHelper.hasSecondaryDisplay) return
        if (sessionStateStore.isDualScreenEnabled()) setSecondaryHomeComponentEnabled(true)
        if (_isCompanionActive.value) return
        if (!allowDuringSession && sessionStateStore.hasActiveSession()) return
        if (sessionStateStore.isForeignAppOnSecondary()) return

        CompanionGuardService.start(appContext)
        companionLaunchJob?.cancel()
        companionLaunchJob = scope.launch {
            delay(COMPANION_LAUNCH_WAIT_MS)
            if (_isCompanionActive.value) return@launch
            if (!allowDuringSession && sessionStateStore.hasActiveSession()) return@launch
            launchCompanionOnSecondaryDisplay()
        }
    }

    private fun launchCompanionOnSecondaryDisplay() {
        val options = displayAffinityHelper.getCompanionLaunchOptions() ?: return
        val intent = Intent(activityContext, SecondaryHomeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        Log.d(TAG, "Launching companion on secondary display")
        activityContext.startActivity(intent, options)
        companionLaunchAttempts++
        scope.launch {
            delay(300)
            refocusMain()
        }
        scope.launch {
            delay(COMPANION_LAUNCH_VERIFY_MS)
            if (_isCompanionActive.value) return@launch
            if (companionLaunchAttempts < MAX_COMPANION_LAUNCH_ATTEMPTS) return@launch
            if (sessionStateStore.isForeignAppOnSecondary()) return@launch
            fallbackToSingleScreen(persistent = false)
        }
    }

    companion object {
        const val ACTION_WIZARD_STATE = "com.nendo.argosy.WIZARD_STATE"
        const val EXTRA_WIZARD_ACTIVE = "wizard_active"
        const val OVERLAY_MENU = "com.nendo.argosy.OVERLAY_MENU"
        const val OVERLAY_QUICK_MENU = "com.nendo.argosy.OVERLAY_QUICK_MENU"
        const val OVERLAY_QUICK_SETTINGS = "com.nendo.argosy.OVERLAY_QUICK_SETTINGS"
        private const val COMPANION_WATCHDOG_TIMEOUT_MS = 5000L
        private const val COMPANION_LAUNCH_WAIT_MS = 500L
        private const val COMPANION_LAUNCH_VERIFY_MS = 8000L
        private const val MAX_COMPANION_LAUNCH_ATTEMPTS = 3
        private const val SWAP_DEBOUNCE_MS = 500L
        private const val DEFAULT_SCREEN_OFF_TIMEOUT_MS = 60_000L
        private const val MIN_SCREEN_OFF_TIMEOUT_MS = 15_000L
        private const val MAX_SCREEN_OFF_TIMEOUT_MS = 30 * 60_000L

        /**
         * How long a swap waits for the incoming screen to settle before revealing it anyway.
         *
         * A restore can defer indefinitely while its section list is still loading, so the wait is
         * bounded: a swap that feels late is a nuisance, a swap that never happens is a dead button.
         */
        private const val SWAP_PREPARE_TIMEOUT_MS = 400L
    }

    fun updateHomeApps(homeApps: Set<String>) {
        sessionStateStore.setHomeApps(homeApps)
        companionHost?.onHomeAppsChanged(homeApps.toList())
    }

    fun broadcastSessionCleared() {
        companionHost?.onSessionEnded()
    }

    // --- Registration ---

    fun registerReceivers() {
        displayAffinityHelper.registerDisplayListener(displayListener)
    }

    fun unregisterReceivers() {
        companionLaunchJob?.cancel()
        companionLaunchJob = null
        displayAffinityHelper.unregisterDisplayListener(displayListener)
    }

    fun refocusSession() {
        if (!sessionStateStore.hasActiveSession()) return
        sessionRefocus?.invoke()
    }

    private fun refocusMain() {
        if (emulatorDisplayId == android.view.Display.DEFAULT_DISPLAY &&
            sessionStateStore.hasActiveSession()
        ) return
        activityContext.startActivity(
            Intent(activityContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        )
    }
}
