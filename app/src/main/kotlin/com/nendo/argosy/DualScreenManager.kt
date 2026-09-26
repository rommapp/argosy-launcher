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
import com.nendo.argosy.util.Logger
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.reportTitleIdRecheck
import com.nendo.argosy.ui.common.toNotificationText
import com.nendo.argosy.data.social.ReviewWriteEvent
import com.nendo.argosy.ui.input.InputDedupBuffer
import com.nendo.argosy.ui.input.InputSignature
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.core.game.toAchievementUi
import com.nendo.argosy.core.notification.NotificationText
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    internal val socialRepository: com.nendo.argosy.data.social.SocialRepository,
    internal val downloadQueueDao: DownloadQueueDao,
    internal val downloadQueueRepository: com.nendo.argosy.data.repository.DownloadQueueRepository,
    internal val gameFileDao: GameFileDao,
    internal val downloadManager: DownloadManager,
    private val gameActionsDelegate: GameActionsDelegate,
    private val platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue,
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
    private val renameSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.RenameSaveChannelUseCase,
    private val deleteSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.DeleteSaveChannelUseCase,
    private val restoreStateUseCase:
        com.nendo.argosy.domain.usecase.state.RestoreStateUseCase,
    private val emulatorResolver: EmulatorResolver,
    private val coreVersionExtractor: com.nendo.argosy.data.emulator.CoreVersionExtractor,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    internal val raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository,
    internal val raTileContentRepository: com.nendo.argosy.data.repository.RaTileContentRepository,
    private val achievementUpdateBus: com.nendo.argosy.core.event.AchievementUpdateBus,
    internal val displayAffinityHelper: DisplayAffinityHelper,
    internal val sessionStateStore: SessionStateStore,
    internal val preferencesRepository: UserPreferencesRepository,
    internal val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    internal val romMRepository: com.nendo.argosy.data.remote.romm.RomMRepository,
    private val gameDocumentLoader: com.nendo.argosy.data.repository.GameDocumentLoader,
    private val documentHighlightStore: com.nendo.argosy.data.repository.DocumentHighlightStore,
    internal val resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase,
    internal val hapticManager: com.nendo.argosy.ui.input.HapticFeedbackManager,
    internal val soundManager: com.nendo.argosy.ui.input.SoundFeedbackManager,
    internal val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    internal val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    internal val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    internal val appsRepository: com.nendo.argosy.data.repository.AppsRepository,
    private val appShortcutActions: com.nendo.argosy.ui.screens.common.AppShortcutActions,
    val notificationManager: com.nendo.argosy.core.notification.NotificationManager,
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
    internal val getRelatedMediaUseCase:
        com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase,
    internal val resolveMediaPlayTargetUseCase:
        com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase,
    private val mediaPlaybackTracker: com.nendo.argosy.data.media.MediaPlaybackTracker,
    internal val mediaAvailabilityVerifier:
        com.nendo.argosy.data.media.MediaAvailabilityVerifier? = null,
    internal val mediaDownloadDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaDownloadDelegate? = null,
    internal val mediaSeriesDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate? = null,
    internal val mediaSiblingsDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaSiblingsDelegate? = null,
    initialRolesSwapped: Boolean = false
) {

    private val appContext: Context = context.applicationContext

    private val activityIndependentScope =
        com.nendo.argosy.util.SafeCoroutineScope(Dispatchers.Main, "DualScreenState")

    private var preGameRolesSwapped: Boolean? = null
    private var activityContext: Context = context
    private var lastStateEntries: Pair<Long, List<UnifiedStateEntry>>? = null

    private val _isRolesSwapped = MutableStateFlow(initialRolesSwapped)
    val isRolesSwapped: StateFlow<Boolean> = _isRolesSwapped

    fun setRolesSwapped(value: Boolean) {
        _isRolesSwapped.value = value
    }

    private val _hasPresentationScreen = MutableStateFlow(false)
    val hasPresentationScreen: StateFlow<Boolean> = _hasPresentationScreen

    private val _controlHints =
        MutableStateFlow<List<com.nendo.argosy.ui.dualscreen.CompanionHint>>(emptyList())

    /**
     * What the buttons do on the screen being driven, for the screen describing it. Published by
     * whichever surface holds the guide bar, so a screen with no presentation slot of its own
     * still has its hints rendered.
     */
    val controlHints: StateFlow<List<com.nendo.argosy.ui.dualscreen.CompanionHint>> = _controlHints

    fun publishControlHints(hints: List<com.nendo.argosy.ui.dualscreen.CompanionHint>) {
        if (_controlHints.value != hints) _controlHints.value = hints
    }

    private val _mutedNotificationKeys = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Notification keys the launcher's current screen already reports, so the presentation screen
     * stays quiet about them too.
     */
    val mutedNotificationKeys: StateFlow<Set<String>> = _mutedNotificationKeys

    fun setMutedNotificationKeys(keys: Set<String>) {
        _mutedNotificationKeys.value = keys
    }


    private fun applyScreenLayout(
        primaryDisplayId: Int,
        presentationDisplayId: Int?,
        appTargetDisplayId: Int?,
        hasPresentation: Boolean
    ) {
        displayAffinityHelper.appTargetDisplayId = appTargetDisplayId
        displayAffinityHelper.roleDisplayIds = presentationDisplayId?.let {
            primaryDisplayId to it
        }
        _hasPresentationScreen.value = hasPresentation
        setPrimaryDisplayId(primaryDisplayId)
    }

    private val _unconfiguredScreenSet = MutableStateFlow<String?>(null)
    val unconfiguredScreenSet: StateFlow<String?> = _unconfiguredScreenSet

    fun clearUnconfiguredScreenSet() {
        _unconfiguredScreenSet.value = null
    }

    private val _screenNumbers = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /**
     * Display id to the number the screen map draws for it, while the map is open. Every rendered
     * surface reads its own display's entry and labels itself, so a number never travels through
     * the presentation slot and never competes with the content a screen is already showing.
     * Empty means the map is closed.
     */
    val screenNumbers: StateFlow<Map<Int, Int>> = _screenNumbers

    private val displayBadges by lazy {
        com.nendo.argosy.hardware.DisplayBadgeOverlay(appContext)
    }

    fun showScreenNumbers(
        size: com.nendo.argosy.hardware.DisplayBadgeSize =
            com.nendo.argosy.hardware.DisplayBadgeSize.LARGE
    ) {
        val numbers = com.nendo.argosy.util.ScreenCatalog(appContext)
            .attachedScreens()
            .associate { it.displayId to it.number }
        _screenNumbers.value = numbers
        displayBadges.show(numbers, size)
    }

    fun hideScreenNumbers() {
        _screenNumbers.value = emptyMap()
        displayBadges.hide()
    }

    private val _focusPickerOpen = MutableStateFlow(false)
    val focusPickerOpen: StateFlow<Boolean> = _focusPickerOpen

    private val _focusPickerIndex = MutableStateFlow(0)
    val focusPickerIndex: StateFlow<Int> = _focusPickerIndex

    fun openFocusPicker() {
        if (_focusPickerOpen.value) return
        _focusPickerIndex.value = 0
        _focusPickerOpen.value = true
        showScreenNumbers(com.nendo.argosy.hardware.DisplayBadgeSize.SMALL)
    }

    fun closeFocusPicker() {
        if (!_focusPickerOpen.value) return
        _focusPickerOpen.value = false
        hideScreenNumbers()
    }

    fun moveFocusPicker(delta: Int) {
        val count = focusableDisplays().size
        if (count == 0) return
        _focusPickerIndex.value = (_focusPickerIndex.value + delta).mod(count)
    }

    fun confirmFocusPicker() {
        val displays = focusableDisplays()
        val target = displays.getOrNull(_focusPickerIndex.value) ?: return
        focusDisplay(target.displayId)
        closeFocusPicker()
    }

    class ResolvedScreenLayout(
        val attached: List<com.nendo.argosy.util.AttachedScreen>,
        val setKey: String,
        val stored: com.nendo.argosy.domain.model.ScreenLayouts,
        val known: com.nendo.argosy.domain.model.ScreenLayout?,
        val layout: com.nendo.argosy.domain.model.ScreenLayout
    )

    suspend fun resolveScreenLayout(): ResolvedScreenLayout? {
        val attached = com.nendo.argosy.util.ScreenCatalog(appContext).attachedScreens()
        if (attached.isEmpty()) return null
        val keys = attached.map { it.key }
        val setKey = com.nendo.argosy.domain.model.ScreenLayouts.setKeyOf(keys)
        val stored = preferencesRepository.userPreferences.first().screenLayouts
        val known = stored.layoutFor(setKey)
        val layout = known ?: com.nendo.argosy.domain.model.ScreenLayout.defaultFor(
            attached.map {
                com.nendo.argosy.domain.model.ScreenSpec(
                    key = it.key,
                    widthPx = it.widthPx,
                    heightPx = it.heightPx,
                    builtIn = it.builtIn
                )
            },
            invertInternalOrder = com.nendo.argosy.util.DisplayAffinityHelper.hasInvertedInternalOrder()
        )
        return ResolvedScreenLayout(attached, setKey, stored, known, layout)
    }

    fun applyStoredScreenLayout(promptWhenUnknown: Boolean = false) {
        if (displayAffinityHelper.isDockedDark) return
        scope.launch {
            val resolved = resolveScreenLayout() ?: return@launch
            val attached = resolved.attached
            val setKey = resolved.setKey
            val known = resolved.known
            val layout = resolved.layout
            val primary = attached.find { it.key == layout.primaryKey } ?: return@launch
            applyScreenLayout(
                primaryDisplayId = primary.displayId,
                presentationDisplayId = attached.find { it.key == layout.presentationKey }?.displayId,
                appTargetDisplayId = attached.find { it.key == layout.appTargetKey }?.displayId,
                hasPresentation = !layout.isSingleDisplay
            )
            _unconfiguredScreenSet.value = setKey.takeIf {
                promptWhenUnknown && known == null && attached.size > 1
            }
            ensureAppScreenLaunched()
        }
    }

    /**
     * Moves the PRIMARY role onto [displayId]. A layout that already matches the live arrangement
     * changes nothing and takes nobody's focus, and a running session keeps the screen it was
     * launched on until it ends.
     */
    fun setPrimaryDisplayId(displayId: Int) {
        val swapped = displayId == android.view.Display.DEFAULT_DISPLAY
        if (swapped == _isRolesSwapped.value) return
        if (sessionStateStore.hasActiveSession()) return
        commitRoleSwap(swapped)
        if (swapped) refocusMain()
    }

    /**
     * Bumped whenever the launcher's display language changes. A locale override only takes
     * effect for resources resolved through an Activity's own wrapped base Context, so both
     * MainActivity and SecondaryHomeActivity collect this and recreate themselves; a token rather
     * than the language itself, since each Activity already reads the new value straight from
     * SessionStateStore during its own attachBaseContext.
     */
    private val _localeChangeToken = MutableStateFlow(0)
    val localeChangeToken: StateFlow<Int> = _localeChangeToken

    fun notifyLocaleChanged() {
        _localeChangeToken.value += 1
    }

    private val _pendingDeepLink = MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: StateFlow<android.net.Uri?> = _pendingDeepLink

    fun publishDeepLink(uri: android.net.Uri) {
        _pendingDeepLink.value = uri
    }

    fun consumeDeepLink(uri: android.net.Uri): Boolean = _pendingDeepLink.compareAndSet(uri, null)

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
        setSecondaryHomeComponentEnabled(
            sessionStateStore.isDualScreenEnabled() || displayAffinityHelper.isDockedDark
        )
        _isDualScreenDevice.value = displayAffinityHelper.hasSecondaryDisplay
    }

    /**
     * Safety net for a companion that cannot initialize on the secondary display: releases the
     * display back to the OS, drops the launcher to single-screen, and stops the relaunch loop.
     * [persistent] records the refusal across restarts, for a display that structurally rejects
     * the companion rather than a transient launch failure.
     */
    fun companionTargetVanished(): Boolean {
        val target = displayAffinityHelper.lastCompanionTargetDisplayId ?: return false
        return !displayAffinityHelper.isPhysicalDisplay(target)
    }

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
        eachCompanion { it.finishCompanion() }
    }

    /**
     * Dedups raw Android input events seen across all dispatch paths (primary activity,
     * companion activity, libretro activity, and forwarded keys). The first path to
     * [claimInput] wins; parallel deliveries of the same physical event get dropped.
     */
    private val inputDedup = InputDedupBuffer()

    fun claimInput(event: android.view.KeyEvent): Boolean =
        inputDedup.claim(InputSignature.of(event)).also {
            if (it) notifyUserActivity("key=${event.keyCode} action=${event.action} device=${event.deviceId}")
        }

    fun claimInput(event: android.view.MotionEvent): Boolean =
        inputDedup.claim(InputSignature.of(event)).also {
            if (it && isMeaningfulMotion(event)) {
                notifyUserActivity("motion action=${event.actionMasked} device=${event.deviceId} source=${event.source}")
            }
        }

    private val lastMotionAxes = HashMap<Int, FloatArray>()

    /**
     * Whether a generic motion event represents a person moving something, as opposed to a
     * connected pad streaming analog readings that have not changed. An idle stick with electrical
     * drift emits a steady stream of joystick MOVE events; counting those as user activity resets
     * the playback dim ramp forever. A joystick event counts only when at least one axis moved past
     * [MOTION_ACTIVITY_AXIS_THRESHOLD] since the previous event from the same device; every
     * non-joystick source counts unconditionally.
     */
    private fun isMeaningfulMotion(event: android.view.MotionEvent): Boolean {
        if (!event.isFromSource(android.view.InputDevice.SOURCE_CLASS_JOYSTICK)) return true
        val axes = FloatArray(MOTION_ACTIVITY_AXES.size) { event.getAxisValue(MOTION_ACTIVITY_AXES[it]) }
        val previous = lastMotionAxes.put(event.deviceId, axes) ?: return false
        for (i in axes.indices) {
            if (kotlin.math.abs(axes[i] - previous[i]) >= MOTION_ACTIVITY_AXIS_THRESHOLD) return true
        }
        return false
    }

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

    private val _lastUserActivityAtMs = MutableStateFlow(android.os.SystemClock.elapsedRealtime())

    /**
     * When the person last did something, as [android.os.SystemClock.elapsedRealtime], raised by
     * [notifyUserActivity]. This is the one idle clock: anything that dims or sleeps a screen on
     * inactivity derives from it, so input on either display resets both.
     */
    val lastUserActivityAtMs: StateFlow<Long> = _lastUserActivityAtMs

    /**
     * The timers behind idleness and the playback dim run on this scope rather than on [scope],
     * which is rebound to each new primary activity and cancelled when the old one is destroyed. A
     * ramp riding [scope] dies silently when that happens - cancelled mid-delay with no value
     * emitted, which froze the dim at its partial stage and never reached dark. This scope lives as
     * long as the manager, so the only ways a ramp ends are the explicit ones, and every one of
     * those publishes a level.
     */
    private val idleTimerScope =
        com.nendo.argosy.util.SafeCoroutineScope(Dispatchers.Main, "MediaDimRamp")

    /**
     * The single notion of "the person did something", raised by every claimed key event, by
     * touch on any of the app's windows, and by claimed joystick motion whose axes actually moved
     * (see [isMeaningfulMotion]). It holds both screens awake and, during a playback, restores the
     * dimmed screen to full brightness and restarts its dim ramp. [source] names the trigger for
     * the diagnostic log.
     */
    fun notifyUserActivity(source: String) {
        _lastUserActivityAtMs.value = android.os.SystemClock.elapsedRealtime()
        Logger.debug(MEDIA_DIM_LOG_TAG, "userActivity source=$source")
        _userActive.value = true
        userIdleJob?.cancel()
        userIdleJob = idleTimerScope.launch {
            delay(screenOffTimeoutMs())
            _userActive.value = false
        }
        restartMediaDimRamp("userActivity:$source")
    }

    private val _mediaDimBrightness = MutableStateFlow<Float?>(null)

    /**
     * The window-brightness override for screens not showing a live playback, or null for no
     * override. It ramps with inactivity while a playback is open - full, then
     * [MEDIA_DIM_PARTIAL_BRIGHTNESS], then dark - and snaps back to null on any input and when the
     * playback ends. Windows apply it only when the player's display - its report, or the
     * relocation target while the report is cleared - is a different one, so the screen showing
     * the video never dims.
     */
    val mediaDimBrightness: StateFlow<Float?> = _mediaDimBrightness

    private val _mediaDimCoverAlpha = MutableStateFlow(0f)

    /**
     * Opacity of the black cover the dimmed window draws over its content, 0..1. A window
     * brightness of zero is the panel's minimum backlight, not off, so the ramp fades this in
     * across the second leg - from the partial stage to the off threshold - reaching fully opaque
     * exactly when the brightness floor lands. A wake releases the brightness override at once but
     * fades this cover out over [MEDIA_DIM_WAKE_FADE_MS] rather than snapping it, because the
     * backlight's climb back to the user's level is the display controller's own ramp and cannot
     * be hurried; the cover leaving in step with the light returning reads as one wake instead of
     * black vanishing over a still-dim screen.
     */
    val mediaDimCoverAlpha: StateFlow<Float> = _mediaDimCoverAlpha

    private var mediaDimJob: Job? = null

    /**
     * Arms the ramp from CURRENT state - a live playback plus the time since the last user input -
     * rather than from a playback transition. StateFlow conflates, so an open edge following a
     * close can be collapsed away during a fast item switch; arming from state means a missed or
     * late edge can delay the ramp but never leave it dormant. When the accumulated idle time has
     * already earned a stage, that stage is published immediately instead of restarting the ramp
     * from zero.
     */
    private fun restartMediaDimRamp(reason: String) {
        mediaDimJob?.cancel()
        mediaDimJob = null
        if (mediaPlaybackTracker.activePlayback.value == null) {
            if (_mediaDimBrightness.value != null) {
                Logger.debug(MEDIA_DIM_LOG_TAG, "cleared, no playback (reason=$reason)")
            }
            _mediaDimBrightness.value = null
            _mediaDimCoverAlpha.value = 0f
            return
        }
        val idleMs = android.os.SystemClock.elapsedRealtime() - _lastUserActivityAtMs.value
        Logger.debug(MEDIA_DIM_LOG_TAG, "armed reason=$reason idleMs=$idleMs")
        mediaDimJob = idleTimerScope.launch {
            val untilPartial = MEDIA_DIM_PARTIAL_DELAY_MS - idleMs
            if (untilPartial > 0) {
                _mediaDimBrightness.value = null
                fadeOutMediaDimCoverOnWake()
                val remainingToPartial = MEDIA_DIM_PARTIAL_DELAY_MS -
                    (android.os.SystemClock.elapsedRealtime() - _lastUserActivityAtMs.value)
                if (remainingToPartial > 0) delay(remainingToPartial)
            }
            _mediaDimBrightness.value = MEDIA_DIM_PARTIAL_BRIGHTNESS
            Logger.debug(MEDIA_DIM_LOG_TAG, "stage partial brightness=$MEDIA_DIM_PARTIAL_BRIGHTNESS")
            val untilOff = MEDIA_DIM_OFF_DELAY_MS - maxOf(idleMs, MEDIA_DIM_PARTIAL_DELAY_MS)
            if (untilOff > 0) {
                Logger.debug(MEDIA_DIM_LOG_TAG, "cover fade start durationMs=$untilOff")
                val fadeStart = android.os.SystemClock.elapsedRealtime()
                val startAlpha = _mediaDimCoverAlpha.value
                while (true) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - fadeStart
                    if (elapsed >= untilOff) break
                    _mediaDimCoverAlpha.value =
                        startAlpha + (1f - startAlpha) * (elapsed.toFloat() / untilOff)
                    delay(MEDIA_DIM_COVER_STEP_MS)
                }
            }
            _mediaDimCoverAlpha.value = 1f
            _mediaDimBrightness.value = MEDIA_DIM_OFF_BRIGHTNESS
            Logger.debug(MEDIA_DIM_LOG_TAG, "stage off brightness=$MEDIA_DIM_OFF_BRIGHTNESS cover=1")
        }
    }

    /**
     * Fades the black cover out from its current opacity on wake instead of dropping it, so the
     * cover leaves at roughly the pace the backlight returns. The duration scales with the
     * starting opacity, keeping the fade rate constant when a wake lands mid-fade and the new ramp
     * job resumes from a partial alpha. Runs inside [mediaDimJob]; further activity restarts it
     * from the current alpha and a playback close cancels it and zeroes the cover.
     */
    private suspend fun fadeOutMediaDimCoverOnWake() {
        val startAlpha = _mediaDimCoverAlpha.value
        if (startAlpha <= 0f) return
        val durationMs = (MEDIA_DIM_WAKE_FADE_MS * startAlpha).toLong().coerceAtLeast(1L)
        Logger.debug(
            MEDIA_DIM_LOG_TAG,
            "wake cover fade start alpha=$startAlpha durationMs=$durationMs"
        )
        val fadeStart = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - fadeStart
            if (elapsed >= durationMs) break
            _mediaDimCoverAlpha.value = startAlpha * (1f - elapsed.toFloat() / durationMs)
            delay(MEDIA_DIM_WAKE_FADE_STEP_MS)
        }
        _mediaDimCoverAlpha.value = 0f
        Logger.debug(MEDIA_DIM_LOG_TAG, "wake cover fade done")
    }

    private fun stopMediaDimRamp(reason: String) {
        mediaDimJob?.cancel()
        mediaDimJob = null
        _mediaDimBrightness.value = null
        _mediaDimCoverAlpha.value = 0f
        Logger.debug(MEDIA_DIM_LOG_TAG, "stopped reason=$reason")
    }

    /**
     * Drives the ramp from the playback flow's current value on the manager-lifetime timer scope.
     * The activity-bound [scope] that hosts [observeMedia] dies with its activity and is only
     * rebound on the next primary-activity create; the dim must survive that, so it observes here
     * instead. Every emission of a live playback re-arms; re-arming is idempotent because the
     * stages are computed from the last-activity timestamp, not from the moment of arming.
     */
    private fun observeMediaDim() {
        idleTimerScope.launch {
            mediaPlaybackTracker.activePlayback.collect { playback ->
                if (playback != null) {
                    restartMediaDimRamp("playback:${playback.itemId}")
                } else {
                    stopMediaDimRamp("playbackClosed")
                }
            }
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
        observeAchievementUnlocks()
    }
    interface CompanionHost {
        fun onForegroundChanged(isForeground: Boolean)
        fun onWizardStateChanged(isActive: Boolean)
        fun onSessionStarted(gameId: Long, isHardcore: Boolean, channelName: String?)
        fun onSessionHardcoreChanged(isHardcore: Boolean, channelName: String?)
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
        fun onDownloadCompleted(gameId: Long)
        fun finishCompanion()
    }

    interface SessionQuickActions {
        fun quickSave()
        fun loadState(slotNumber: Int)
        fun screenshot()
        fun openCheats()
        fun openGameSettings()
        fun quit()
    }

    var sessionQuickActions: SessionQuickActions? = null
        set(value) {
            field = value
            _swappedCompanionState.update { it.copy(quickActionsAvailable = value != null) }
            if (value == null) _sessionControls.value = com.nendo.argosy.ui.dualscreen.dashboard.SessionControls()
        }

    private val _sessionControls = MutableStateFlow(com.nendo.argosy.ui.dualscreen.dashboard.SessionControls())
    val sessionControls: StateFlow<com.nendo.argosy.ui.dualscreen.dashboard.SessionControls> = _sessionControls

    fun updateSessionControls(controls: com.nendo.argosy.ui.dualscreen.dashboard.SessionControls) {
        _sessionControls.value = controls
    }

    private val dashboardScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    val dashboardReader = com.nendo.argosy.ui.screens.gamedetail.components.DocumentReaderController(
        scope = dashboardScope,
        loader = gameDocumentLoader,
        romMRepository = romMRepository,
        highlightStore = documentHighlightStore
    ).also { reader ->
        dashboardScope.launch {
            reader.state.map { it != null }.distinctUntilChanged().drop(1).collect { open ->
                if (!open) refreshDashboardDocumentProgress()
            }
        }
    }

    private suspend fun refreshDashboardDocumentProgress() {
        val state = _swappedCompanionState.value
        if (!state.isLoaded) return
        val romId = withContext(Dispatchers.IO) { gameDao.getById(state.gameId)?.rommId } ?: return
        suspend fun progressOf(document: com.nendo.argosy.ui.screens.gamedetail.GameDocument?) =
            document?.rommFileId?.let { romMRepository.getDocumentProgress(romId, it) }
        val manual = progressOf(state.manual)
        val walkthrough = progressOf(state.walkthrough)
        _swappedCompanionState.update { live ->
            if (live.gameId != state.gameId) return@update live
            live.copy(
                manualLastPage = manual?.lastPage,
                walkthroughProgress = walkthrough?.progress
            )
        }
    }

    fun openDashboardDocument(document: com.nendo.argosy.ui.screens.gamedetail.GameDocument) {
        val gameId = companionSessionGameId
        scope.launch {
            val romId = gameDao.getById(gameId)?.rommId
            dashboardReader.open(document, romId)
        }
    }

    fun updateCompanionSaveDirty(isDirty: Boolean) {
        _swappedCompanionState.update { it.copy(isDirty = isDirty) }
    }

    var sessionRefocus: (() -> Unit)? = null

    private val companionHosts =
        com.nendo.argosy.ui.dualscreen.DisplayHostRegistry<CompanionHost>()

    fun registerCompanionHost(displayId: Int, host: CompanionHost) {
        companionHosts.register(displayId, host)
    }

    fun unregisterCompanionHost(displayId: Int, host: CompanionHost) {
        companionHosts.unregister(displayId, host)
    }

    /**
     * The companion surface that takes input: the one on the display holding the interactive role,
     * or the only companion there is.
     */
    val controlCompanion: CompanionHost?
        get() {
            val interactive = displayAffinityHelper
                .getRoleDisplayIds(_isRolesSwapped.value)
                ?.first
            companionHosts.hostFor(interactive)?.let { return it }
            return companionHosts.all().singleOrNull()
        }

    private fun eachCompanion(action: (CompanionHost) -> Unit) {
        companionHosts.all().forEach(action)
    }

    interface AppScreenHost {
        fun releaseAppScreen()
    }

    private val appScreenHosts =
        com.nendo.argosy.ui.dualscreen.DisplayHostRegistry<AppScreenHost>()

    fun registerAppScreenHost(displayId: Int, host: AppScreenHost) {
        appScreenHosts.register(displayId, host)
    }

    fun unregisterAppScreenHost(displayId: Int, host: AppScreenHost) {
        appScreenHosts.unregister(displayId, host)
    }

    fun holdsAppScreen(displayId: Int): Boolean =
        displayAffinityHelper.appScreenDisplayId(_isRolesSwapped.value) == displayId

    /**
     * The displays a viewer can hand input to, in screen-number order, each with the number the
     * badge draws for it.
     */
    fun focusableDisplays(): List<com.nendo.argosy.util.AttachedScreen> =
        com.nendo.argosy.util.ScreenCatalog(appContext).attachedScreens()

    /**
     * Moves input focus to [displayId], through whichever surface of ours is rendered there.
     */
    fun focusDisplay(displayId: Int) {
        FocusDirectorActivity.launchOnDisplay(activityContext, displayId)
    }

    fun releaseStaleAppScreens() {
        appScreenHosts.displayIds()
            .filterNot { holdsAppScreen(it) }
            .forEach { appScreenHosts.hostFor(it)?.releaseAppScreen() }
    }

    fun notifyLibraryRefresh() {
        eachCompanion { it.onLibraryRefresh() }
    }

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

    private val _companionDetails =
        MutableStateFlow<List<Pair<com.nendo.argosy.ui.dualscreen.SlotOwner, CompanionDetail>>>(emptyList())

    /**
     * Describes [owner]'s selection on the presentation screen, above every other owner's. Null
     * withdraws only [owner]'s description, uncovering the one beneath it.
     */
    fun setCompanionDetail(owner: com.nendo.argosy.ui.dualscreen.SlotOwner, detail: CompanionDetail?) {
        _companionDetails.update { entries ->
            entries.filterNot { it.first == owner } + listOfNotNull(detail?.let { owner to it })
        }
    }

    private val _isCompanionActive = MutableStateFlow(false)
    val isCompanionActive: StateFlow<Boolean> = _isCompanionActive

    /**
     * Whether the companion window is the one hosting the launcher. What each activity renders and
     * where each activity sends its key events both read this, so a screen cannot end up drawing a
     * presentation slot while still handling input for a launcher it is not showing.
     */
    val companionHoldsPrimary: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(
            _isDualScreenDevice,
            _isCompanionActive,
            _isRolesSwapped
        ) { dualScreen, companionActive, swapped ->
            dualScreen && companionActive && !swapped
        }.stateIn(activityIndependentScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _presentationSlots =
        MutableStateFlow<List<Pair<com.nendo.argosy.ui.dualscreen.SlotOwner, com.nendo.argosy.ui.dualscreen.PresentationSlot>>>(
            emptyList()
        )

    private val _presentationStyle =
        MutableStateFlow(com.nendo.argosy.domain.model.PresentationStyle())
    val presentationStyle: StateFlow<com.nendo.argosy.domain.model.PresentationStyle> =
        _presentationStyle

    /**
     * What the presentation screen should show right now: the most recent published slot, or the
     * fallback once every publisher has released. Screens publish while they are on screen and
     * release when they leave, so the surface follows navigation without either side tracking it.
     */
    fun presentSlot(
        owner: com.nendo.argosy.ui.dualscreen.SlotOwner,
        slot: com.nendo.argosy.ui.dualscreen.PresentationSlot
    ) {
        _presentationSlots.update { entries ->
            entries.filterNot { it.first == owner } + (owner to slot)
        }
    }

    fun releaseSlot(owner: com.nendo.argosy.ui.dualscreen.SlotOwner) {
        _presentationSlots.update { entries -> entries.filterNot { it.first == owner } }
    }

    private val _primaryOnHome = MutableStateFlow(true)

    /**
     * Whether the primary display is showing home. Anything else there - Apps, Settings, a
     * destination reached from the drawer - owns the pad, and the companion is a passenger until it
     * comes back. The companion outlives the primary leaving and returning, so it cannot infer this
     * from its own lifecycle.
     */
    val primaryOnHome: StateFlow<Boolean> = _primaryOnHome

    fun setPrimaryOnHome(onHome: Boolean) {
        _primaryOnHome.value = onHome
    }

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

    private val _mediaPlayerDisplay = MutableStateFlow<Int?>(null)

    /**
     * The observable face of [mediaPlayerDisplayId], for the windows that dim themselves while a
     * playback runs on some other display: a relocation re-reports here and the dimming follows it.
     */
    val mediaPlayerDisplay: StateFlow<Int?> = _mediaPlayerDisplay

    /**
     * The display the player window currently occupies, reported by the player itself. Held here
     * rather than in the activity because the companion has to be able to send an episode to a
     * window living on another display.
     *
     * It is an observation, not an assignment, so it is cleared when the roles swap: the position
     * it records was chosen under the previous arrangement, and sending the next episode there
     * would place it by a rule that no longer applies.
     */
    var mediaPlayerDisplayId: Int?
        get() = _mediaPlayerDisplay.value
        set(value) {
            _mediaPlayerDisplay.value = value
        }

    /**
     * The player window's own key dispatch, registered while that window is alive so the companion
     * can hand over events Android delivered to it instead. A forwarded event runs the exact
     * dispatch a directly-delivered one runs - claim, mapping and routing included - so the two
     * paths cannot diverge. A separate pair from the emulator dispatchers because a game and a
     * playback can be live at once, one per display, and a shared field would let either
     * registration clobber the other. Cleared by the player on teardown and again here when the
     * playback ends, so a window that died uncleanly cannot leave a sink behind to swallow input.
     */
    @Volatile var mediaPlayerKeyDispatcher: ((android.view.KeyEvent) -> Boolean)? = null

    /**
     * The motion half of [mediaPlayerKeyDispatcher]: raw trigger-axis motion the companion window
     * receives goes through the player's own axis-to-key conversion rather than a second one.
     */
    @Volatile var mediaPlayerMotionDispatcher: ((android.view.MotionEvent) -> Boolean)? = null

    private val _mediaPlayerControlsLocked = MutableStateFlow(false)

    /**
     * Whether the viewer has locked the player's controls. The companion's key-yield gate reads
     * this and keeps the pad for itself while it holds, so the one gate that already decides who
     * owns the pad during playback is also the one that honours the lock - there is no second
     * routing path to disagree with it. Written only by the player window mirroring its own view
     * model, and cleared here when the playback ends so a window that died uncleanly cannot leave
     * the pad locked out of a player that no longer exists.
     */
    val mediaPlayerControlsLocked: StateFlow<Boolean> = _mediaPlayerControlsLocked

    /**
     * Engaging the lock also hands the pad to the companion window outright: forwarding stops at
     * the gate, but the player's window may still hold input focus - a touch on it takes focus -
     * and focus is what decides which window Android delivers keys to in the first place.
     */
    fun setMediaPlayerControlsLocked(locked: Boolean) {
        if (_mediaPlayerControlsLocked.value == locked) return
        _mediaPlayerControlsLocked.value = locked
        if (locked) controlCompanion?.refocusSelf()
    }

    fun toggleCompanionMediaView() {
        _companionMediaVisible.value = !_companionMediaVisible.value &&
            (_mediaSignedIn.value || mediaPlaybackTracker.activePlayback.value != null)
    }

    fun setCompanionMediaVisible(visible: Boolean) {
        _companionMediaVisible.value = visible
    }

    private val _mediaInfoRequest = MutableStateFlow<String?>(null)

    /**
     * The media title whose information the viewer explicitly asked to see, or null when the media
     * panel should follow the playback as usual. Held here rather than in either home view model
     * because the panel that renders it lives on whichever display holds the interactive role, and
     * only DSM state reaches both.
     */
    val mediaInfoRequest: StateFlow<String?> = _mediaInfoRequest

    fun requestMediaInfo(itemId: String) {
        if (itemId.isBlank()) return
        _mediaInfoRequest.value = itemId
    }

    fun clearMediaInfoRequest() {
        _mediaInfoRequest.value = null
    }

    /**
     * Where the player should move now that a game has taken a screen, or null when there is nowhere
     * to move it to. Null is the single-screen answer and also the answer when the game and the
     * player are already on different displays.
     */
    /**
     * Where a film belongs when nothing has stated a position yet.
     *
     * The bigger panel wins, because watching is the one thing on this device that is better for
     * having more screen, and unlike Home or Library it is not something the viewer is driving from
     * moment to moment. A game already occupying that display is the only override, and then the
     * film takes the other screen rather than opening underneath the game.
     */
    fun mediaPlayerRelocationDisplayId(): Int? {
        val emulator = emulatorDisplayId
        if (emulator != null) return displayAffinityHelper.getMediaPlayerDisplayId(emulator)
        return displayAffinityHelper.largestDisplayId() ?: interactiveDisplayId()
    }

    fun playMediaItem(itemId: String, startOver: Boolean = false) {
        if (itemId.isBlank()) return
        val target = mediaPlayerDisplayId ?: mediaPlayerRelocationDisplayId()
        val options = target?.let {
            displayAffinityHelper.getActivityOptions(forEmulator = false, overrideDisplayId = it)
        }
        val launchContext = target?.let { displayAffinityHelper.displayContext(it) } ?: appContext
        com.nendo.argosy.ui.screens.player.PlayerActivity.startOnDisplay(
            context = launchContext,
            args = com.nendo.argosy.ui.screens.player.PlayerArgs(
                itemId = itemId,
                startPositionMs = if (startOver) 0L else
                    com.nendo.argosy.ui.screens.player.PlayerActivity.RESOLVE_RESUME
            ),
            options = options
        )
        if (target != null) directMediaPlayerFocus(target)
    }

    private var mediaFocusJob: Job? = null

    /**
     * Makes the player's display the focused one shortly after a playback launch. Firmwares that
     * keep a per-display volume bind a playback's audio against the focused display, and a launch
     * that began as a touch on the other screen leaves focus there - the film then answers to that
     * screen's volume instead of its own. Skipped while a game is up or launching, because the
     * game owns focus and taking it would also take the pad.
     *
     * The translucent focus director is tried first because it feeds the player window no input;
     * the accessibility tap is the fallback for external displays, where the director launch is
     * denied and a synthetic touch is the only remaining way to move display focus.
     *
     * A locked player yields: the lock deliberately parks focus on the companion window so the pad
     * drives it, and reclaiming focus here would undo that routing.
     */
    fun directMediaPlayerFocus(displayId: Int) {
        if (!displayAffinityHelper.hasSecondaryDisplay) return
        if (sessionStateStore.hasActiveSession()) return
        if (isLaunchingGame) return
        if (_mediaPlayerControlsLocked.value) return
        mediaFocusJob?.cancel()
        mediaFocusJob = scope.launch {
            delay(MEDIA_FOCUS_DIRECT_DELAY_MS)
            if (sessionStateStore.hasActiveSession() || isLaunchingGame) return@launch
            try {
                FocusDirectorActivity.launchOnDisplay(appContext, displayId)
            } catch (e: SecurityException) {
                val a11y = FocusAccessibilityService.instance
                if (a11y != null) {
                    a11y.tapOnDisplay(displayId)
                } else {
                    Log.w(TAG, "Media focus direct blocked on display $displayId (device restriction)")
                }
            }
        }
    }

    private var accountObserverJob: Job? = null
    private var mediaObserverJob: Job? = null

    /**
     * Every achievement on record for the running game, refreshed from the database on each
     * unlock the bus reports. Both companion roles draw the same list; empty outside a session.
     * Declared ahead of [init] with the other observer jobs, because a property initialiser that
     * runs after the init block would reset the job handle the block just stored.
     */
    private val _companionAchievements = MutableStateFlow<List<AchievementUi>>(emptyList())
    val companionAchievements: StateFlow<List<AchievementUi>> = _companionAchievements
    private var companionSessionGameId = -1L
    private var achievementObserverJob: Job? = null

    private fun observeAchievementUnlocks() {
        achievementObserverJob?.cancel()
        achievementObserverJob = scope.launch {
            achievementUpdateBus.updates.collect { update ->
                if (update.gameId != companionSessionGameId) return@collect
                refreshCompanionAchievements(update.gameId)
                _swappedCompanionState.update {
                    it.copy(
                        achievementCount = update.totalCount,
                        earnedAchievementCount = update.earnedCount
                    )
                }
            }
        }
    }

    private suspend fun refreshCompanionAchievements(gameId: Long) {
        val loaded = withContext(Dispatchers.IO) {
            raRepository.getCachedAchievements(gameId).map { it.toAchievementUi() }
        }
        if (companionSessionGameId == gameId) _companionAchievements.value = loaded
    }

    private fun clearCompanionAchievements() {
        companionSessionGameId = -1L
        _companionAchievements.value = emptyList()
    }

    init {
        scope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                menuWrapMode = prefs.menuWrapMode
                _presentationStyle.value = prefs.presentationStyle
            }
        }
        observeActiveAccount()
        observeMedia()
        observeMediaDim()
        observeAchievementUnlocks()
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
                if (!isOpen) {
                    mediaFocusJob?.cancel()
                    mediaFocusJob = null
                    mediaPlayerDisplayId = null
                    mediaPlayerKeyDispatcher = null
                    mediaPlayerMotionDispatcher = null
                    _mediaPlayerControlsLocked.value = false
                }
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
        clearCompanionAchievements()
        _dualSyncOverlay.value = null
        _dualSaveConflict.value = null
        eachCompanion { it.onAccountSwitched() }
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

    private val _keyboardToggleEvent = MutableStateFlow(0L)
    val keyboardToggleEvent: StateFlow<Long> = _keyboardToggleEvent

    var onOverlayFocusChanged: ((Boolean) -> Unit)? = null
    var isOverlayFocused = false
        set(value) {
            field = value
            onOverlayFocusChanged?.invoke(isOverlayFocused)
        }

    private val _swappedIsGameActive = MutableStateFlow(false)
    val swappedIsGameActive: StateFlow<Boolean> = _swappedIsGameActive

    private val _swappedCompanionState = MutableStateFlow(
        com.nendo.argosy.hardware.CompanionInGameState()
    )
    val swappedCompanionState: StateFlow<com.nendo.argosy.hardware.CompanionInGameState> =
        _swappedCompanionState
    var swappedSessionTimer: com.nendo.argosy.hardware.CompanionSessionTimer? = null
        private set

    /**
     * What the presentation screen shows right now. A live session outranks everything, then the
     * most recent published slot, then whatever screen is describing its selection.
     */
    /**
     * Whether the Primary surface on [primaryDisplayId] should show the in-game dashboard in place
     * of Home. It does only while a game runs on the presentation screen, which leaves the
     * dashboard nowhere else to go; a game on the app screen leaves the presentation screen free
     * for it and Home stays on Primary.
     */
    fun primaryShowsDashboard(primaryDisplayId: Int?): Boolean {
        if (!_swappedIsGameActive.value) return false
        val gameDisplay = emulatorDisplayId ?: return false
        if (gameDisplay == primaryDisplayId) return false
        val showcase = showcaseDisplayId() ?: return true
        return gameDisplay == showcase
    }

    val presentationSlot: StateFlow<com.nendo.argosy.ui.dualscreen.PresentationSlot> =
        kotlinx.coroutines.flow.combine(
            _presentationSlots,
            _companionDetails,
            _swappedIsGameActive,
            _swappedCompanionState,
            _companionAchievements
        ) { slots, details, gameActive, inGame, achievements ->
            val detail = details.lastOrNull()?.second
            when {
                gameActive && inGame.isLoaded ->
                    com.nendo.argosy.ui.dualscreen.PresentationSlot.InGame(inGame, achievements)
                slots.isNotEmpty() -> slots.last().second
                detail != null -> com.nendo.argosy.ui.dualscreen.PresentationSlot.Detail(detail)
                else -> com.nendo.argosy.ui.dualscreen.PresentationSlot.Fallback
            }
        }
            .stateIn(
                activityIndependentScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                com.nendo.argosy.ui.dualscreen.PresentationSlot.Fallback
            )

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
                eachCompanion { it.onRoleSwapped(newSwapped) }
            }
            _isDualScreenDevice.value = true
            CompanionGuardService.start(appContext)
            ensureCompanionLaunched()
            applyStoredScreenLayout(promptWhenUnknown = true)
            scheduleDockedResync()
        }

        override fun onDisplayRemoved(displayId: Int) {
            dockedDark = displayAffinityHelper.isDockedDark
            companionLaunchJob?.cancel()
            companionLaunchJob = null
            _isCompanionActive.value = false
            CompanionGuardService.stop(appContext)
            reprobeSecondaryDisplay()
            _isDualScreenDevice.value = displayAffinityHelper.hasSecondaryDisplay
            cleanupSwappedState()
            if (displayAffinityHelper.hasSecondaryDisplay) {
                CompanionGuardService.start(appContext)
                ensureCompanionLaunched()
            }
            clearUnconfiguredScreenSet()
            applyStoredScreenLayout()
        }

        override fun onDisplayChanged(displayId: Int) {
            syncDockedState()
        }
    }

    private var dockedDark = false

    private fun syncDockedState() {
        val docked = displayAffinityHelper.isDockedDark
        if (docked == dockedDark) return
        dockedDark = docked
        Log.i(TAG, "Built-in screens ${if (docked) "off beside an external display" else "back on"}")
        teardownCompanion()
        if (docked) {
            companionLaunchAttempts = 0
            displayAffinityHelper.secondaryDisplayUsable = true
            sessionStateStore.setSecondaryDisplayUsable(true)
            if (_isRolesSwapped.value) commitRoleSwap(false)
            _hasPresentationScreen.value = false
            setSecondaryHomeComponentEnabled(true)
            _isDualScreenDevice.value = true
            CompanionGuardService.start(appContext)
            ensureCompanionLaunched()
        } else {
            reprobeSecondaryDisplay()
            if (displayAffinityHelper.hasSecondaryDisplay) {
                CompanionGuardService.start(appContext)
                ensureCompanionLaunched()
            }
            applyStoredScreenLayout()
        }
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

        _mediaInfoRequest.value = null
        _swappedIsGameActive.value = false
        _swappedCompanionState.value = com.nendo.argosy.hardware.CompanionInGameState()
        swappedSessionTimer?.stop(appContext)
        swappedSessionTimer = null

        eachCompanion { it.onRoleSwapped(false) }

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
    private fun isEmulatorStillOnScreen(context: Context): Boolean {
        val emulatorPackage = sessionStateStore.getEmulatorPackage() ?: return false
        return permissionHelper.isPackageOnScreenOrRecent(context, emulatorPackage)
    }

    /**
     * Whether the emulator has left the screen a launcher surface just resumed on. The emulator
     * reports stopped only after the launcher resumes, so a first answer of "still there" is asked
     * again after [EMULATOR_SETTLE_MS] while [launcherStillInFront] holds.
     */
    suspend fun emulatorLeftScreen(context: Context, launcherStillInFront: () -> Boolean): Boolean {
        if (!isEmulatorStillOnScreen(context)) return true
        delay(EMULATOR_SETTLE_MS)
        return launcherStillInFront() && !isEmulatorStillOnScreen(context)
    }

    /**
     * Ends the session whose emulator [emulatorLeftScreen] reported gone. On a dual-screen device it
     * then kills the stopped emulator along with any presentation window it left on the other
     * display, retrying until the process is cached or a new session starts.
     */
    fun endSessionAfterEmulatorLeft() {
        val emulatorPackage = sessionStateStore.getEmulatorPackage()
        emulatorDisplayId = null
        sessionStateStore.clearSession()
        val sessionEnd = playSessionTracker.endSessionInBackground()
        broadcastSessionCleared()
        if (emulatorPackage == null || !_isDualScreenDevice.value) return
        scope.launch {
            sessionEnd.join()
            repeat(EMULATOR_RELEASE_ATTEMPTS) {
                if (sessionStateStore.hasActiveSession()) return@launch
                gameLaunchDelegate.stopBackgroundEmulator(emulatorPackage)
                delay(EMULATOR_RELEASE_INTERVAL_MS)
            }
        }
    }

    val homeAppsList: List<String>
        get() = sessionStateStore.getHomeApps()?.toList() ?: emptyList()

    val homeAppsFlow: kotlinx.coroutines.flow.Flow<List<String>>
        get() = preferencesRepository.userPreferences
            .map { it.secondaryHomeApps.toList() }
            .distinctUntilChanged()

    suspend fun installedAppLabels(): List<Pair<String, String>> =
        appsRepository.getInstalledApps()
            .map { it.packageName to it.label }
            .sortedBy { it.second.lowercase() }

    fun appLabel(packageName: String): String =
        appsRepository.getAppLabel(packageName) ?: packageName

    suspend fun isAppPinned(packageName: String): Boolean =
        appShortcutActions.isPinned(packageName)

    suspend fun isAppHidden(packageName: String): Boolean =
        appShortcutActions.isHidden(packageName)

    fun appMenuRowsFor(
        screens: List<com.nendo.argosy.util.AttachedScreen>
    ): List<com.nendo.argosy.ui.components.AppMenuRow> = buildList {
        if (screens.size > 1) {
            screens.forEach { screen ->
                add(
                    com.nendo.argosy.ui.components.AppMenuRow.OpenOnScreen(
                        screen.displayId,
                        screen.number,
                        screen.key
                    )
                )
            }
        }
        add(
            com.nendo.argosy.ui.components.AppMenuRow.Action(
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_SECONDARY_HOME
            )
        )
        add(
            com.nendo.argosy.ui.components.AppMenuRow.Action(
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_VISIBILITY
            )
        )
        add(
            com.nendo.argosy.ui.components.AppMenuRow.Action(
                com.nendo.argosy.ui.components.AppContextMenuItem.UNINSTALL
            )
        )
    }

    fun runAppMenuRow(packageName: String, row: com.nendo.argosy.ui.components.AppMenuRow) {
        when (row) {
            is com.nendo.argosy.ui.components.AppMenuRow.OpenOnScreen ->
                launchCompanionApp(packageName, row.screenKey)
            is com.nendo.argosy.ui.components.AppMenuRow.Action -> when (row.item) {
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_SECONDARY_HOME ->
                    scope.launch { appShortcutActions.togglePinned(packageName) }
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_VISIBILITY ->
                    scope.launch { appShortcutActions.toggleHidden(packageName) }
                com.nendo.argosy.ui.components.AppContextMenuItem.UNINSTALL ->
                    activityContext.startActivity(appShortcutActions.uninstallIntent(packageName))
                else -> Unit
            }
        }
    }

    fun clearPendingOverlay() {
        _pendingOverlayEvent.value = null
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
    }


    fun onOpenOverlayFromCompanion(eventName: String) {
        isOverlayFocused = true
        _pendingOverlayEvent.value = eventName ?: OVERLAY_MENU
        refocusMain()
    }

    /**
     * Raises or lowers the system keyboard for the launcher window. The launcher is refocused
     * first so the keyboard attaches to whatever it has focused rather than to the companion,
     * which has nothing to type into; where the keyboard lives on screen is the device's choice.
     */
    fun toggleUpperKeyboard() {
        refocusMain()
        _keyboardToggleEvent.value = System.currentTimeMillis()
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
            swappedSessionTimer?.stop(appContext)
            swappedSessionTimer = com.nendo.argosy.hardware.CompanionSessionTimer().also { it.start(appContext) }
            companionSessionGameId = gameId
            scope.launch { refreshCompanionAchievements(gameId) }
            scope.launch(Dispatchers.IO) {
                val game = gameDao.getById(gameId) ?: return@launch
                val platform = platformRepository.getById(game.platformId)
                val documents = com.nendo.argosy.ui.screens.gamedetail.components.gameDocuments(
                    game = game,
                    files = gameFileDao.getFilesForGame(gameId),
                    romMRepository = romMRepository
                )
                _swappedCompanionState.update {
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
                        channelName = sessionStateStore.getChannelName(),
                        isHardcore = sessionStateStore.isHardcore(),
                        isDirty = sessionStateStore.isSaveDirty(),
                        isLoaded = true,
                        backgroundPath = game.backgroundPath,
                        manual = documents.firstOrNull {
                            it.category == com.nendo.argosy.data.model.VariantCategory.MANUAL.key
                        },
                        walkthrough = documents.firstOrNull {
                            it.category == com.nendo.argosy.data.model.VariantCategory.WALKTHROUGH.key
                        }
                    ).withLiveQuickActionState(quickActionsAvailable = sessionQuickActions != null)
                }
                refreshDashboardDocumentProgress()
            }
            eachCompanion { it.onSessionStarted(gameId, isHardcore, channelName) }
        } else {
            if (!_swappedIsGameActive.value) return
            emulatorDisplayId = null
            _swappedIsGameActive.value = false
            _swappedCompanionState.value = com.nendo.argosy.hardware.CompanionInGameState()
            _sessionControls.value = com.nendo.argosy.ui.dualscreen.dashboard.SessionControls()
            dashboardReader.dismiss()
            clearCompanionAchievements()
            sessionStateStore.clearSession()
            swappedSessionTimer?.stop(appContext)
            swappedSessionTimer = null
            val savedSwapped = preGameRolesSwapped
            if (savedSwapped != null) {
                _isRolesSwapped.value = savedSwapped
                preGameRolesSwapped = null
            }
            Handler(Looper.getMainLooper()).post {
                if (savedSwapped != null) onRoleSwapped?.invoke(savedSwapped)
                eachCompanion {
                    it.onSessionEnded()
                    it.onRoleSwapped(_isRolesSwapped.value)
                }
            }
        }
    }

    /**
     * The hardcore claim of the running session changed after it started (RetroAchievements
     * confirmed or declined it). Only the mode and channel move; the session itself, its timer
     * and the companion screen stay where they are.
     */
    fun onSessionHardcoreChanged(isHardcore: Boolean, channelName: String?) {
        if (!_swappedIsGameActive.value) return
        _swappedCompanionState.update { it.copy(isHardcore = isHardcore, channelName = channelName) }
        eachCompanion { it.onSessionHardcoreChanged(isHardcore, channelName) }
    }

    fun onDownloadCompleted(gameId: Long) {
        eachCompanion { it.onDownloadCompleted(gameId) }
    }

    fun onRoleSwapReceived() {
        val resolver = com.nendo.argosy.util.DisplayRoleResolver(
            displayAffinityHelper, sessionStateStore
        )
        _isRolesSwapped.value = resolver.isSwapped
        onRoleSwapped?.invoke(_isRolesSwapped.value)
    }

    // --- Modal Operations ---

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
                            Log.w(TAG, "Channel switch restore failed: ${result.reason}")
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
                            Log.w(TAG, "Restore point apply failed: ${result.reason}")
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
            copySaveChannelUseCase(
                gameId = gameId,
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

    private fun broadcastSaveActionResult(type: String, gameId: Long) = Unit

    private fun broadcastUnifiedSaves(gameId: Long) = Unit

    /**
     * Sends the game's states to whichever screen is showing its detail.
     *
     * The states tab reads what is delivered here; without it the tab draws its slots over an
     * empty list however many states are cached or synced.
     */
    private fun stateEntriesFor(gameId: Long): List<UnifiedStateEntry> =
        lastStateEntries?.takeIf { it.first == gameId }?.second ?: emptyList()

    private fun stateSlotLabel(slot: Int): String =
        if (slot < 0) {
            appContext.getString(R.string.notif_dualscreen_state_slot_auto)
        } else {
            appContext.getString(R.string.notif_dualscreen_state_slot_numbered, slot)
        }

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
                notificationManager.showError(NotificationText.Res(R.string.notif_dualscreen_no_local_path))
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
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.notif_dualscreen_state_restored, listOf(stateSlotLabel(slot)))
                    )
                is com.nendo.argosy.domain.usecase.state.RestoreStateResult.VersionMismatch ->
                    notificationManager.showError(
                        NotificationText.Res(
                            R.string.notif_dualscreen_state_version_mismatch,
                            listOf(stateSlotLabel(slot))
                        )
                    )
                else ->
                    notificationManager.showError(
                        NotificationText.Res(R.string.notif_dualscreen_state_restore_failed, listOf(stateSlotLabel(slot)))
                    )
            }
            broadcastUnifiedStates(gameId)
        }
    }

    private fun handleStateDelete(gameId: Long, slotArg: String?) {
        val slot = slotArg?.toIntOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val entry = stateEntriesFor(gameId).firstOrNull { it.slotNumber == slot } ?: return@launch
            stateCacheManager.purgeState(gameId, entry.localCacheId, entry.serverStateId)
            notificationManager.showSuccess(
                NotificationText.Res(R.string.notif_dualscreen_state_deleted, listOf(stateSlotLabel(slot)))
            )
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
                    NotificationText.Res(
                        R.string.notif_dualscreen_state_copied,
                        listOf(stateSlotLabel(sourceSlot), stateSlotLabel(targetSlot))
                    )
                )
                broadcastUnifiedStates(gameId)
            } else {
                notificationManager.showError(
                    NotificationText.Res(R.string.notif_dualscreen_state_copy_failed, listOf(stateSlotLabel(sourceSlot)))
                )
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load states for gameId=$gameId", e)
            }
        }
    }

    private fun deliverSyncingDone(gameId: Long) = Unit

    // --- Companion Sync ---

    fun resyncCompanionState() {
        broadcastForegroundState(true)
        controlCompanion?.onOverlayClosed()
    }

    fun broadcastForegroundState(isForeground: Boolean) {
        sessionStateStore.setArgosyForeground(isForeground)
        eachCompanion { it.onForegroundChanged(isForeground) }
        if (isForeground) {
            ensureAppScreenLaunched()
            if (!_isCompanionActive.value && displayAffinityHelper.hasSecondaryDisplay) {
                ensureCompanionLaunched()
            }
            val isWizard = sessionStateStore.isWizardActive()
            if (isWizard) eachCompanion { it.onWizardStateChanged(true) }
            scope.launch {
                val prefs = preferencesRepository.preferences.first()
                updateHomeApps(prefs.secondaryHomeApps)
            }
        }
    }

    fun broadcastWizardState(isActive: Boolean) {
        sessionStateStore.setWizardActive(isActive)
        if (!isActive) sessionStateStore.setFirstRunComplete(true)
        eachCompanion { it.onWizardStateChanged(isActive) }
    }

    private var lastSwapTimeMs = 0L

    fun swapRoles() {
        val now = System.currentTimeMillis()
        if (now - lastSwapTimeMs < SWAP_DEBOUNCE_MS) return
        lastSwapTimeMs = now

        if (sessionStateStore.hasActiveSession()) return

        val newSwapped = !_isRolesSwapped.value
        mirrorOverrideTo(newSwapped)
        applyRoleChange(newSwapped)
    }

    private fun mirrorOverrideTo(swapped: Boolean) {
        val value = if (swapped) "SWAPPED" else "STANDARD"
        if (sessionStateStore.getDisplayRoleOverride() == value) return
        sessionStateStore.setDisplayRoleOverride(value)
        scope.launch {
            preferencesRepository.setDisplayRoleOverride(DisplayRoleOverride.fromString(value))
        }
    }

    fun clearDisplayRoleOverride() {
        if (sessionStateStore.getDisplayRoleOverride() == DisplayRoleOverride.AUTO.name) return
        sessionStateStore.setDisplayRoleOverride(DisplayRoleOverride.AUTO.name)
        scope.launch { preferencesRepository.setDisplayRoleOverride(DisplayRoleOverride.AUTO) }
    }

    /**
     * Stores the override and applies the arrangement it resolves to, skipping the live commit
     * while a game is running.
     */
    fun applyDisplayRoleOverride(override: DisplayRoleOverride) {
        sessionStateStore.setDisplayRoleOverride(override.name)
        scope.launch { preferencesRepository.setDisplayRoleOverride(override) }
        if (sessionStateStore.hasActiveSession()) return
        val resolved = DisplayRoleResolver(displayAffinityHelper, sessionStateStore).isSwapped
        if (resolved == _isRolesSwapped.value) return
        applyRoleChange(resolved)
    }

    private fun applyRoleChange(swapped: Boolean) {
        commitRoleSwap(swapped)
        activityIndependentScope.launch {
            withContext(Dispatchers.IO) { persistPrimaryRole(swapped) }
        }
    }

    private suspend fun persistPrimaryRole(swapped: Boolean) {
        if (displayAffinityHelper.isDockedDark) return
        val primaryDisplayId = displayAffinityHelper.getRoleDisplayIds(swapped)?.first ?: return
        val resolved = resolveScreenLayout() ?: return
        val primary = resolved.attached.find { it.displayId == primaryDisplayId } ?: return
        val next = resolved.layout.withRole(
            primary.key,
            com.nendo.argosy.domain.model.ScreenRole.PRIMARY
        )
        if (next.roles == resolved.layout.roles) return
        preferencesRepository.setScreenLayouts(resolved.stored.with(resolved.setKey, next))
    }

    private fun commitRoleSwap(newSwapped: Boolean) {
        _isRolesSwapped.value = newSwapped
        sessionStateStore.setRolesSwapped(newSwapped)
        mediaPlayerDisplayId = null
        _mediaInfoRequest.value = null
        onRoleSwapped?.invoke(newSwapped)
        eachCompanion { it.onRoleSwapped(newSwapped) }
        if (!newSwapped) controlCompanion?.refocusSelf()
    }

    fun broadcastOpenOverlay(eventName: String) {
        controlCompanion?.onOverlayRequested(eventName)
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
        if (sessionStateStore.isDualScreenEnabled() || displayAffinityHelper.isDockedDark) {
            setSecondaryHomeComponentEnabled(true)
        }
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

    /**
     * Puts a surface on the display holding the app-target role, and does nothing when no display
     * holds it.
     */
    fun ensureAppScreenLaunched() {
        releaseStaleAppScreens()
        if (!sessionStateStore.isDualScreenEnabled()) return
        val displayId = displayAffinityHelper.appScreenDisplayId(_isRolesSwapped.value) ?: return
        if (appScreenHosts.hostFor(displayId) != null) return
        val options = displayAffinityHelper.getAppScreenLaunchOptions(_isRolesSwapped.value) ?: return
        val intent = Intent(activityContext, com.nendo.argosy.hardware.AppScreenActivity::class.java)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        Log.d(TAG, "Launching app screen on display $displayId")
        activityContext.startActivity(intent, options)
    }

    fun launchCompanionApp(packageName: String, pinnedScreenKey: String? = null) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scope.launch {
            if (pinnedScreenKey != null) {
                preferencesRepository.setAppDisplayTarget(packageName, pinnedScreenKey)
            }
            val options = displayAffinityHelper.getAppLaunchOptions(
                preferredScreenKey = pinnedScreenKey
                    ?: preferencesRepository.userPreferences.first().appDisplayTargets[packageName],
                rolesSwapped = _isRolesSwapped.value,
                occupiedDisplayId = emulatorDisplayId
            )
            if (options != null) activityContext.startActivity(intent, options)
            else activityContext.startActivity(intent)
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
        private const val EMULATOR_SETTLE_MS = 2_000L
        private const val EMULATOR_RELEASE_ATTEMPTS = 6
        private const val EMULATOR_RELEASE_INTERVAL_MS = 5_000L
        const val ACTION_WIZARD_STATE = "com.nendo.argosy.WIZARD_STATE"
        const val EXTRA_WIZARD_ACTIVE = "wizard_active"
        const val OVERLAY_MENU = "com.nendo.argosy.OVERLAY_MENU"
        const val OVERLAY_QUICK_MENU = "com.nendo.argosy.OVERLAY_QUICK_MENU"
        const val OVERLAY_QUICK_SETTINGS = "com.nendo.argosy.OVERLAY_QUICK_SETTINGS"
        private const val COMPANION_WATCHDOG_TIMEOUT_MS = 5000L
        private const val COMPANION_LAUNCH_WAIT_MS = 500L
        private const val DOCKED_RESYNC_DELAY_MS = 2000L
        private const val COMPANION_LAUNCH_VERIFY_MS = 8000L
        private const val MAX_COMPANION_LAUNCH_ATTEMPTS = 3
        private const val SWAP_DEBOUNCE_MS = 500L

        /**
         * How long a playback launch waits before directing focus to the player's display. Long
         * enough for a cold player window to be up and frontmost there, so the transient director
         * lands above it and hands focus down to it on finishing rather than to whatever was
         * behind.
         */
        private const val MEDIA_FOCUS_DIRECT_DELAY_MS = 600L
        private const val DEFAULT_SCREEN_OFF_TIMEOUT_MS = 60_000L
        private const val MIN_SCREEN_OFF_TIMEOUT_MS = 15_000L
        private const val MAX_SCREEN_OFF_TIMEOUT_MS = 30 * 60_000L

        /**
         * The dim ramp for the screen not showing a live playback: untouched for
         * [MEDIA_DIM_PARTIAL_DELAY_MS] it drops to [MEDIA_DIM_PARTIAL_BRIGHTNESS], still readable
         * at a glance but no longer competing with the film, and at [MEDIA_DIM_OFF_DELAY_MS] it
         * goes to [MEDIA_DIM_OFF_BRIGHTNESS]. A window-brightness zero is the panel's MINIMUM
         * backlight, not off (a couple of nits on measured hardware), so the windows applying the
         * off stage pair it with an opaque black cover; the display stays powered and wakes on any
         * input.
         */
        private const val MEDIA_DIM_PARTIAL_DELAY_MS = 30_000L
        private const val MEDIA_DIM_OFF_DELAY_MS = 60_000L
        private const val MEDIA_DIM_PARTIAL_BRIGHTNESS = 0.4f
        private const val MEDIA_DIM_OFF_BRIGHTNESS = 0f
        private const val MEDIA_DIM_COVER_STEP_MS = 250L

        /**
         * How long the black cover takes to fade out on wake, from fully opaque. The backlight's
         * return is the display controller's own ramp - rate configs the platform neither exposes
         * nor lets a window influence - so this cannot be derived and is tuned by eye: short
         * enough to clear slightly ahead of a typical slow-ramp climb, so the cover never makes
         * the wake feel later than the light.
         */
        private const val MEDIA_DIM_WAKE_FADE_MS = 800L
        private const val MEDIA_DIM_WAKE_FADE_STEP_MS = 33L

        /**
         * Temporary diagnostic channel for the playback dim ramp; filter with
         * `adb logcat -s MediaDimRamp`. Remove once the ramp is confirmed stable on device.
         */
        const val MEDIA_DIM_LOG_TAG = "MediaDimRamp"

        private const val MOTION_ACTIVITY_AXIS_THRESHOLD = 0.05f
        private val MOTION_ACTIVITY_AXES = intArrayOf(
            android.view.MotionEvent.AXIS_X,
            android.view.MotionEvent.AXIS_Y,
            android.view.MotionEvent.AXIS_Z,
            android.view.MotionEvent.AXIS_RZ,
            android.view.MotionEvent.AXIS_HAT_X,
            android.view.MotionEvent.AXIS_HAT_Y,
            android.view.MotionEvent.AXIS_LTRIGGER,
            android.view.MotionEvent.AXIS_RTRIGGER,
            android.view.MotionEvent.AXIS_BRAKE,
            android.view.MotionEvent.AXIS_GAS
        )

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
        eachCompanion { it.onHomeAppsChanged(homeApps.toList()) }
    }

    fun broadcastSessionCleared() {
        eachCompanion { it.onSessionEnded() }
    }

    // --- Registration ---

    private val blankPanelsSettingObserver =
        object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "Blank-panels-on-video-output setting changed, docked=${displayAffinityHelper.isDockedDark}")
                scheduleDockedResync()
            }
        }

    private var dockedResyncJob: Job? = null

    private fun scheduleDockedResync() {
        syncDockedState()
        dockedResyncJob?.cancel()
        dockedResyncJob = scope.launch {
            delay(DOCKED_RESYNC_DELAY_MS)
            syncDockedState()
        }
    }

    fun registerReceivers() {
        displayAffinityHelper.registerDisplayListener(displayListener)
        appContext.contentResolver.registerContentObserver(
            android.provider.Settings.System.getUriFor(
                com.nendo.argosy.util.DisplayAffinityHelper.BLANK_PANELS_ON_VIDEO_OUTPUT_SETTING
            ),
            false,
            blankPanelsSettingObserver
        )
        syncDockedState()
    }

    fun unregisterReceivers() {
        companionLaunchJob?.cancel()
        companionLaunchJob = null
        dockedResyncJob?.cancel()
        displayAffinityHelper.unregisterDisplayListener(displayListener)
        appContext.contentResolver.unregisterContentObserver(blankPanelsSettingObserver)
    }

    fun refocusSession() {
        if (!sessionStateStore.hasActiveSession()) return
        sessionRefocus?.invoke()
    }

    private fun refocusMain() {
        if (emulatorDisplayId == android.view.Display.DEFAULT_DISPLAY &&
            sessionStateStore.hasActiveSession()
        ) return
        val interactive = interactiveDisplayId()
        if (mediaPlaybackTracker.activePlayback.value != null) {
            val target = mediaPlayerDisplayId ?: mediaPlayerRelocationDisplayId()
            if (target == null || interactive == null || target == interactive) {
                val options = target?.let {
                    displayAffinityHelper.getActivityOptions(forEmulator = false, overrideDisplayId = it)
                }
                val launchContext = target?.let { displayAffinityHelper.displayContext(it) } ?: appContext
                com.nendo.argosy.ui.screens.player.PlayerActivity.raise(launchContext, options)
                return
            }
        }
        if (companionHoldsPrimary.value) {
            controlCompanion?.let {
                it.refocusSelf()
                return
            }
        }
        activityContext.startActivity(
            Intent(activityContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            },
            displayAffinityHelper.getActivityOptions(
                forEmulator = false,
                overrideDisplayId = android.view.Display.DEFAULT_DISPLAY
            )
        )
    }
}
