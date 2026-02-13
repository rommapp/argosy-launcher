package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.local.DatabaseFactory
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailLowerScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailTab
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.GameDetailOption
import com.nendo.argosy.ui.dualscreen.gamedetail.SaveFocusColumn
import com.nendo.argosy.ui.dualscreen.home.DualHomeFocusZone
import com.nendo.argosy.ui.dualscreen.home.DualHomeLowerContent
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Secondary display home activity running in the :companion process.
 * Registered as SECONDARY_HOME to be the default launcher on secondary displays.
 *
 * CRITICAL: This activity runs in a separate process and must NEVER:
 * - Launch MainActivity or any main process components
 * - Use Hilt-injected dependencies
 * - Reference any singletons from the main process
 */
class SecondaryHomeActivity : ComponentActivity() {

    companion object {
        const val ACTION_ARGOSY_FOREGROUND = "com.nendo.argosy.FOREGROUND"
        const val ACTION_ARGOSY_BACKGROUND = "com.nendo.argosy.BACKGROUND"
        const val ACTION_SAVE_STATE_CHANGED = "com.nendo.argosy.SAVE_STATE_CHANGED"
        const val ACTION_SESSION_CHANGED = "com.nendo.argosy.SESSION_CHANGED"
        const val ACTION_HOME_APPS_CHANGED = "com.nendo.argosy.HOME_APPS_CHANGED"
        const val ACTION_LIBRARY_REFRESH = "com.nendo.argosy.LIBRARY_REFRESH"
        const val ACTION_DOWNLOAD_COMPLETED = "com.nendo.argosy.DOWNLOAD_COMPLETED"
        const val EXTRA_IS_DIRTY = "is_dirty"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_IS_HARDCORE = "is_hardcore"
        const val EXTRA_HOME_APPS = "home_apps"
    }

    enum class CompanionScreen { HOME, GAME_DETAIL }

    private var currentScreen by mutableStateOf(CompanionScreen.HOME)
    private var dualGameDetailViewModel: DualGameDetailViewModel? = null
    private var isScreenshotViewerOpen = false
    private var launchedExternalApp = false

    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var collectionDao: CollectionDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao

    // Show splash until we receive first foreground state broadcast
    private var isInitialized by mutableStateOf(false)
    // Default to false - only show library when we positively know Argosy is in foreground
    private var isArgosyForeground by mutableStateOf(false)
    private var isGameActive by mutableStateOf(false)
    private var currentChannelName by mutableStateOf<String?>(null)
    private var isSaveDirty by mutableStateOf(false)
    private var homeApps by mutableStateOf<List<String>>(emptyList())
    private var primaryColor by mutableStateOf<Int?>(null)

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var dualHomeViewModel: DualHomeViewModel
    private lateinit var sessionStateStore: SessionStateStore
    private var useDualScreenMode by mutableStateOf(true)

    // Input swap preferences (read from SessionStateStore, written by main process)
    private var swapAB = false
    private var swapXY = false
    private var swapStartSelect = false

    // Computed icon swap values for CompositionLocal (layout detection xor raw prefs)
    private var abIconsSwapped by mutableStateOf(false)
    private var xyIconsSwapped by mutableStateOf(false)
    private var startSelectSwapped by mutableStateOf(false)

    private fun loadInitialState() {
        sessionStateStore = SessionStateStore(applicationContext)

        // Read initial state from SharedPreferences
        isArgosyForeground = sessionStateStore.isArgosyForeground()
        isGameActive = sessionStateStore.hasActiveSession()
        currentChannelName = sessionStateStore.getChannelName()
        isSaveDirty = sessionStateStore.isSaveDirty()
        homeApps = sessionStateStore.getHomeApps().toList()
        primaryColor = sessionStateStore.getPrimaryColor()

        // Update ViewModel with home apps
        if (homeApps.isNotEmpty()) {
            viewModel.setHomeApps(homeApps)
        }

        loadInputSwapPreferences()
        isInitialized = true
    }

    private fun loadInputSwapPreferences() {
        swapAB = sessionStateStore.getSwapAB()
        swapXY = sessionStateStore.getSwapXY()
        swapStartSelect = sessionStateStore.getSwapStartSelect()

        val isNintendoLayout = ControllerDetector.detectFromActiveGamepad().layout == DetectedLayout.NINTENDO
        abIconsSwapped = isNintendoLayout xor swapAB
        xyIconsSwapped = isNintendoLayout xor swapXY
        startSelectSwapped = swapStartSelect
    }


    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ARGOSY_FOREGROUND -> {
                    isArgosyForeground = true
                    isInitialized = true
                }
                ACTION_ARGOSY_BACKGROUND -> {
                    isArgosyForeground = false
                    isInitialized = true
                }
            }
        }
    }

    private val saveStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SAVE_STATE_CHANGED) {
                isSaveDirty = intent.getBooleanExtra(EXTRA_IS_DIRTY, false)
            }
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SESSION_CHANGED) {
                val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)
                if (gameId > 0) {
                    isGameActive = true
                    currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                    isSaveDirty = false
                    currentScreen = CompanionScreen.HOME
                    dualGameDetailViewModel = null
                } else {
                    isGameActive = false
                    currentChannelName = null
                    isSaveDirty = false
                }
                isInitialized = true
            }
        }
    }

    private val homeAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HOME_APPS_CHANGED) {
                val apps = intent.getStringArrayListExtra(EXTRA_HOME_APPS)?.toList() ?: emptyList()
                homeApps = apps
                // Also update the ViewModel for library mode
                viewModel.setHomeApps(apps)
            }
        }
    }

    private val libraryRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LIBRARY_REFRESH, ACTION_DOWNLOAD_COMPLETED -> {
                    viewModel.refresh()
                }
            }
        }
    }

    private val refocusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DualScreenBroadcasts.ACTION_REFOCUS_LOWER) {
                val refocusIntent = Intent(this@SecondaryHomeActivity, SecondaryHomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(refocusIntent)
            }
        }
    }

    private val modalResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_MODAL_RESULT) return
            val vm = dualGameDetailViewModel ?: return

            val dismissed = intent.getBooleanExtra(
                DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, false
            )
            if (dismissed) {
                when (vm.activeModal.value) {
                    ActiveModal.COLLECTION -> vm.dismissCollectionModal()
                    ActiveModal.EMULATOR -> vm.dismissPicker()
                    else -> vm.dismissPicker()
                }
                refocusSelf()
                return
            }

            val type = intent.getStringExtra(
                DualScreenBroadcasts.EXTRA_MODAL_TYPE
            )
            when (type) {
                ActiveModal.RATING.name, ActiveModal.DIFFICULTY.name -> {
                    val value = intent.getIntExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_VALUE, 0
                    )
                    vm.setPickerValue(value)
                    vm.confirmPicker()
                    refocusSelf()
                }
                ActiveModal.STATUS.name -> {
                    val value = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED
                    ) ?: return
                    vm.setStatusSelection(value)
                    vm.confirmPicker()
                    refocusSelf()
                }
                ActiveModal.EMULATOR.name -> {
                    val index = intent.getIntExtra(
                        DualScreenBroadcasts.EXTRA_SELECTED_INDEX, -1
                    )
                    if (index >= 0) vm.confirmEmulatorByIndex(index)
                    else vm.dismissPicker()
                    refocusSelf()
                }
                ActiveModal.SAVE_NAME.name -> {
                    // Save data arrives via ACTION_SAVE_DATA broadcast
                    refocusSelf()
                }
                ActiveModal.COLLECTION.name -> {
                    val createName = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_COLLECTION_CREATE_NAME
                    )
                    if (createName != null) {
                        vm.createAndAddToCollection(createName)
                        lifecycleScope.launch {
                            delay(100)
                            broadcastCollectionModalOpen(vm)
                        }
                        return
                    }
                    val toggleId = intent.getLongExtra(
                        DualScreenBroadcasts.EXTRA_COLLECTION_TOGGLE_ID, -1
                    )
                    if (toggleId > 0) {
                        vm.toggleCollection(toggleId)
                    }
                }
            }
        }
    }

    private val saveDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_SAVE_DATA) return
            val vm = dualGameDetailViewModel ?: return
            val json = intent.getStringExtra(
                DualScreenBroadcasts.EXTRA_SAVE_DATA_JSON
            ) ?: return
            val entries = com.nendo.argosy.ui.dualscreen.gamedetail
                .parseSaveEntryDataList(json)
            val activeChannel = intent.getStringExtra(
                DualScreenBroadcasts.EXTRA_ACTIVE_CHANNEL
            )
            val activeTimestamp = if (intent.hasExtra(
                    DualScreenBroadcasts.EXTRA_ACTIVE_SAVE_TIMESTAMP
                )) {
                intent.getLongExtra(
                    DualScreenBroadcasts.EXTRA_ACTIVE_SAVE_TIMESTAMP, 0
                )
            } else null
            vm.loadUnifiedSaves(entries, activeChannel, activeTimestamp)
        }
    }

    private val directActionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_DIRECT_ACTION) return
            val type = intent.getStringExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE)
            val vm = dualGameDetailViewModel ?: return
            val actionGameId = intent.getLongExtra(
                DualScreenBroadcasts.EXTRA_GAME_ID, -1
            )
            when (type) {
                "REFRESH_DONE" -> {
                    if (actionGameId > 0) vm.loadGame(actionGameId)
                }
                "DELETE_DONE" -> {
                    if (actionGameId > 0) vm.loadGame(actionGameId)
                }
                "HIDE_DONE" -> returnToHome()
                "SAVE_SWITCH_DONE", "SAVE_RESTORE_DONE",
                "SAVE_CREATE_DONE", "SAVE_LOCK_DONE" -> {
                    // Save data arrives via ACTION_SAVE_DATA broadcast
                }
            }
        }
    }

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
                    SecondaryHomeContent(
                        isInitialized = isInitialized,
                        isArgosyForeground = isArgosyForeground,
                        isGameActive = isGameActive,
                        channelName = currentChannelName,
                        isSaveDirty = isSaveDirty,
                        homeApps = homeApps,
                        viewModel = viewModel,
                        dualHomeViewModel = dualHomeViewModel,
                        useDualScreenMode = useDualScreenMode,
                        currentScreen = currentScreen,
                        dualGameDetailViewModel = dualGameDetailViewModel,
                        onAppClick = { packageName -> launchApp(packageName) },
                        onGameSelected = { gameId -> selectGame(gameId) },
                        onDetailBack = { returnToHome() },
                        onOptionAction = { vm, option ->
                            handleOption(vm, option)
                        },
                        onScreenshotViewed = { index ->
                            broadcastScreenshotSelected(index)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchedExternalApp = false
        isGameActive = sessionStateStore.hasActiveSession()
        currentChannelName = sessionStateStore.getChannelName()
        isSaveDirty = sessionStateStore.isSaveDirty()
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COMPANION_RESUMED)
                .setPackage(packageName)
        )
    }

    override fun onStop() {
        super.onStop()
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COMPANION_PAUSED)
                .setPackage(packageName)
        )
    }

    private fun initializeDependencies() {
        val database = DatabaseFactory.getDatabase(applicationContext)
        gameDao = database.gameDao()
        platformDao = database.platformDao()
        collectionDao = database.collectionDao()
        emulatorConfigDao = database.emulatorConfigDao()
        val appsRepository = AppsRepository(applicationContext)
        val displayAffinityHelper = DisplayAffinityHelper(applicationContext)

        viewModel = SecondaryHomeViewModel(
            gameDao = gameDao,
            platformDao = platformDao,
            appsRepository = appsRepository,
            preferencesRepository = null, // Don't use DataStore in companion process - data comes via broadcasts
            displayAffinityHelper = displayAffinityHelper,
            downloadManager = null,
            context = applicationContext
        )

        dualHomeViewModel = DualHomeViewModel(
            gameDao = gameDao,
            platformDao = platformDao,
            displayAffinityHelper = displayAffinityHelper,
            context = applicationContext
        )
    }

    private fun registerReceivers() {
        val foregroundFilter = IntentFilter().apply {
            addAction(ACTION_ARGOSY_FOREGROUND)
            addAction(ACTION_ARGOSY_BACKGROUND)
        }
        val saveStateFilter = IntentFilter(ACTION_SAVE_STATE_CHANGED)
        val sessionFilter = IntentFilter(ACTION_SESSION_CHANGED)
        val homeAppsFilter = IntentFilter(ACTION_HOME_APPS_CHANGED)
        val libraryRefreshFilter = IntentFilter().apply {
            addAction(ACTION_LIBRARY_REFRESH)
            addAction(ACTION_DOWNLOAD_COMPLETED)
        }
        val refocusFilter = IntentFilter(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
        val modalResultFilter = IntentFilter(DualScreenBroadcasts.ACTION_MODAL_RESULT)
        val directActionFilter = IntentFilter(DualScreenBroadcasts.ACTION_DIRECT_ACTION)
        val saveDataFilter = IntentFilter(DualScreenBroadcasts.ACTION_SAVE_DATA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(foregroundReceiver, foregroundFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(saveStateReceiver, saveStateFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(sessionReceiver, sessionFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(homeAppsReceiver, homeAppsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(libraryRefreshReceiver, libraryRefreshFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(refocusReceiver, refocusFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(modalResultReceiver, modalResultFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(directActionResultReceiver, directActionFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(saveDataReceiver, saveDataFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(foregroundReceiver, foregroundFilter)
            registerReceiver(saveStateReceiver, saveStateFilter)
            registerReceiver(sessionReceiver, sessionFilter)
            registerReceiver(homeAppsReceiver, homeAppsFilter)
            registerReceiver(libraryRefreshReceiver, libraryRefreshFilter)
            registerReceiver(refocusReceiver, refocusFilter)
            registerReceiver(modalResultReceiver, modalResultFilter)
            registerReceiver(directActionResultReceiver, directActionFilter)
            registerReceiver(saveDataReceiver, saveDataFilter)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchedExternalApp = true
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
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

        val displayAffinityHelper = DisplayAffinityHelper(applicationContext)
        val vm = DualGameDetailViewModel(
            gameDao = gameDao,
            platformDao = platformDao,
            collectionDao = collectionDao,
            emulatorConfigDao = emulatorConfigDao,
            displayAffinityHelper = displayAffinityHelper,
            context = applicationContext
        )
        vm.loadGame(gameId)
        dualGameDetailViewModel = vm
        currentScreen = CompanionScreen.GAME_DETAIL
        broadcastGameDetailOpened(gameId)
    }

    private fun returnToHome() {
        isScreenshotViewerOpen = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        broadcastGameDetailClosed()
        dualHomeViewModel.refresh()
    }

    private fun broadcastGameDetailOpened(gameId: Long) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
            }
        )
    }

    private fun broadcastGameDetailClosed() {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED)
                .setPackage(packageName)
        )
    }

    private fun broadcastScreenshotSelected(index: Int) {
        isScreenshotViewerOpen = true
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, index)
            }
        )
    }

    private fun broadcastScreenshotCleared() {
        isScreenshotViewerOpen = false
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR)
                .setPackage(packageName)
        )
    }

    private fun broadcastModalState(
        vm: DualGameDetailViewModel,
        modal: ActiveModal
    ) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE, modal.name)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_VALUE,
                    vm.ratingPickerValue.value
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED,
                    vm.statusPickerValue.value
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_STATUS_CURRENT,
                    vm.uiState.value.status
                )
            }
        )
    }

    private fun broadcastModalClose() {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, true)
            }
        )
    }

    private fun broadcastEmulatorModalOpen(
        emulators: List<com.nendo.argosy.data.emulator.InstalledEmulator>,
        currentName: String?
    ) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.EMULATOR.name
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_NAMES,
                    ArrayList(emulators.map { it.def.displayName })
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_VERSIONS,
                    ArrayList(emulators.map { it.versionName ?: "" })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_CURRENT,
                    currentName
                )
            }
        )
    }

    private fun broadcastCollectionModalOpen(
        vm: DualGameDetailViewModel
    ) {
        val items = vm.collectionItems.value
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.COLLECTION.name
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_IDS,
                    items.map { it.id }.toLongArray()
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_NAMES,
                    ArrayList(items.map { it.name })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_CHECKED,
                    items.map { it.isInCollection }.toBooleanArray()
                )
            }
        )
    }

    private fun broadcastDirectAction(type: String, gameId: Long) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
            }
        )
    }

    private fun launchGame(gameId: Long) {
        val (intent, options) = dualHomeViewModel.getLaunchIntent(gameId)
        if (options != null) {
            startActivity(intent, options)
        } else {
            startActivity(intent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            android.util.Log.d("SecondaryInput", "keyCode=$keyCode (${android.view.KeyEvent.keyCodeToString(keyCode)}), swapAB=$swapAB, swapXY=$swapXY")
            val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
            if (gamepadEvent != null) {
                val result = if (useDualScreenMode && isArgosyForeground && !isGameActive) {
                    when (currentScreen) {
                        CompanionScreen.HOME -> handleDualHomeInput(gamepadEvent)
                        CompanionScreen.GAME_DETAIL -> handleDualDetailInput(gamepadEvent)
                    }
                } else {
                    handleGridInput(gamepadEvent)
                }
                if (result.handled) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleDualHomeInput(event: GamepadEvent): InputResult {
        val state = dualHomeViewModel.uiState.value
        val inAppBar = state.focusZone == DualHomeFocusZone.APP_BAR

        return when (event) {
            GamepadEvent.Left -> {
                if (inAppBar) dualHomeViewModel.selectPreviousApp()
                else dualHomeViewModel.selectPrevious()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                if (inAppBar) dualHomeViewModel.selectNextApp(homeApps.size)
                else dualHomeViewModel.selectNext()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                if (!inAppBar && homeApps.isNotEmpty()) {
                    dualHomeViewModel.focusAppBar(homeApps.size)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.Up -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.PrevSection -> {
                if (inAppBar) dualHomeViewModel.focusCarousel()
                dualHomeViewModel.previousSection()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                if (inAppBar) dualHomeViewModel.focusCarousel()
                dualHomeViewModel.nextSection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (inAppBar) {
                    val packageName = homeApps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        launchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else {
                    val game = state.selectedGame
                    if (game != null && game.isPlayable) {
                        launchGame(game.id)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                }
            }
            GamepadEvent.ContextMenu -> {
                if (inAppBar) return InputResult.UNHANDLED
                val game = state.selectedGame
                if (game != null) {
                    selectGame(game.id)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.SecondaryAction -> {
                if (inAppBar) return InputResult.UNHANDLED
                dualHomeViewModel.toggleFavorite()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleDualDetailInput(event: GamepadEvent): InputResult {
        val vm = dualGameDetailViewModel ?: return InputResult.UNHANDLED

        val modal = vm.activeModal.value
        if (modal != ActiveModal.NONE) {
            return handleModalInput(vm, modal, event)
        }

        return when (event) {
            GamepadEvent.PrevSection -> {
                broadcastScreenshotCleared()
                vm.previousTab()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                broadcastScreenshotCleared()
                vm.nextTab()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                vm.moveSelectionUp()
                if (isScreenshotViewerOpen) {
                    broadcastScreenshotSelected(
                        vm.selectedScreenshotIndex.value
                    )
                }
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                vm.moveSelectionDown()
                if (isScreenshotViewerOpen) {
                    broadcastScreenshotSelected(
                        vm.selectedScreenshotIndex.value
                    )
                }
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> {
                        vm.focusSlotsColumn()
                    }
                    DualGameDetailTab.OPTIONS -> {
                        handleInlineAdjust(vm, -1)
                    }
                    DualGameDetailTab.MEDIA -> {
                        vm.moveSelectionLeft()
                        if (isScreenshotViewerOpen) {
                            broadcastScreenshotSelected(
                                vm.selectedScreenshotIndex.value
                            )
                        }
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> {
                        vm.focusHistoryColumn()
                    }
                    DualGameDetailTab.OPTIONS -> {
                        handleInlineAdjust(vm, 1)
                    }
                    DualGameDetailTab.MEDIA -> {
                        vm.moveSelectionRight()
                        if (isScreenshotViewerOpen) {
                            broadcastScreenshotSelected(
                                vm.selectedScreenshotIndex.value
                            )
                        }
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> {
                        handleSaveConfirm(vm)
                        InputResult.HANDLED
                    }
                    DualGameDetailTab.MEDIA -> {
                        val idx = vm.selectedScreenshotIndex.value
                        if (idx >= 0) broadcastScreenshotSelected(idx)
                        InputResult.HANDLED
                    }
                    DualGameDetailTab.OPTIONS -> {
                        handleOptionAction(vm)
                        InputResult.HANDLED
                    }
                }
            }
            GamepadEvent.Back -> {
                if (isScreenshotViewerOpen) {
                    broadcastScreenshotCleared()
                } else {
                    returnToHome()
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                if (vm.uiState.value.currentTab == DualGameDetailTab.SAVES) {
                    handleSaveLockAsSlot(vm)
                }
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> {
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleModalInput(
        vm: DualGameDetailViewModel,
        modal: ActiveModal,
        event: GamepadEvent
    ): InputResult {
        when (modal) {
            ActiveModal.EMULATOR, ActiveModal.COLLECTION -> {
                if (event == GamepadEvent.Back) {
                    when (modal) {
                        ActiveModal.EMULATOR -> vm.dismissPicker()
                        ActiveModal.COLLECTION -> vm.dismissCollectionModal()
                        else -> {}
                    }
                    broadcastModalClose()
                }
                return InputResult.HANDLED
            }
            else -> {}
        }

        return when (event) {
            GamepadEvent.Confirm -> {
                val ratingValue = vm.ratingPickerValue.value
                val statusValue = vm.statusPickerValue.value
                vm.confirmPicker()
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                        setPackage(packageName)
                        putExtra(
                            DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                            modal.name
                        )
                        putExtra(
                            DualScreenBroadcasts.EXTRA_MODAL_VALUE,
                            ratingValue
                        )
                        putExtra(
                            DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED,
                            statusValue
                        )
                    }
                )
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                vm.dismissPicker()
                broadcastModalClose()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleInlineAdjust(
        vm: DualGameDetailViewModel,
        delta: Int
    ) {
        val index = vm.selectedOptionIndex.value
        val option = vm.visibleOptions.value.getOrNull(index) ?: return
        when (option) {
            GameDetailOption.RATING -> {
                vm.adjustRatingInline(delta)
                broadcastInlineUpdate(
                    "rating", vm.uiState.value.rating ?: 0
                )
            }
            GameDetailOption.DIFFICULTY -> {
                vm.adjustDifficultyInline(delta)
                broadcastInlineUpdate(
                    "difficulty", vm.uiState.value.userDifficulty
                )
            }
            GameDetailOption.STATUS -> {
                vm.cycleStatusInline(delta)
                broadcastInlineUpdate(
                    "status", vm.uiState.value.status
                )
            }
            else -> {}
        }
    }

    private fun broadcastInlineUpdate(field: String, value: Int) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_INLINE_UPDATE).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD, field)
                putExtra(
                    DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, value
                )
            }
        )
    }

    private fun broadcastInlineUpdate(field: String, value: String?) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_INLINE_UPDATE).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD, field)
                putExtra(
                    DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE, value
                )
            }
        )
    }

    private fun handleOptionAction(vm: DualGameDetailViewModel) {
        val index = vm.selectedOptionIndex.value
        val option = vm.visibleOptions.value.getOrNull(index) ?: return
        handleOption(vm, option)
    }

    private fun handleOption(
        vm: DualGameDetailViewModel,
        option: GameDetailOption
    ) {
        val gameId = vm.uiState.value.gameId
        when (option) {
            GameDetailOption.PLAY -> {
                if (vm.uiState.value.isPlayable) {
                    broadcastDirectAction("PLAY", gameId)
                } else {
                    launchSingleScreenDetail(vm)
                }
            }
            GameDetailOption.RATING -> {
                vm.openRatingPicker()
                broadcastModalState(vm, ActiveModal.RATING)
            }
            GameDetailOption.DIFFICULTY -> {
                vm.openDifficultyPicker()
                broadcastModalState(vm, ActiveModal.DIFFICULTY)
            }
            GameDetailOption.STATUS -> {
                vm.openStatusPicker()
                broadcastModalState(vm, ActiveModal.STATUS)
            }
            GameDetailOption.TOGGLE_FAVORITE -> vm.toggleFavorite()
            GameDetailOption.CHANGE_EMULATOR -> {
                lifecycleScope.launch {
                    val detector = EmulatorDetector(applicationContext)
                    detector.detectEmulators()
                    val emulators = detector.getInstalledForPlatform(
                        vm.uiState.value.platformSlug
                    )
                    vm.openEmulatorPicker(emulators)
                    broadcastEmulatorModalOpen(
                        emulators, vm.uiState.value.emulatorName
                    )
                }
            }
            GameDetailOption.ADD_TO_COLLECTION -> {
                vm.openCollectionModal()
                lifecycleScope.launch {
                    delay(50)
                    broadcastCollectionModalOpen(vm)
                }
            }
            GameDetailOption.REFRESH_METADATA -> {
                broadcastDirectAction("REFRESH_METADATA", gameId)
            }
            GameDetailOption.DELETE -> {
                broadcastDirectAction("DELETE", gameId)
            }
            GameDetailOption.HIDE -> {
                broadcastDirectAction("HIDE", gameId)
            }
        }
    }

    private fun launchSingleScreenDetail(vm: DualGameDetailViewModel) {
        val gameId = vm.uiState.value.gameId
        val (intent, opts) = vm.getGameDetailIntent(gameId)
        if (opts != null) startActivity(intent, opts)
        else startActivity(intent)
        returnToHome()
        refocusSelf()
    }

    private fun handleSaveConfirm(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn == SaveFocusColumn.SLOTS) {
            val slot = vm.saveSlots.value
                .getOrNull(vm.selectedSlotIndex.value) ?: return
            if (slot.isCreateAction) {
                broadcastSaveNamePrompt("CREATE_SLOT", cacheId = null)
            } else {
                vm.setActiveChannel(slot.channelName)
                broadcastSaveAction(
                    "SAVE_SWITCH_CHANNEL",
                    state.gameId,
                    channelName = slot.channelName
                )
            }
        } else {
            val item = vm.saveHistory.value
                .getOrNull(vm.selectedHistoryIndex.value) ?: return
            val channelName = vm.focusedSlotChannelName
            vm.setActiveRestorePoint(channelName, item.timestamp)
            broadcastSaveAction(
                "SAVE_SET_RESTORE_POINT",
                state.gameId,
                channelName = channelName,
                timestamp = item.timestamp
            )
        }
    }

    private fun handleSaveLockAsSlot(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn != SaveFocusColumn.HISTORY) return
        val item = vm.saveHistory.value
            .getOrNull(vm.selectedHistoryIndex.value) ?: return
        broadcastSaveNamePrompt("LOCK_AS_SLOT", cacheId = item.cacheId)
    }

    private fun broadcastSaveAction(
        type: String,
        gameId: Long,
        channelName: String? = null,
        timestamp: Long? = null
    ) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                channelName?.let {
                    putExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME, it)
                }
                timestamp?.let {
                    putExtra(DualScreenBroadcasts.EXTRA_SAVE_TIMESTAMP, it)
                }
            }
        )
    }

    private fun broadcastSaveNamePrompt(
        actionType: String,
        cacheId: Long?
    ) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.SAVE_NAME.name
                )
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, actionType)
                if (cacheId != null) {
                    putExtra(DualScreenBroadcasts.EXTRA_SAVE_CACHE_ID, cacheId)
                }
            }
        )
    }

    private fun refocusSelf() {
        val intent = Intent(
            this, SecondaryHomeActivity::class.java
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun handleGridInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                viewModel.moveFocusUp()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                viewModel.moveFocusDown()
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                viewModel.moveFocusLeft()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.moveFocusRight()
                InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                viewModel.previousSection()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                viewModel.nextSection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                selectFocusedGame()
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                launchFocusedGame()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun selectFocusedGame() {
        val (intent, options) = viewModel.selectFocusedGame() ?: return
        if (options != null) {
            startActivity(intent, options)
        } else {
            startActivity(intent)
        }
    }

    private fun launchFocusedGame() {
        val result = viewModel.launchFocusedGame() ?: return
        val (intent, options) = result
        intent?.let {
            if (options != null) {
                startActivity(it, options)
            } else {
                startActivity(it)
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(foregroundReceiver)
            unregisterReceiver(saveStateReceiver)
            unregisterReceiver(sessionReceiver)
            unregisterReceiver(homeAppsReceiver)
            unregisterReceiver(libraryRefreshReceiver)
            unregisterReceiver(refocusReceiver)
            unregisterReceiver(modalResultReceiver)
            unregisterReceiver(directActionResultReceiver)
            unregisterReceiver(saveDataReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        super.onDestroy()
    }
}

/**
 * Unified container for secondary display content.
 *
 * Uses a single root Box to prevent back navigation issues.
 * Content visibility is controlled via AnimatedVisibility rather than
 * switching between different root composables.
 */
@Composable
private fun SecondaryHomeContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    channelName: String?,
    isSaveDirty: Boolean,
    homeApps: List<String>,
    viewModel: SecondaryHomeViewModel,
    dualHomeViewModel: DualHomeViewModel,
    useDualScreenMode: Boolean,
    currentScreen: SecondaryHomeActivity.CompanionScreen,
    dualGameDetailViewModel: DualGameDetailViewModel?,
    onAppClick: (String) -> Unit,
    onGameSelected: (Long) -> Unit,
    onDetailBack: () -> Unit,
    onOptionAction: (DualGameDetailViewModel, GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit
) {
    // Consume all back presses - SECONDARY_HOME should be a navigation dead-end
    BackHandler(enabled = true) {
        // Do nothing - prevent back navigation from affecting this screen
    }

    val showLibrary = isInitialized && isArgosyForeground && !isGameActive
    val showCompanion = isInitialized && !showLibrary
    val showSplash = !isInitialized

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Splash screen - shown until we receive foreground state
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_helm),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    alpha = 0.6f
                )
            }
        }

        // Library mode - dual screen carousel or grid fallback
        AnimatedVisibility(
            visible = showLibrary,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (useDualScreenMode) {
                when (currentScreen) {
                    SecondaryHomeActivity.CompanionScreen.HOME -> {
                        DualHomeLowerContent(
                            viewModel = dualHomeViewModel,
                            homeApps = homeApps,
                            onGameSelected = onGameSelected,
                            onAppClick = onAppClick
                        )
                    }
                    SecondaryHomeActivity.CompanionScreen.GAME_DETAIL -> {
                        if (dualGameDetailViewModel != null) {
                            DualGameDetailContent(
                                viewModel = dualGameDetailViewModel,
                                onOptionAction = { option ->
                                    onOptionAction(
                                        dualGameDetailViewModel, option
                                    )
                                },
                                onScreenshotViewed = onScreenshotViewed,
                                onBack = onDetailBack
                            )
                        }
                    }
                }
            } else {
                SecondaryHomeScreen(viewModel = viewModel)
            }
        }

        // Companion mode
        AnimatedVisibility(
            visible = showCompanion,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CompanionContent(
                channelName = channelName,
                isHardcore = false, // TODO: Get from session
                gameName = null,
                isDirty = isSaveDirty,
                homeApps = homeApps,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun DualGameDetailContent(
    viewModel: DualGameDetailViewModel,
    onOptionAction: (GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val slots by viewModel.saveSlots.collectAsState()
    val history by viewModel.saveHistory.collectAsState()
    val selectedSlotIndex by viewModel.selectedSlotIndex.collectAsState()
    val selectedHistoryIndex by viewModel.selectedHistoryIndex.collectAsState()
    val visibleOptions by viewModel.visibleOptions.collectAsState()
    val selectedScreenshotIndex by viewModel.selectedScreenshotIndex.collectAsState()
    val selectedOptionIndex by viewModel.selectedOptionIndex.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()
    val savesLoading by viewModel.savesLoading.collectAsState()
    val savesApplying by viewModel.savesApplying.collectAsState()

    DualGameDetailLowerScreen(
        state = state,
        slots = slots,
        history = history,
        saveFocusColumn = state.saveFocusColumn,
        selectedSlotIndex = selectedSlotIndex,
        selectedHistoryIndex = selectedHistoryIndex,
        visibleOptions = visibleOptions,
        selectedScreenshotIndex = selectedScreenshotIndex,
        selectedOptionIndex = selectedOptionIndex,
        savesLoading = savesLoading,
        savesApplying = savesApplying,
        isDimmed = activeModal != ActiveModal.NONE,
        onTabChanged = { viewModel.setTab(it) },
        onSlotTapped = { index ->
            viewModel.moveSlotSelection(index - viewModel.selectedSlotIndex.value)
        },
        onHistoryTapped = { index ->
            viewModel.moveHistorySelection(index - viewModel.selectedHistoryIndex.value)
        },
        onScreenshotSelected = { index ->
            viewModel.setScreenshotIndex(index)
        },
        onScreenshotView = { index ->
            onScreenshotViewed(index)
        },
        onOptionSelected = { option -> onOptionAction(option) },
        onBack = onBack
    )
}
