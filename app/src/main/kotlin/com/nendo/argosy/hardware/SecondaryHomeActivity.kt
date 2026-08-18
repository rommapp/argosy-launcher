package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.ui.theme.CustomFontFamilies
import com.nendo.argosy.ui.theme.CustomFontLoader
import com.nendo.argosy.ui.theme.ThemeState
import com.nendo.argosy.ui.theme.toThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.dualscreen.ShowcaseViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.parseSaveEntryDataList
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode
import com.nendo.argosy.ui.dualscreen.media.DualMediaRow
import com.nendo.argosy.ui.dualscreen.media.DualMediaViewModel
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SecondaryHomeActivity :
    ComponentActivity(),
    DualScreenManager.CompanionHost {

    private lateinit var dsm: DualScreenManager

    var currentScreen by mutableStateOf(CompanionScreen.HOME)
        private set
    var dualGameDetailViewModel: DualGameDetailViewModel? = null
        private set
    private var isScreenshotViewerOpen = false
    private var launchedExternalApp = false
    private var preSessionDetailGameId = -1L

    private var isInitialized by mutableStateOf(false)
    var isArgosyForeground by mutableStateOf(false)
        private set
    var isGameActive by mutableStateOf(false)
        private set
    private var isWizardActive by mutableStateOf(false)
    private var currentChannelName by mutableStateOf<String?>(null)
    private var isSaveDirty by mutableStateOf(false)
    private var isHardcore by mutableStateOf(false)
    var homeApps by mutableStateOf<List<String>>(emptyList())
        private set
    private var companionInGameState by mutableStateOf(CompanionInGameState())
    private var companionSessionTimer: CompanionSessionTimer? = null
    private var homeRestoreSettled = false

    private var confirmHoldJob: kotlinx.coroutines.Job? = null
    private var confirmHoldFired = false

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var dualHomeViewModel: DualHomeViewModel
    private var dualMediaViewModel by mutableStateOf<DualMediaViewModel?>(null)
    private var isMediaPanelVisible by mutableStateOf(false)
    private var mediaToggle by mutableStateOf<CompanionMediaToggle?>(null)
    private lateinit var stateManager: SecondaryHomeStateManager
    var isShowcaseRole by mutableStateOf(false)
        private set

    private val _showcaseState = MutableStateFlow(DualHomeShowcaseState())
    private val _showcaseViewMode = MutableStateFlow("CAROUSEL")
    private val _showcaseCollectionState = MutableStateFlow(DualCollectionShowcaseState())
    private val _showcaseGameDetailState = MutableStateFlow<DualGameDetailUpperState?>(null)
    private var showcaseViewModel: ShowcaseViewModel? = null

    var swapAB = false; private set
    var swapXY = false; private set
    var swapStartSelect = false; private set

    private var abIconsSwapped by mutableStateOf(false)
    private var xyIconsSwapped by mutableStateOf(false)
    private var startSelectSwapped by mutableStateOf(false)


    private lateinit var broadcasts: SecondaryHomeBroadcastHelper
    private lateinit var inputHandler: SecondaryHomeInputHandler
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSystemBarsWatchdog()

        // On cold boot the WindowInsetsController is not fully wired before decor attach,
        // so an immediate hideSystemUI() can be a no-op and the navbar stays visible until
        // the first focus-change event. Defer the first hide until attach guarantees the
        // controller is live.
        window.decorView.doOnAttach { hideSystemUI() }

        if (!SessionStateStore(applicationContext).isDualScreenEnabled()) {
            android.util.Log.d("SecondaryHome", "dualScreenEnabled=false, finishing")
            finish()
            return
        }

        if (releaseUnsupportedDisplay()) return

        val existing = DualScreenManagerHolder.instance
        if (existing != null) {
            dsm = existing
            initializeCompanion()
        } else if (!com.nendo.argosy.util.SecondaryHomeComponent.isDefaultHome(this)) {
            android.util.Log.i("SecondaryHome", "Respawned without a running Argosy and not default home, releasing secondary display")
            com.nendo.argosy.util.SecondaryHomeComponent.setEnabled(this, false)
            CompanionGuardService.stop(this)
            finish()
            return
        } else if (sessionHoldsThisDisplay()) {
            android.util.Log.i(
                "SecondaryHome",
                "Respawned behind a running session on this display, waiting for it to end"
            )
            awaitSessionEnd()
        } else {
            recoverThroughMainActivity()
        }

        setContent {
            // Collect the same ThemeState the primary display uses so cover-art
            // style, ui-scale, and other theme locals match across screens.
            // Activity isn't a Hilt entry point, so we read the prefs flow
            // directly through DSM rather than instantiating a second
            // ThemeViewModel (which would also spin up a duplicate
            // ambient-LED observer).
            // Keyed on isInitialized: dsm is resolved asynchronously after
            // setContent runs, so the collector has to (re)start once dsm
            // is available. Without the key produceState fires once, sees
            // dsm uninitialized, and never re-runs -- live theme updates
            // from the primary screen never reach the secondary.
            // dsm is resolved asynchronously after setContent runs, so the
            // collector has to start once isInitialized flips true. Without
            // the LaunchedEffect key, live theme updates from the primary
            // screen would never reach the secondary.
            val themeState = remember { mutableStateOf(ThemeState()) }
            val customFonts = remember { mutableStateOf(CustomFontFamilies()) }
            LaunchedEffect(isInitialized) {
                if (!isInitialized) return@LaunchedEffect
                dsm.preferencesRepository.userPreferences.collect { prefs ->
                    themeState.value = prefs.toThemeState()
                    customFonts.value = resolveCustomFonts(prefs.displayFontPath, prefs.bodyFontPath)
                }
            }
            SecondaryHomeTheme(themeState = themeState.value, fonts = customFonts.value) {
                if (!isInitialized) return@SecondaryHomeTheme
                val scrapingArtwork by dsm.imageCacheManager.progress.collectAsState()
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalABIconsSwapped provides abIconsSwapped,
                    LocalXYIconsSwapped provides xyIconsSwapped,
                    LocalSwapStartSelect provides startSelectSwapped,
                    com.nendo.argosy.ui.components.LocalArtworkScraping provides
                        scrapingArtwork.isProcessing
                ) {
                    if (isShowcaseRole) {
                        ShowcaseRoleContent(
                            isInitialized = isInitialized,
                            isArgosyForeground = isArgosyForeground,
                            isGameActive = isGameActive,
                            isWizardActive = isWizardActive,
                            showcaseViewModel = showcaseViewModel!!,
                            viewModel = viewModel,
                            homeApps = homeApps,
                            showcaseState = _showcaseState,
                            showcaseViewMode = _showcaseViewMode,
                            collectionShowcaseState = _showcaseCollectionState,
                            gameDetailState = _showcaseGameDetailState,
                            syncConflictState = dsm.dualSyncOverlay,
                            syncConflictFocusIndex = dsm.dualSyncOverlayFocusIndex,
                            onAppClick = ::launchApp,
                            dualMediaViewModel = dualMediaViewModel,
                            isMediaPanelVisible = isMediaPanelVisible
                        )
                    } else {
                        SecondaryHomeContent(
                            isInitialized = isInitialized,
                            isArgosyForeground = isArgosyForeground,
                            isGameActive = isGameActive,
                            isWizardActive = isWizardActive,
                            companionInGameState = companionInGameState,
                            companionSessionTimer = companionSessionTimer,
                            homeApps = homeApps,
                            viewModel = viewModel,
                            dualHomeViewModel = dualHomeViewModel,
                            currentScreen = currentScreen,
                            dualGameDetailViewModel = dualGameDetailViewModel,
                            onAppClick = ::launchApp,
                            onGameSelected = ::selectGame,
                            onCollectionsClick = {
                                dualHomeViewModel.enterCollections()
                                broadcasts.broadcastViewModeChange()
                                broadcasts.broadcastCollectionFocused()
                            },
                            onLibraryToggle = ::handleLibraryToggle,
                            onViewAllClick = ::handleViewAllClick,
                            onCollectionTapped = ::handleCollectionTapped,
                            onGridGameTapped = ::handleGridGameTapped,
                            onLetterClick = {
                                dualHomeViewModel.jumpToSection(it)
                                broadcasts.broadcastLibraryGameSelection()
                            },
                            onFilterOptionTapped = {
                                dualHomeViewModel.moveFilterFocus(
                                    it - dualHomeViewModel.uiState.value.filterFocusedIndex
                                )
                                dualHomeViewModel.confirmFilter()
                            },
                            onFilterCategoryTapped = {
                                dualHomeViewModel.setFilterCategory(it)
                            },
                            onSearchQueryChange = { query ->
                                dualHomeViewModel.updateSearchQuery(query)
                            },
                            onDetailBack = ::returnToHome,
                            onOptionAction = { vm, option ->
                                inputHandler.handleOption(vm, option)
                            },
                            onScreenshotViewed = { index ->
                                isScreenshotViewerOpen = true
                                broadcasts.broadcastScreenshotSelected(index)
                            },
                            onDimTapped = { broadcasts.broadcastRefocusUpper() },
                            onCustomGridActivate = {
                                inputHandler.handleDualHomeInput(
                                    com.nendo.argosy.ui.input.GamepadEvent.Confirm
                                )
                            },
                            onTabChanged = { panel ->
                                companionInGameState = companionInGameState.copy(
                                    currentPanel = panel
                                )
                            },
                            onQuickSave = { dsm.sessionQuickActions?.quickSave() },
                            onQuickLoad = { dsm.sessionQuickActions?.quickLoad() },
                            onScreenshot = { dsm.sessionQuickActions?.screenshot() },
                            dualMediaViewModel = dualMediaViewModel,
                            isMediaPanelVisible = isMediaPanelVisible,
                            mediaToggle = mediaToggle,
                            onMediaToggle = { dsm.toggleCompanionMediaView() },
                            onMediaRowTapped = { index -> dualMediaViewModel?.focusRow(index) },
                            onMediaRowConfirmed = ::playFocusedMediaRow
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (releaseUnsupportedDisplay()) return
        hideSystemUI()
        if (!::dsm.isInitialized) return
        val currentDsm = DualScreenManagerHolder.instance
        if (currentDsm != null && dsm !== currentDsm) {
            android.util.Log.w("SecondaryHome", "DSM stale, reconnecting to new instance")
            dsm = currentDsm
            initializeCompanion()
        }
        dualHomeViewModel.stopDrawerForwarding()
        launchedExternalApp = false
        syncFromSessionStore()
        broadcasts.broadcastCompanionResumed()
        endSessionIfEmulatorGone()
    }

    private fun syncFromSessionStore() {
        val store = dsm.sessionStateStore
        store.setForeignAppOnSecondary(false)
        isGameActive = store.hasActiveSession()
        isHardcore = store.isHardcore()
        currentChannelName = store.getChannelName()
        isSaveDirty = store.isSaveDirty()
    }

    /**
     * Ends a session whose emulator has left this display. The companion getting its display back is
     * the one event that says a game running on it is over, and it is the only one there is: nothing
     * else observes an emulator the launcher does not own.
     */
    private fun endSessionIfEmulatorGone() {
        if (!isGameActive || dsm.isLaunchingGame) return
        val emulatorDisplay = dsm.emulatorDisplayId ?: return
        val ownDisplay = window.decorView.display?.displayId ?: return
        if (emulatorDisplay != ownDisplay) return
        if (dsm.isEmulatorStillOnScreen(this)) return
        android.util.Log.d("SecondaryHome", "Companion resumed and the emulator is gone, ending session")
        dsm.emulatorDisplayId = null
        dsm.playSessionTracker.endSessionInBackground()
        dsm.broadcastSessionCleared()
    }

    override fun onStop() {
        super.onStop()
        if (::broadcasts.isInitialized) broadcasts.broadcastCompanionPaused()
    }

    override fun finishCompanion() {
        runOnUiThread { finish() }
    }

    override fun onDestroy() {
        if (::dsm.isInitialized) dsm.companionHost = null
        displayListener?.let {
            getSystemService(DisplayManager::class.java)
                .unregisterDisplayListener(it)
        }
        displayListener = null
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val result = super.dispatchTouchEvent(event)
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            if (isGameActive && ::dsm.isInitialized) {
                window.decorView.post { dsm.refocusSession() }
            } else if (isShowcaseRole) {
                window.decorView.post { broadcasts.broadcastRefocusUpper() }
            } else if (
                dualHomeViewModel.forwardingMode.value ==
                    com.nendo.argosy.ui.dualscreen.home.ForwardingMode.BACKGROUND &&
                currentScreen == CompanionScreen.HOME
            ) {
                window.decorView.post { broadcasts.broadcastRefocusUpper() }
            }
        }
        return result
    }

    /**
     * Gamepad keys are taken before the view hierarchy sees them, exactly as the primary activity
     * does it.
     *
     * onKeyDown is the fallback Android calls only once every view has declined the key, so a single
     * focused composable anywhere on this display silently owns the d-pad - after closing the app
     * drawer its grid held focus, and eight presses went into walking out of it before the carousel
     * saw one. The launcher decides selection itself, so the window has no business consulting focus
     * first; typing is the sole exception, and it says so.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isCompanionTextEntryActive()) return super.dispatchKeyEvent(event)
        when (event.action) {
            android.view.KeyEvent.ACTION_DOWN ->
                if (handleGamepadKeyDown(event.keyCode, event)) return true
            android.view.KeyEvent.ACTION_UP ->
                if (handleGamepadKeyUp(event.keyCode, event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isCompanionTextEntryActive(): Boolean =
        ::dualHomeViewModel.isInitialized && dualHomeViewModel.uiState.value.isTextEntryActive

    private fun handleGamepadKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (::dsm.isInitialized && !dsm.claimInput(event)) return true
        if (event.repeatCount == 0) {
            val conflictEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
            if (conflictEvent != null) {
                val conflictResult = inputHandler.handleSyncConflictInput(conflictEvent)
                if (conflictResult.handled) return true
                val saveResult = inputHandler.handleSaveConflictInput(conflictEvent)
                if (saveResult.handled) return true
            }
        }
        if (isShowcaseRole) {
            if (!isArgosyForeground && event.repeatCount == 0) {
                val gamepadEvent = mapKeycodeToGamepadEvent(
                    keyCode, swapAB, swapXY, swapStartSelect
                )
                if (gamepadEvent != null) {
                    val vm = showcaseViewModel
                    if (vm != null && vm.isModalActive() &&
                        vm.handleModalGamepadEvent(gamepadEvent)
                    ) return true
                }
            }
            return false
        }
        val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
        if (gamepadEvent == com.nendo.argosy.ui.input.GamepadEvent.Confirm && deferConfirm()) {
            if (event.repeatCount == 0) beginConfirmHold()
            return true
        }
        if (event.repeatCount == 0 && gamepadEvent != null) {
            val result = inputHandler.routeInput(
                gamepadEvent, true, isGameActive, currentScreen
            )
            if (::dsm.isInitialized) dsm.inputFeedback.play(gamepadEvent, result)
            if (result.handled) return true
        }
        return false
    }

    private fun handleGamepadKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
        if (gamepadEvent == com.nendo.argosy.ui.input.GamepadEvent.Confirm && confirmHoldJob != null) {
            endConfirmHold()
            return true
        }
        return false
    }

    /**
     * Only the curated grid needs a held A, and deferring the press everywhere else would put a
     * delay on every confirm on this screen. The companion dispatches on key-down, so the wait has
     * to be introduced deliberately and kept to where it earns its cost.
     */
    /**
     * Whether confirm should wait to see if it becomes a hold. Only the surfaces that do something
     * with a hold defer it, because deferring costs every press its immediacy: the curated grid,
     * where a hold picks a tile up, and the library, where it opens the game's menu.
     */
    private fun deferConfirm(): Boolean {
        if (isShowcaseRole || currentScreen != CompanionScreen.HOME) return false
        if (isMediaPanelVisible) return false
        if (!::dualHomeViewModel.isInitialized) return false
        if (viewModel.uiState.value.isDrawerOpen) return true
        val state = dualHomeViewModel.uiState.value
        return when (state.viewMode) {
            DualHomeViewMode.CAROUSEL ->
                state.layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID &&
                    !state.showTilePicker && !state.showTileMenu
            DualHomeViewMode.LIBRARY_GRID ->
                !state.showFilterOverlay && !state.showLibraryMenu &&
                    state.collectionPickerGameId == null
            else -> false
        }
    }

    private fun beginConfirmHold() {
        confirmHoldFired = false
        confirmHoldJob?.cancel()
        confirmHoldJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(CONFIRM_HOLD_MS)
            confirmHoldFired = true
            inputHandler.routeInput(
                com.nendo.argosy.ui.input.GamepadEvent.LongConfirm,
                true,
                isGameActive,
                currentScreen
            )
        }
    }

    /**
     * Completes a press that [beginConfirmHold] started, and does nothing otherwise.
     *
     * Whether a press is deferred is decided again when the button comes up, and by then the press
     * itself may have changed the answer - opening the drawer makes the release deferrable when the
     * push was not. Without the guard the release invents a second Confirm the user never gave, and
     * it lands on whatever the first one just opened.
     */
    private fun endConfirmHold() {
        val job = confirmHoldJob ?: return
        job.cancel()
        confirmHoldJob = null
        if (confirmHoldFired) return
        inputHandler.routeInput(
            com.nendo.argosy.ui.input.GamepadEvent.Confirm,
            true,
            isGameActive,
            currentScreen
        )
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        isArgosyForeground = isForeground
        if (isForeground && isGameActive && !sessionKeepsItsScreen()) onSessionEnded()
        isInitialized = true
    }

    /**
     * Whether the game keeps the screen it was on as the launcher UI comes forward. Only one on the
     * other display does: with a single pair of panels, the launcher arriving is the game losing the
     * one it had. The launcher UI lives on the default display, so a game anywhere else is untouched
     * by it - which is also the exemption the launcher side applies before it tears a session down,
     * and the two have to agree or the companion drops the in-game panel out from under a live game.
     */
    private fun sessionKeepsItsScreen(): Boolean {
        if (!dsm.hasLiveSession()) return false
        if (isShowcaseRole) return true
        return dsm.emulatorDisplayId?.let { it != Display.DEFAULT_DISPLAY } ?: false
    }

    override fun onWizardStateChanged(isActive: Boolean) {
        isWizardActive = isActive
    }

    override fun onSaveDirtyChanged(isDirty: Boolean) {
        isSaveDirty = isDirty; companionInGameState = companionInGameState.copy(isDirty = isDirty)
    }

    override fun onSessionActionsChanged(available: Boolean) {
        runOnUiThread {
            companionInGameState = companionInGameState.copy(quickActionsAvailable = available)
        }
    }

    override fun onHasQuickSaveChanged(hasQuickSave: Boolean) {
        runOnUiThread {
            companionInGameState = companionInGameState.copy(hasQuickSave = hasQuickSave)
        }
    }

    override fun onSessionStarted(
        gameId: Long, isHardcore: Boolean, channelName: String?
    ) {
        preSessionDetailGameId = if (currentScreen == CompanionScreen.GAME_DETAIL) {
            dsm.sessionStateStore.getDetailGameId()
        } else -1L
        isGameActive = true
        if (!dsm.isExternalDisplay) {
            viewModel.companionFocusAppBar(homeApps.size)
        }
        this.isHardcore = isHardcore
        currentChannelName = channelName
        isSaveDirty = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        dsm.sessionStateStore.setCompanionScreen("HOME")
        loadCompanionGameData(gameId)
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = CompanionSessionTimer().also {
            it.start(applicationContext)
        }
        isInitialized = true
    }

    override fun onSessionEnded() {
        isGameActive = false
        isHardcore = false
        currentChannelName = null
        isSaveDirty = false
        companionInGameState = CompanionInGameState()
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
        val savedGameId = preSessionDetailGameId
        preSessionDetailGameId = -1L
        if (savedGameId > 0) {
            selectGame(savedGameId)
        } else {
            dsm.sessionStateStore.setCompanionScreen("HOME")
        }
        isInitialized = true
    }

    override fun onHomeAppsChanged(apps: List<String>) {
        homeApps = apps; viewModel.setHomeApps(apps)
    }

    override fun onLibraryRefresh() {
        viewModel.refresh(); dualHomeViewModel.refresh()
    }

    /**
     * Drops back to Home before reloading. Recents, last-played and the game-detail overlay are
     * per-account, so a detail screen left open across a switch would be rendering rows the
     * incoming account does not own.
     */
    override fun onAccountSwitched() {
        returnToHome()
        onLibraryRefresh()
    }

    override fun onOverlayRequested(eventName: String) {
        if (!isShowcaseRole) return
        when (eventName) {
            "drawer" -> viewModel.openDrawer()
        }
    }

    override fun onRoleSwapped(isSwapped: Boolean) {
        isShowcaseRole = isSwapped
    }

    override fun onOverlayClosed() {
        dualHomeViewModel.stopDrawerForwarding()
    }

    override fun onBackgroundForward() {
        dualHomeViewModel.startBackgroundForwarding()
    }

    override fun onForwardKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        swapAB: Boolean,
        swapXY: Boolean,
        swapStartSelect: Boolean
    ) {
        val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect) ?: return
        if (gamepadEvent == com.nendo.argosy.ui.input.GamepadEvent.Confirm &&
            (confirmHoldJob != null || deferConfirm())
        ) {
            when (action) {
                android.view.KeyEvent.ACTION_DOWN -> if (repeatCount == 0) beginConfirmHold()
                android.view.KeyEvent.ACTION_UP -> endConfirmHold()
            }
            return
        }
        if (action == android.view.KeyEvent.ACTION_DOWN && repeatCount == 0) {
            inputHandler.routeInput(gamepadEvent, true, isGameActive, currentScreen)
        }
    }

    override fun refocusSelf() = startActivity(
        Intent(this, SecondaryHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )

    override fun onGameDetailOpened(gameId: Long) {}

    override fun onGameDetailClosed() {}

    override fun onScreenshotSelected(index: Int) {}

    override fun onScreenshotCleared() {}

    override fun onModalResult(
        dismissed: Boolean,
        type: String?,
        value: Int,
        statusSelected: String?,
        selectedIndex: Int,
        collectionToggleId: Long,
        collectionCreateName: String?
    ) {
        val vm = dualGameDetailViewModel ?: return
        if (dismissed) {
            when (vm.activeModal.value) {
                ActiveModal.COLLECTION -> vm.dismissCollectionModal()
                ActiveModal.STEAM_INSTALL -> vm.dismissSteamInstallModal()
                ActiveModal.EMULATOR -> vm.dismissPicker()
                else -> vm.dismissPicker()
            }
            refocusSelf()
            return
        }
        when (type) {
            ActiveModal.RATING.name, ActiveModal.DIFFICULTY.name -> {
                vm.setPickerValue(value)
                vm.confirmPicker()
                refocusSelf()
            }
            ActiveModal.STATUS.name -> {
                val statusVal = statusSelected ?: return
                vm.setStatusSelection(statusVal)
                vm.confirmPicker()
                refocusSelf()
            }
            ActiveModal.EMULATOR.name -> {
                if (selectedIndex >= 0) vm.confirmEmulatorByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.CORE.name -> {
                if (selectedIndex >= 0) vm.confirmCoreByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.SAVE_PATH.name -> {
                if (selectedIndex >= 0) vm.confirmSavePathByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.DISPLAY_TARGET.name -> {
                if (selectedIndex >= 0) vm.confirmDisplayTargetByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.MEMORY_CARD.name -> {
                if (selectedIndex >= 0) vm.confirmMemoryCardByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.VARIANT_PICKER.name -> {
                if (selectedIndex >= 0) vm.confirmVariantByIndex(selectedIndex)
                else vm.dismissPicker()
                refocusSelf()
            }
            ActiveModal.STEAM_INSTALL.name -> {
                vm.dismissSteamInstallModal()
                vm.loadGame(vm.uiState.value.gameId)
                refocusSelf()
            }
            ActiveModal.SAVE_NAME.name -> refocusSelf()
            ActiveModal.COLLECTION.name -> {
                if (collectionCreateName != null) {
                    vm.createAndAddToCollection(collectionCreateName)
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(100)
                        broadcasts.broadcastCollectionModalOpen(vm)
                    }
                    return
                }
                if (collectionToggleId > 0) vm.toggleCollection(collectionToggleId)
            }
        }
    }

    override fun onDirectActionResult(type: String, gameId: Long) {
        if (type == "UNHIDE_DONE") {
            dualHomeViewModel.refresh()
            return
        }
        if (type == "STEAM_INSTALL_DONE") {
            dualHomeViewModel.refresh()
            if (gameId > 0) dualGameDetailViewModel?.loadGame(gameId)
            return
        }
        val vm = dualGameDetailViewModel ?: return
        when (type) {
            "DELETE_START" -> { if (gameId > 0) vm.onDeleteStarted() }
            "REFRESH_DONE", "DELETE_DONE" -> { if (gameId > 0) vm.loadGame(gameId) }
            "HIDE_DONE" -> returnToHome()
            "SAVE_SWITCH_DONE", "SAVE_RESTORE_DONE", "SAVE_CREATE_DONE", "SAVE_LOCK_DONE" -> { }
        }
    }

    override fun onSaveDataReceived(json: String, activeChannel: String?, activeTimestamp: Long?, syncing: Boolean) {
        val vm = dualGameDetailViewModel ?: return
        try {
            val entries = parseSaveEntryDataList(json)
            vm.loadUnifiedSaves(entries, activeChannel, activeTimestamp)
            vm.setSyncing(syncing)
        } catch (e: Exception) {
            android.util.Log.e("SecondaryHome", "Failed to parse save data", e)
        }
    }

    override fun onSavesSyncDone() {
        dualGameDetailViewModel?.setSyncing(false)
    }

    override fun onDownloadCompleted(gameId: Long) {
        onLibraryRefresh()
        if (gameId > 0 && _showcaseState.value.gameId == gameId) {
            _showcaseState.value = _showcaseState.value.copy(isDownloaded = true)
        }
    }

    /**
     * Keeps the showcase on whatever the grid has under its cursor.
     *
     * The one-shot broadcasts fire at moments chosen for the carousel, and tiles arrive from the
     * database after the section does, so on a cold start the carousel's game would win the race and
     * stay. Following the cursor means the upper screen is right whenever it settles, including on
     * first load and on the way back from a game's details.
     */
    private fun observeCustomGridSelection() {
        lifecycleScope.launch {
            dualHomeViewModel.uiState
                .map {
                    Triple(it.layoutKind, it.customGrid.focusedTile?.target, it.customGrid.tiles.size)
                }
                .distinctUntilChanged()
                .collect { (layout, _, _) ->
                    if (layout != com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) return@collect
                    if (currentScreen != CompanionScreen.HOME) return@collect
                    broadcasts.broadcastCurrentGameSelection()
                }
        }
    }

    fun returnToHome() {
        isScreenshotViewerOpen = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        dsm.sessionStateStore.setCompanionScreen("HOME")
        broadcasts.broadcastGameDetailClosed()
        broadcasts.broadcastCurrentGameSelection()
        dualHomeViewModel.refresh()
    }

    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    private suspend fun resolveCustomFonts(displayPath: String?, bodyPath: String?): CustomFontFamilies =
        withContext(Dispatchers.IO) {
            CustomFontFamilies(
                display = resolveFontFamily(displayPath),
                body = resolveFontFamily(bodyPath)
            )
        }

    private fun resolveFontFamily(path: String?): FontFamily? {
        if (path == null) return null
        fontFamilyCache[path]?.let { return it }
        val family = CustomFontLoader.loadFamily(path)
        if (family != null) fontFamilyCache[path] = family
        return family
    }

    private fun initializeCompanion() {
        registerDisplayListener()
        initializeDependencies()
        loadInitialState()
        dsm.companionHost = this
        lifecycleScope.launch { dsm.dualScreenShowcase.collect { _showcaseState.value = it } }
        lifecycleScope.launch { dsm.dualViewMode.collect { _showcaseViewMode.value = it } }
        lifecycleScope.launch { dsm.dualCollectionShowcase.collect { _showcaseCollectionState.value = it } }
        lifecycleScope.launch { dsm.dualGameDetailState.collect { _showcaseGameDetailState.value = it } }
        lifecycleScope.launch {
            dsm.companionMediaVisible.collect { visible ->
                isMediaPanelVisible = visible
                dualMediaViewModel?.setActive(visible)
                refreshMediaToggle()
            }
        }
        lifecycleScope.launch { dsm.mediaPlayback.collect { refreshMediaToggle() } }
        lifecycleScope.launch { dsm.mediaSignedIn.collect { refreshMediaToggle() } }
        lifecycleScope.launch {
            dsm.preferencesRepository.userPreferences.collect { prefs ->
                applyInputSwapState(stateManager.inputSwapStateFrom(prefs))
            }
        }
    }

    /**
     * The app-bar button exists only where it leads somewhere: a signed-in media account, or a
     * playback already running. Without either, the panel behind it would be empty and the button
     * would be promising something the device cannot give.
     */
    private fun refreshMediaToggle() {
        val playback = dsm.mediaPlayback.value
        mediaToggle = if (playback == null && !dsm.mediaSignedIn.value) {
            null
        } else {
            CompanionMediaToggle(
                showingMedia = isMediaPanelVisible,
                isPlaying = playback?.isPlaying == true
            )
        }
    }

    fun confirmFocusedMediaRow() {
        val index = dualMediaViewModel?.uiState?.value?.focusedRowIndex ?: return
        playFocusedMediaRow(index)
    }

    private fun playFocusedMediaRow(index: Int) {
        val vm = dualMediaViewModel ?: return
        vm.focusRow(index)
        val row = vm.uiState.value.rows.getOrNull(index) as? DualMediaRow.Item ?: return
        dsm.playMediaItem(row.item.itemId)
    }

    private fun applyInputSwapState(state: SecondaryHomeStateManager.InputSwapState) {
        swapAB = state.swapAB
        swapXY = state.swapXY
        swapStartSelect = state.swapStartSelect
        abIconsSwapped = state.abIconsSwapped
        xyIconsSwapped = state.xyIconsSwapped
        startSelectSwapped = state.startSelectSwapped
    }

    private fun initializeDependencies() {
        val gameRepository = dsm.gameRepository
        val platformRepository = dsm.platformRepository
        val collectionRepository = dsm.collectionRepository
        val affinityHelper = dsm.displayAffinityHelper

        viewModel = SecondaryHomeViewModel(
            gameRepository = gameRepository, platformRepository = platformRepository,
            appsRepository = AppsRepository(applicationContext),
            preferencesRepository = null,
            displayAffinityHelper = affinityHelper,
            downloadManager = null, context = applicationContext,
            syncPreferencesRepository = dsm.syncPreferencesRepository
        )
        dualHomeViewModel = DualHomeViewModel(
            gameRepository = gameRepository, platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            downloadQueueRepository = dsm.downloadQueueRepository,
            displayAffinityHelper = affinityHelper,
            context = applicationContext,
            steamContentManager = dsm.steamContentManager,
            preferencesRepository = dsm.preferencesRepository,
            repairImageCacheUseCase = dsm.repairImageCacheUseCase,
            downloadFileStatusRepository = dsm.downloadFileStatusRepository,
            gradientExtractionDelegate = dsm.gradientExtractionDelegate,
            getPinnedCollectionsUseCase = dsm.getPinnedCollectionsUseCase,
            getGamesForPinnedCollectionUseCase = dsm.getGamesForPinnedCollectionUseCase,
            advanceCollectionFocusUseCase = dsm.advanceCollectionFocusUseCase,
            prepareCollectionQueueUseCase = dsm.prepareCollectionQueueUseCase,
            sessionStateStore = dsm.sessionStateStore,
            homeTileRepository = dsm.homeTileRepository,
            homeTilePromptQueue = dsm.homeTilePromptQueue,
            appsRepository = dsm.appsRepository,
            syncPreferencesRepository = dsm.syncPreferencesRepository
        )
        dualHomeViewModel.observeHomeTiles()
        dualHomeViewModel.observeTilePrompts()
        dualMediaViewModel = DualMediaViewModel(
            mediaRepository = dsm.mediaRepository,
            playback = dsm.mediaPlayback
        )
        observeCustomGridSelection()
        broadcasts = SecondaryHomeBroadcastHelper(
            dsm = dsm, dualHomeViewModel = dualHomeViewModel,
            secondaryHomeViewModel = { viewModel }
        )
        dualHomeViewModel.onRestoreComplete = {
            homeRestoreSettled = true
            if (currentScreen == CompanionScreen.HOME) {
                broadcasts.broadcastCurrentGameSelection()
            }
        }
        stateManager = SecondaryHomeStateManager(
            context = applicationContext, gameRepository = gameRepository,
            activeSaveRepository = dsm.activeSaveRepository,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            emulatorConfigDao = dsm.emulatorConfigDao,
            downloadQueueRepository = dsm.downloadQueueRepository,
            steamRepository = dsm.steamRepository,
            configureEmulatorUseCase = dsm.configureEmulatorUseCase,
            builtinCoreResolver = dsm.builtinCoreResolver,
            saveHandlerRegistry = dsm.saveHandlerRegistry,
            steamContentManager = dsm.steamContentManager,
            displayAffinityHelper = affinityHelper,
            downloadFileStatusRepository = dsm.downloadFileStatusRepository,
            preferencesRepository = dsm.preferencesRepository,
            resolveGameEmulatorContext = dsm.resolveGameEmulatorContext
        )

        inputHandler = SecondaryHomeInputHandler(
            viewModel = viewModel,
            dualHomeViewModel = dualHomeViewModel,
            broadcasts = broadcasts,
            homeApps = { homeApps },
            dualGameDetailViewModel = { dualGameDetailViewModel },
            isScreenshotViewerOpen = { isScreenshotViewerOpen },
            setScreenshotViewerOpen = { isScreenshotViewerOpen = it },
            onSelectGame = ::selectGame,
            onReturnToHome = ::returnToHome,
            onLaunchApp = ::launchApp,
            onLaunchAppOnOtherDisplay = ::launchAppOnOtherDisplay,
            onRefocusSelf = ::refocusSelf,
            context = applicationContext,
            lifecycleLaunch = { block -> lifecycleScope.launch { block() } },
            dualMediaViewModel = { dualMediaViewModel },
            onConfirmMediaRow = ::confirmFocusedMediaRow
        )
        inputHandler.setDrawerAppLauncher { intent, options ->
            if (intent != null) {
                if (options != null) startActivity(intent, options)
                else startActivity(intent)
            }
        }

        showcaseViewModel = ShowcaseViewModel(
            detailState = _showcaseGameDetailState,
            broadcasts = broadcasts,
            isControlActive = { isArgosyForeground }
        )
    }

    private fun loadInitialState() {
        val initial = stateManager.loadInitialState(viewModel, dualHomeViewModel)

        isShowcaseRole = initial.isShowcaseRole
        isArgosyForeground = initial.isArgosyForeground
        isGameActive = initial.isGameActive
        homeRestoreSettled = !initial.restoreScheduled
        isWizardActive = dsm.sessionStateStore.isWizardActive() ||
            !dsm.sessionStateStore.isFirstRunComplete()
        currentChannelName = initial.currentChannelName
        isSaveDirty = initial.isSaveDirty
        homeApps = initial.homeApps
        // primaryColor is now sourced live from the user-prefs flow via
        // themeState in setContent; the one-shot snapshot from initial state
        // would freeze the secondary screen's accent at start time and never
        // reflect Settings changes made on the primary.
        isHardcore = initial.isHardcore

        if (initial.isGameActive && initial.activeGameId > 0) {
            loadCompanionGameData(initial.activeGameId)
            companionSessionTimer = CompanionSessionTimer().also {
                it.start(applicationContext)
            }
        }

        if (initial.restoredDetailViewModel != null) {
            dualGameDetailViewModel = initial.restoredDetailViewModel
            currentScreen = initial.restoredScreen!!
            broadcasts.broadcastGameDetailOpened(initial.restoredDetailGameId)
        }

        val inputSwap = stateManager.loadInputSwapPreferences()
        swapAB = inputSwap.swapAB
        swapXY = inputSwap.swapXY
        swapStartSelect = inputSwap.swapStartSelect
        abIconsSwapped = inputSwap.abIconsSwapped
        xyIconsSwapped = inputSwap.xyIconsSwapped
        startSelectSwapped = inputSwap.startSelectSwapped

        isInitialized = true
    }

    private fun loadCompanionGameData(gameId: Long) {
        lifecycleScope.launch {
            companionInGameState = stateManager.loadCompanionGameData(gameId).withLiveQuickActionState(
                quickActionsAvailable = dsm.sessionQuickActions != null,
                hasQuickSave = dsm.companionHasQuickSave
            )
        }
    }

    private fun handleLibraryToggle() {
        dualHomeViewModel.toggleLibraryGrid {
            broadcasts.broadcastViewModeChange()
            val state = dualHomeViewModel.uiState.value
            if (state.viewMode == DualHomeViewMode.LIBRARY_GRID)
                broadcasts.broadcastLibraryGameSelection()
            else
                broadcasts.broadcastCurrentGameSelection()
        }
    }

    private fun handleViewAllClick() {
        val onReady = {
            broadcasts.broadcastViewModeChange(); broadcasts.broadcastLibraryGameSelection()
        }
        val platformId = dualHomeViewModel.uiState.value.currentPlatformId
        if (platformId != null) dualHomeViewModel.enterLibraryGridForPlatform(platformId, onReady)
        else dualHomeViewModel.enterLibraryGrid(onReady)
    }

    private fun handleCollectionTapped(index: Int) {
        val items = dualHomeViewModel.uiState.value.collectionItems
        val item = items.getOrNull(index)
        if (item is com.nendo.argosy.ui.dualscreen.home.DualCollectionListItem.Collection) {
            dualHomeViewModel.enterCollectionGames(item.id)
            broadcasts.broadcastViewModeChange()
        }
    }

    private fun handleGridGameTapped(index: Int) {
        val s = dualHomeViewModel.uiState.value
        when (s.viewMode) {
            DualHomeViewMode.COLLECTION_GAMES -> {
                dualHomeViewModel.moveCollectionGamesFocus(index - s.collectionGamesFocusedIndex)
                broadcasts.broadcastCollectionGameSelection()
                s.collectionGames.getOrNull(index)?.let { selectGame(it.id) }
            }
            DualHomeViewMode.LIBRARY_GRID -> {
                dualHomeViewModel.setLibraryFocusIndex(index)
                broadcasts.broadcastLibraryGameSelection()
                s.libraryGames.getOrNull(index)?.let { selectGame(it.id) }
            }
            else -> {}
        }
    }

    private fun launchApp(packageName: String) = launchAppInternal(packageName, null)

    private fun launchAppOnOtherDisplay(packageName: String) = launchAppInternal(
        packageName,
        android.app.ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
    )

    private fun launchAppInternal(packageName: String, options: Bundle?) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchedExternalApp = true
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (options != null) {
                    startActivity(launchIntent, options)
                } else {
                    if (::dsm.isInitialized) dsm.sessionStateStore.setForeignAppOnSecondary(true)
                    startActivity(launchIntent)
                }
            }
        } catch (_: Exception) {
            launchedExternalApp = false
        }
    }

    /**
     * Whether the session written to disk is still being played on this display.
     *
     * The companion is pinned as this display's home, so the OS recreates it the moment the launcher
     * process dies - underneath the game, which knows nothing about any of it. Every in-memory signal
     * that a game is running died with the process; the record on disk and the emulator's own windows
     * are what is left, and together they say a restart happened rather than a player coming back.
     * Silence from usage access answers false, which is the old behaviour: recover and let the
     * launcher come forward.
     */
    private fun sessionHoldsThisDisplay(): Boolean {
        val store = SessionStateStore(applicationContext)
        if (!store.hasActiveSession()) return false
        val emulatorPackage = store.getEmulatorPackage() ?: return false
        val emulatorDisplay = store.getEmulatorDisplayId()
        val ownDisplay = try {
            window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId
        } catch (_: Exception) { null }
        if (emulatorDisplay != null && ownDisplay != null && emulatorDisplay != ownDisplay) return false
        return PermissionHelper().isPackageOnScreenOrRecent(this, emulatorPackage)
    }

    /**
     * Holds the companion still until the game on this display is done with it, then recovers.
     *
     * Polled rather than driven by this activity's own lifecycle: a companion recreated underneath a
     * game may never be resumed, and one the OS chose to bring forward is resumed while the game is
     * still there, so neither event answers on its own. The poll runs only in this one state - no
     * manager, a session on this display - and ends the moment it recovers.
     */
    private fun awaitSessionEnd() {
        lifecycleScope.launch {
            while (sessionHoldsThisDisplay()) {
                kotlinx.coroutines.delay(SESSION_END_POLL_MS)
            }
            recoverThroughMainActivity()
        }
    }

    /**
     * Brings the launcher process back up and attaches to the manager it creates. Only ever called
     * when nothing is being played on this display: it puts an activity on the default display and
     * finishes this one on failure, and the OS respawning a pinned home activity turns that into a
     * loop if a game is what it keeps landing behind.
     */
    private fun recoverThroughMainActivity() {
        android.util.Log.w("SecondaryHome", "DSM not available, launching MainActivity")
        startActivity(
            Intent(this, com.nendo.argosy.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle()
        )
        lifecycleScope.launch {
            var attempts = 0
            while (DualScreenManagerHolder.instance == null && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            val holder = DualScreenManagerHolder.instance
            if (holder == null) {
                android.util.Log.e("SecondaryHome", "DSM still null after waiting, finishing")
                finish()
                return@launch
            }
            dsm = holder
            initializeCompanion()
            syncFromSessionStore()
            dsm.onCompanionResumed()
            endSessionIfEmulatorGone()
        }
    }

    /**
     * The companion is only ever launched onto the secondary display, so landing on the default
     * one means the OS refused that placement - it does not permit a home activity there. Left
     * running it would cover the launcher and be relaunched in a loop, so release the component
     * and drop to single-screen instead. Returns true when the activity has been finished.
     */
    private fun releaseUnsupportedDisplay(): Boolean {
        val ownDisplayId = try {
            window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId
        } catch (_: Exception) { return false }
        if (ownDisplayId != android.view.Display.DEFAULT_DISPLAY) return false

        android.util.Log.w(
            "SecondaryHome",
            "Companion placed on the default display; this OS does not allow a secondary home"
        )
        SessionStateStore(applicationContext).setSecondaryDisplayUsable(false)
        DualScreenManagerHolder.instance?.fallbackToSingleScreen(persistent = true)
        com.nendo.argosy.util.SecondaryHomeComponent.setEnabled(this, false)
        CompanionGuardService.stop(this)
        finish()
        return true
    }

    private fun registerDisplayListener() {
        val displayManager = getSystemService(DisplayManager::class.java)
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {
                val myDisplayId = try {
                    windowManager.defaultDisplay.displayId
                } catch (_: Exception) { -1 }
                if (displayId == myDisplayId ||
                    displayManager.displays.size <= 1
                ) {
                    android.util.Log.w(
                        "SecondaryHome",
                        "Display removed, finishing companion activity"
                    )
                    finish()
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
    }

    private fun selectGame(gameId: Long) {
        val vm = stateManager.createGameDetailViewModel()
        vm.loadGame(gameId)
        dualGameDetailViewModel = vm
        currentScreen = CompanionScreen.GAME_DETAIL
        dsm.sessionStateStore.setCompanionScreen("GAME_DETAIL", gameId)
        broadcasts.broadcastGameDetailOpened(gameId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun installSystemBarsWatchdog() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                // Call hide() inline so it lands within the same insets dispatch - posting
                // delays the request until the next UI tick, by which point the controller
                // may again think the bars belong on screen.
                WindowInsetsControllerCompat(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }
            insets
        }
        // Force a synchronous insets pass so the listener fires immediately after install
        // rather than waiting for the next layout-driven dispatch.
        androidx.core.view.ViewCompat.requestApplyInsets(window.decorView)
    }

}

private const val CONFIRM_HOLD_MS = 500L
private const val SESSION_END_POLL_MS = 3000L
