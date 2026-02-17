package com.nendo.argosy.hardware

import android.content.Intent
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.local.DatabaseFactory
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SecondaryHomeActivity :
    ComponentActivity(),
    SecondaryHomeBroadcastReceiverManager.ReceiverHost {

    override var currentScreen by mutableStateOf(CompanionScreen.HOME)
        private set
    override var dualGameDetailViewModel: DualGameDetailViewModel? = null
        private set
    private var isScreenshotViewerOpen = false
    private var launchedExternalApp = false

    private var isInitialized by mutableStateOf(false)
    override var isArgosyForeground by mutableStateOf(false)
        private set
    override var isGameActive by mutableStateOf(false)
        private set
    private var currentChannelName by mutableStateOf<String?>(null)
    private var isSaveDirty by mutableStateOf(false)
    private var isHardcore by mutableStateOf(false)
    override var homeApps by mutableStateOf<List<String>>(emptyList())
        private set
    private var primaryColor by mutableStateOf<Int?>(null)

    private var companionInGameState by mutableStateOf(CompanionInGameState())
    private var companionSessionTimer: CompanionSessionTimer? = null

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var dualHomeViewModel: DualHomeViewModel
    private lateinit var stateManager: SecondaryHomeStateManager
    override var useDualScreenMode by mutableStateOf(false)
        private set
    override var isShowcaseRole by mutableStateOf(false)
        private set

    private val _showcaseState = MutableStateFlow(DualHomeShowcaseState())
    private val _showcaseViewMode = MutableStateFlow("CAROUSEL")
    private val _showcaseCollectionState = MutableStateFlow(DualCollectionShowcaseState())
    private val _showcaseGameDetailState = MutableStateFlow<DualGameDetailUpperState?>(null)

    override var swapAB = false; private set
    override var swapXY = false; private set
    override var swapStartSelect = false; private set

    private var abIconsSwapped by mutableStateOf(false)
    private var xyIconsSwapped by mutableStateOf(false)
    private var startSelectSwapped by mutableStateOf(false)

    private var dualScreenInputFocus = "AUTO"

    private lateinit var broadcasts: SecondaryHomeBroadcastHelper
    private lateinit var inputHandler: SecondaryHomeInputHandler
    private lateinit var receiverManager: SecondaryHomeBroadcastReceiverManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeDependencies()
        loadInitialState()
        registerReceivers()

        setContent {
            SecondaryHomeTheme(primaryColor = primaryColor) {
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
                            showcaseState = _showcaseState,
                            showcaseViewMode = _showcaseViewMode,
                            collectionShowcaseState = _showcaseCollectionState,
                            gameDetailState = _showcaseGameDetailState
                        )
                    } else {
                        SecondaryHomeContent(
                            isInitialized = isInitialized,
                            isArgosyForeground = isArgosyForeground,
                            isGameActive = isGameActive,
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
                                dualHomeViewModel.jumpToLetter(it)
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
        launchedExternalApp = false
        val store = stateManager.sessionStateStore
        isGameActive = store.hasActiveSession()
        isHardcore = store.isHardcore()
        currentChannelName = store.getChannelName()
        isSaveDirty = store.isSaveDirty()
        broadcasts.broadcastCompanionResumed()
    }

    override fun onStop() {
        super.onStop()
        broadcasts.broadcastCompanionPaused()
    }

    override fun onDestroy() {
        receiverManager.unregisterAll()
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
        if (isShowcaseRole) return super.onKeyDown(keyCode, event)
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
        isArgosyForeground = isForeground; isInitialized = true
    }

    override fun onSaveDirtyChanged(isDirty: Boolean) {
        isSaveDirty = isDirty; companionInGameState = companionInGameState.copy(isDirty = isDirty)
    }

    override fun onSessionStarted(
        gameId: Long, isHardcore: Boolean, channelName: String?
    ) {
        isGameActive = true
        viewModel.companionFocusAppBar(homeApps.size)
        this.isHardcore = isHardcore
        currentChannelName = channelName
        isSaveDirty = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        stateManager.sessionStateStore.setCompanionScreen("HOME")
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
        stateManager.sessionStateStore.setCompanionScreen("HOME")
        companionInGameState = CompanionInGameState()
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
        isInitialized = true
    }

    override fun onHomeAppsChanged(apps: List<String>) {
        homeApps = apps; viewModel.setHomeApps(apps)
    }

    override fun onLibraryRefresh() {
        viewModel.refresh(); dualHomeViewModel.refresh()
    }

    override fun refocusSelf() = startActivity(
        Intent(this, SecondaryHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )

    override fun returnToHome() {
        isScreenshotViewerOpen = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        stateManager.sessionStateStore.setCompanionScreen("HOME")
        broadcasts.broadcastGameDetailClosed()
        dualHomeViewModel.refresh()
    }

    override fun lifecycleLaunch(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }

    private fun initializeDependencies() {
        val database = DatabaseFactory.getDatabase(applicationContext)
        val gameDao = database.gameDao()
        val platformRepository = PlatformRepository(database.platformDao())
        val collectionRepository = CollectionRepository(database.collectionDao())
        val emulatorConfigDao = database.emulatorConfigDao()
        val affinityHelper = DisplayAffinityHelper(applicationContext)

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
            downloadQueueDao = database.downloadQueueDao(),
            displayAffinityHelper = affinityHelper,
            context = applicationContext
        )
        broadcasts = SecondaryHomeBroadcastHelper(
            context = this, dualHomeViewModel = dualHomeViewModel,
            secondaryHomeViewModel = { viewModel }
        )
        stateManager = SecondaryHomeStateManager(
            context = applicationContext, gameDao = gameDao,
            platformRepository = platformRepository, collectionRepository = collectionRepository,
            emulatorConfigDao = emulatorConfigDao,
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
    }

    private fun loadInitialState() {
        val initial = stateManager.loadInitialState(viewModel, dualHomeViewModel)

        useDualScreenMode = initial.useDualScreenMode
        isShowcaseRole = initial.isShowcaseRole
        isArgosyForeground = initial.isArgosyForeground
        isGameActive = initial.isGameActive
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

    private fun registerReceivers() {
        receiverManager = SecondaryHomeBroadcastReceiverManager(
            context = this,
            viewModel = viewModel,
            dualHomeViewModel = dualHomeViewModel,
            broadcasts = broadcasts,
            inputHandler = inputHandler,
            host = this,
            showcaseState = _showcaseState,
            showcaseViewMode = _showcaseViewMode,
            showcaseCollectionState = _showcaseCollectionState,
            showcaseGameDetailState = _showcaseGameDetailState
        )
        receiverManager.registerAll()
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
        stateManager.sessionStateStore.setCompanionScreen("GAME_DETAIL", gameId)
        broadcasts.broadcastGameDetailOpened(gameId)
    }

}
