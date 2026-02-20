package com.nendo.argosy.hardware

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
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
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var primaryColor by mutableStateOf<Int?>(null)

    private var companionInGameState by mutableStateOf(CompanionInGameState())
    private var companionSessionTimer: CompanionSessionTimer? = null

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var dualHomeViewModel: DualHomeViewModel
    private lateinit var stateManager: SecondaryHomeStateManager
    var useDualScreenMode by mutableStateOf(false)
        private set
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

    private var dualScreenInputFocus = "AUTO"

    private lateinit var broadcasts: SecondaryHomeBroadcastHelper
    private lateinit var inputHandler: SecondaryHomeInputHandler
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existing = DualScreenManagerHolder.instance
        if (existing != null) {
            dsm = existing
            initializeCompanion()
        } else {
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
                dsm.onCompanionResumed()
            }
        }

        setContent {
            SecondaryHomeTheme(primaryColor = primaryColor) {
                if (!isInitialized) return@SecondaryHomeTheme
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalABIconsSwapped provides abIconsSwapped,
                    LocalXYIconsSwapped provides xyIconsSwapped,
                    LocalSwapStartSelect provides startSelectSwapped
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
                            onAppClick = ::launchApp
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
                            useDualScreenMode = useDualScreenMode,
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
                            onDetailBack = ::returnToHome,
                            onOptionAction = { vm, option ->
                                inputHandler.handleOption(vm, option)
                            },
                            onScreenshotViewed = { index ->
                                isScreenshotViewerOpen = true
                                broadcasts.broadcastScreenshotSelected(index)
                            },
                            onDimTapped = { broadcasts.broadcastRefocusUpper() },
                            onTabChanged = { panel ->
                                companionInGameState = companionInGameState.copy(
                                    currentPanel = panel
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::dsm.isInitialized) return
        val currentDsm = DualScreenManagerHolder.instance
        if (currentDsm != null && dsm !== currentDsm) {
            android.util.Log.w("SecondaryHome", "DSM stale, reconnecting to new instance")
            dsm = currentDsm
            initializeCompanion()
        }
        dualHomeViewModel.stopDrawerForwarding()
        launchedExternalApp = false
        val store = dsm.sessionStateStore
        isGameActive = store.hasActiveSession()
        isHardcore = store.isHardcore()
        currentChannelName = store.getChannelName()
        isSaveDirty = store.isSaveDirty()
        broadcasts.broadcastCompanionResumed()
    }

    override fun onStop() {
        super.onStop()
        if (::broadcasts.isInitialized) broadcasts.broadcastCompanionPaused()
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
        if (!isShowcaseRole &&
            event.action == android.view.MotionEvent.ACTION_UP &&
            dualHomeViewModel.forwardingMode.value ==
                com.nendo.argosy.ui.dualscreen.home.ForwardingMode.BACKGROUND
        ) {
            window.decorView.post {
                if (currentScreen == CompanionScreen.HOME) broadcasts.broadcastRefocusUpper()
            }
        }
        return result
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
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
            return super.onKeyDown(keyCode, event)
        }
        if (dualScreenInputFocus == "TOP") return super.onKeyDown(keyCode, event)

        if (event.repeatCount == 0) {
            val gamepadEvent = mapKeycodeToGamepadEvent(
                keyCode, swapAB, swapXY, swapStartSelect
            )
            if (gamepadEvent != null) {
                val result = inputHandler.routeInput(
                    gamepadEvent, useDualScreenMode, isArgosyForeground,
                    isGameActive, currentScreen
                )
                if (result.handled) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        isArgosyForeground = isForeground
        if (isForeground && isGameActive &&
            !dsm.sessionStateStore.hasActiveSession()
        ) {
            onSessionEnded()
        }
        isInitialized = true
    }

    override fun onWizardStateChanged(isActive: Boolean) {
        isWizardActive = isActive
    }

    override fun onSaveDirtyChanged(isDirty: Boolean) {
        isSaveDirty = isDirty; companionInGameState = companionInGameState.copy(isDirty = isDirty)
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
        if (savedGameId > 0 && useDualScreenMode) {
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

    override fun onForwardKey(keyCode: Int, swapAB: Boolean, swapXY: Boolean, swapStartSelect: Boolean) {
        val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect) ?: return
        inputHandler.routeInput(gamepadEvent, useDualScreenMode, isArgosyForeground, isGameActive, currentScreen)
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
                ActiveModal.UPDATES_DLC -> vm.dismissUpdatesModal()
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
        val vm = dualGameDetailViewModel ?: return
        when (type) {
            "REFRESH_DONE", "DELETE_DONE" -> { if (gameId > 0) vm.loadGame(gameId) }
            "HIDE_DONE" -> returnToHome()
            "SAVE_SWITCH_DONE", "SAVE_RESTORE_DONE", "SAVE_CREATE_DONE", "SAVE_LOCK_DONE" -> { }
        }
    }

    override fun onSaveDataReceived(json: String, activeChannel: String?, activeTimestamp: Long?) {
        val vm = dualGameDetailViewModel ?: return
        try {
            val entries = parseSaveEntryDataList(json)
            vm.loadUnifiedSaves(entries, activeChannel, activeTimestamp)
        } catch (e: Exception) {
            android.util.Log.e("SecondaryHome", "Failed to parse save data", e)
        }
    }

    override fun onDownloadCompleted(gameId: Long) {
        onLibraryRefresh()
        if (gameId > 0 && _showcaseState.value.gameId == gameId) {
            _showcaseState.value = _showcaseState.value.copy(isDownloaded = true)
        }
    }

    fun returnToHome() {
        isScreenshotViewerOpen = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        dsm.sessionStateStore.setCompanionScreen("HOME")
        broadcasts.broadcastGameDetailClosed()
        dualHomeViewModel.refresh()
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
    }

    private fun initializeDependencies() {
        val gameDao = dsm.gameDao
        val platformRepository = dsm.platformRepository
        val collectionRepository = dsm.collectionRepository
        val affinityHelper = dsm.displayAffinityHelper

        viewModel = SecondaryHomeViewModel(
            gameDao = gameDao, platformRepository = platformRepository,
            appsRepository = AppsRepository(applicationContext),
            preferencesRepository = null,
            displayAffinityHelper = affinityHelper,
            downloadManager = null, context = applicationContext
        )
        dualHomeViewModel = DualHomeViewModel(
            gameDao = gameDao, platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            downloadQueueDao = dsm.downloadQueueDao,
            displayAffinityHelper = affinityHelper,
            context = applicationContext
        )
        broadcasts = SecondaryHomeBroadcastHelper(
            dsm = dsm, dualHomeViewModel = dualHomeViewModel,
            secondaryHomeViewModel = { viewModel }
        )
        stateManager = SecondaryHomeStateManager(
            context = applicationContext, gameDao = gameDao,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            emulatorConfigDao = dsm.emulatorConfigDao,
            gameFileDao = dsm.gameFileDao,
            downloadQueueDao = dsm.downloadQueueDao,
            displayAffinityHelper = affinityHelper
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
            onPersistCarouselPosition = {
                stateManager.persistCarouselPosition(dualHomeViewModel)
            },
            context = applicationContext,
            lifecycleLaunch = { block -> lifecycleScope.launch { block() } }
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

        useDualScreenMode = initial.useDualScreenMode
        isShowcaseRole = initial.isShowcaseRole
        isArgosyForeground = initial.isArgosyForeground
        isGameActive = initial.isGameActive
        isWizardActive = dsm.sessionStateStore.isWizardActive() ||
            !dsm.sessionStateStore.isFirstRunComplete()
        currentChannelName = initial.currentChannelName
        isSaveDirty = initial.isSaveDirty
        homeApps = initial.homeApps
        primaryColor = initial.primaryColor
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
        dualScreenInputFocus = inputSwap.dualScreenInputFocus
        abIconsSwapped = inputSwap.abIconsSwapped
        xyIconsSwapped = inputSwap.xyIconsSwapped
        startSelectSwapped = inputSwap.startSelectSwapped

        isInitialized = true
    }

    private fun loadCompanionGameData(gameId: Long) {
        lifecycleScope.launch {
            companionInGameState = stateManager.loadCompanionGameData(gameId)
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
            }
            DualHomeViewMode.LIBRARY_GRID -> {
                dualHomeViewModel.moveLibraryFocus(index - s.libraryFocusedIndex)
                broadcasts.broadcastLibraryGameSelection()
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
                if (options != null) startActivity(launchIntent, options)
                else startActivity(launchIntent)
            }
        } catch (_: Exception) {
            launchedExternalApp = false
        }
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
        if (!useDualScreenMode) {
            val (intent, options) = dualHomeViewModel.getGameDetailIntent(gameId)
            if (options != null) startActivity(intent, options)
            else startActivity(intent)
            return
        }
        val vm = stateManager.createGameDetailViewModel()
        vm.loadGame(gameId)
        dualGameDetailViewModel = vm
        currentScreen = CompanionScreen.GAME_DETAIL
        dsm.sessionStateStore.setCompanionScreen("GAME_DETAIL", gameId)
        broadcasts.broadcastGameDetailOpened(gameId)
    }

}
