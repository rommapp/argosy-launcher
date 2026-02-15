package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.key
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
import com.nendo.argosy.data.local.entity.getDisplayName
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
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcase
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeUpperScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayRoleResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var isHardcore by mutableStateOf(false)
    private var homeApps by mutableStateOf<List<String>>(emptyList())
    private var primaryColor by mutableStateOf<Int?>(null)

    private var companionInGameState by mutableStateOf(CompanionInGameState())
    private var companionSessionTimer: CompanionSessionTimer? = null

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var dualHomeViewModel: DualHomeViewModel
    private lateinit var sessionStateStore: SessionStateStore
    private lateinit var displayAffinityHelper: DisplayAffinityHelper
    private var useDualScreenMode by mutableStateOf(false)
    private var isShowcaseRole by mutableStateOf(false)

    // Showcase state flows (used when isShowcaseRole = true)
    private val _showcaseState = MutableStateFlow(DualHomeShowcaseState())
    private val _showcaseViewMode = MutableStateFlow("CAROUSEL")
    private val _showcaseCollectionState = MutableStateFlow(DualCollectionShowcaseState())
    private val _showcaseGameDetailState = MutableStateFlow<DualGameDetailUpperState?>(null)

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

        useDualScreenMode = displayAffinityHelper.hasSecondaryDisplay
        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        isShowcaseRole = resolver.isSwapped

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

        isHardcore = sessionStateStore.isHardcore()

        val activeGameId = sessionStateStore.getGameId()
        if (isGameActive && activeGameId > 0) {
            loadCompanionGameData(activeGameId)
            companionSessionTimer = CompanionSessionTimer().also {
                it.start(applicationContext)
            }
        }

        // Restore carousel position
        val savedSection = sessionStateStore.getCarouselSectionIndex()
        val savedSelected = sessionStateStore.getCarouselSelectedIndex()
        if (savedSection > 0 || savedSelected > 0) {
            dualHomeViewModel.restorePosition(savedSection, savedSelected)
        }

        // Restore game detail screen if persisted and no game session active
        val savedScreen = sessionStateStore.getCompanionScreen()
        val savedDetailGameId = sessionStateStore.getDetailGameId()
        if (savedScreen == "GAME_DETAIL" &&
            savedDetailGameId > 0 &&
            !isGameActive
        ) {
            val affinityHelper = DisplayAffinityHelper(applicationContext)
            val vm = DualGameDetailViewModel(
                gameDao = gameDao,
                platformDao = platformDao,
                collectionDao = collectionDao,
                emulatorConfigDao = emulatorConfigDao,
                displayAffinityHelper = affinityHelper,
                context = applicationContext
            )
            vm.loadGame(savedDetailGameId)
            dualGameDetailViewModel = vm
            currentScreen = CompanionScreen.GAME_DETAIL
            broadcastGameDetailOpened(savedDetailGameId)
        }

        loadInputSwapPreferences()
        isInitialized = true
    }

    private fun loadInputSwapPreferences() {
        swapAB = sessionStateStore.getSwapAB()
        swapXY = sessionStateStore.getSwapXY()
        swapStartSelect = sessionStateStore.getSwapStartSelect()
        dualScreenInputFocus = sessionStateStore.getDualScreenInputFocus()

        val isNintendoLayout = ControllerDetector.detectFromActiveGamepad().layout == DetectedLayout.NINTENDO
        abIconsSwapped = isNintendoLayout xor swapAB
        xyIconsSwapped = isNintendoLayout xor swapXY
        startSelectSwapped = swapStartSelect
    }

    private fun loadCompanionGameData(gameId: Long) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformDao.getById(game.platformId)
            val startTime = sessionStateStore.getSessionStartTimeMillis()
            companionInGameState = CompanionInGameState(
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
                sessionStartTimeMillis = startTime,
                channelName = sessionStateStore.getChannelName(),
                isHardcore = sessionStateStore.isHardcore(),
                isDirty = sessionStateStore.isSaveDirty(),
                isLoaded = true
            )
        }
    }

    private fun persistCarouselPosition() {
        val state = dualHomeViewModel.uiState.value
        sessionStateStore.setCarouselPosition(
            state.currentSectionIndex,
            state.selectedIndex
        )
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
                val isDirty = intent.getBooleanExtra(EXTRA_IS_DIRTY, false)
                isSaveDirty = isDirty
                companionInGameState = companionInGameState.copy(isDirty = isDirty)
            }
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SESSION_CHANGED) {
                val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)
                if (gameId > 0) {
                    isGameActive = true
                    viewModel.companionFocusAppBar(homeApps.size)
                    isHardcore = intent.getBooleanExtra(
                        EXTRA_IS_HARDCORE, false
                    )
                    currentChannelName = intent.getStringExtra(
                        EXTRA_CHANNEL_NAME
                    )
                    isSaveDirty = false
                    currentScreen = CompanionScreen.HOME
                    dualGameDetailViewModel = null
                    sessionStateStore.setCompanionScreen("HOME")
                    loadCompanionGameData(gameId)
                    companionSessionTimer?.stop(applicationContext)
                    companionSessionTimer = CompanionSessionTimer().also {
                        it.start(applicationContext)
                    }
                } else {
                    isGameActive = false
                    isHardcore = false
                    currentChannelName = null
                    isSaveDirty = false
                    sessionStateStore.setCompanionScreen("HOME")
                    companionInGameState = CompanionInGameState()
                    companionSessionTimer?.stop(applicationContext)
                    companionSessionTimer = null
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
                    dualHomeViewModel.refresh()
                    if (intent.action == ACTION_DOWNLOAD_COMPLETED) {
                        val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)
                        if (gameId > 0 && _showcaseState.value.gameId == gameId) {
                            _showcaseState.value = _showcaseState.value.copy(
                                isDownloaded = true
                            )
                        }
                    }
                }
            }
        }
    }

    private val overlayCloseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_CLOSE_OVERLAY ->
                    dualHomeViewModel.stopDrawerForwarding()
                DualScreenBroadcasts.ACTION_BACKGROUND_FORWARD ->
                    dualHomeViewModel.startBackgroundForwarding()
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

    private val showcaseGameSelectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DualScreenBroadcasts.ACTION_GAME_SELECTED) {
                _showcaseState.value = DualHomeShowcaseState(
                    gameId = intent.getLongExtra("game_id", -1),
                    title = intent.getStringExtra("title") ?: "",
                    coverPath = intent.getStringExtra("cover_path"),
                    backgroundPath = intent.getStringExtra("background_path"),
                    platformName = intent.getStringExtra("platform_name") ?: "",
                    platformSlug = intent.getStringExtra("platform_slug") ?: "",
                    playTimeMinutes = intent.getIntExtra("play_time_minutes", 0),
                    lastPlayedAt = intent.getLongExtra("last_played_at", 0),
                    status = intent.getStringExtra("status"),
                    communityRating = intent.getFloatExtra("community_rating", 0f).takeIf { it > 0f },
                    userRating = intent.getIntExtra("user_rating", 0),
                    userDifficulty = intent.getIntExtra("user_difficulty", 0),
                    description = intent.getStringExtra("description"),
                    developer = intent.getStringExtra("developer"),
                    releaseYear = intent.getIntExtra("release_year", 0).takeIf { it > 0 },
                    titleId = intent.getStringExtra("title_id"),
                    isFavorite = intent.getBooleanExtra("is_favorite", false),
                    isDownloaded = intent.getBooleanExtra("is_downloaded", true)
                )
            }
        }
    }

    private val showcaseViewModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED -> {
                    _showcaseViewMode.value = intent.getStringExtra(DualScreenBroadcasts.EXTRA_VIEW_MODE) ?: "CAROUSEL"
                }
                DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED -> {
                    _showcaseCollectionState.value = DualCollectionShowcaseState(
                        name = intent.getStringExtra(DualScreenBroadcasts.EXTRA_COLLECTION_NAME_DISPLAY) ?: "",
                        description = intent.getStringExtra(DualScreenBroadcasts.EXTRA_COLLECTION_DESCRIPTION),
                        coverPaths = intent.getStringArrayListExtra(DualScreenBroadcasts.EXTRA_COLLECTION_COVER_PATHS)?.toList() ?: emptyList(),
                        gameCount = intent.getIntExtra(DualScreenBroadcasts.EXTRA_COLLECTION_GAME_COUNT, 0),
                        platformSummary = intent.getStringExtra(DualScreenBroadcasts.EXTRA_COLLECTION_PLATFORM_SUMMARY) ?: "",
                        totalPlaytimeMinutes = intent.getIntExtra(DualScreenBroadcasts.EXTRA_COLLECTION_TOTAL_PLAYTIME, 0)
                    )
                }
            }
        }
    }

    private val showcaseGameDetailReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED -> {
                    val gameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
                    if (gameId == -1L) return
                    val showcase = _showcaseState.value
                    _showcaseGameDetailState.value = if (showcase.gameId == gameId) {
                        DualGameDetailUpperState(
                            gameId = gameId,
                            title = showcase.title,
                            coverPath = showcase.coverPath,
                            backgroundPath = showcase.backgroundPath,
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
                        DualGameDetailUpperState(gameId = gameId)
                    }
                }
                DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED -> {
                    _showcaseGameDetailState.value = null
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED -> {
                    val index = intent.getIntExtra(DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, -1)
                    _showcaseGameDetailState.value = _showcaseGameDetailState.value?.copy(
                        viewerScreenshotIndex = index.takeIf { it >= 0 }
                    )
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR -> {
                    _showcaseGameDetailState.value = _showcaseGameDetailState.value?.copy(
                        viewerScreenshotIndex = null
                    )
                }
                DualScreenBroadcasts.ACTION_INLINE_UPDATE -> {
                    val field = intent.getStringExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD) ?: return
                    when (field) {
                        "rating" -> {
                            val v = intent.getIntExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0)
                            _showcaseGameDetailState.value = _showcaseGameDetailState.value?.copy(
                                rating = v.takeIf { it > 0 }
                            )
                        }
                        "difficulty" -> {
                            val v = intent.getIntExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0)
                            _showcaseGameDetailState.value = _showcaseGameDetailState.value?.copy(
                                userDifficulty = v
                            )
                        }
                        "status" -> {
                            val v = intent.getStringExtra(DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE)
                            _showcaseGameDetailState.value = _showcaseGameDetailState.value?.copy(
                                status = v
                            )
                        }
                    }
                }
            }
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
                            onAppClick = { packageName -> launchApp(packageName) },
                            onGameSelected = { gameId -> selectGame(gameId) },
                            onCollectionsClick = {
                                dualHomeViewModel.enterCollections()
                                broadcastViewModeChange()
                                broadcastCollectionFocused()
                            },
                            onLibraryToggle = {
                                dualHomeViewModel.toggleLibraryGrid {
                                    broadcastViewModeChange()
                                    val state = dualHomeViewModel.uiState.value
                                    if (state.viewMode == com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.LIBRARY_GRID)
                                        broadcastLibraryGameSelection()
                                    else
                                        broadcastCurrentGameSelection()
                                }
                            },
                            onViewAllClick = {
                                val platformId = dualHomeViewModel.uiState.value.currentPlatformId
                                if (platformId != null) {
                                    dualHomeViewModel.enterLibraryGridForPlatform(platformId) {
                                        broadcastViewModeChange()
                                        broadcastLibraryGameSelection()
                                    }
                                } else {
                                    dualHomeViewModel.enterLibraryGrid {
                                        broadcastViewModeChange()
                                        broadcastLibraryGameSelection()
                                    }
                                }
                            },
                            onCollectionTapped = { index ->
                                val items = dualHomeViewModel.uiState.value.collectionItems
                                val item = items.getOrNull(index)
                                if (item is com.nendo.argosy.ui.dualscreen.home.DualCollectionListItem.Collection) {
                                    dualHomeViewModel.enterCollectionGames(item.id)
                                    broadcastViewModeChange()
                                }
                            },
                            onGridGameTapped = { index ->
                                val state = dualHomeViewModel.uiState.value
                                when (state.viewMode) {
                                    com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.COLLECTION_GAMES -> {
                                        dualHomeViewModel.moveCollectionGamesFocus(
                                            index - state.collectionGamesFocusedIndex
                                        )
                                        broadcastCollectionGameSelection()
                                    }
                                    com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.LIBRARY_GRID -> {
                                        dualHomeViewModel.moveLibraryFocus(
                                            index - state.libraryFocusedIndex
                                        )
                                        broadcastLibraryGameSelection()
                                    }
                                    else -> {}
                                }
                            },
                            onLetterClick = { letter ->
                                dualHomeViewModel.jumpToLetter(letter)
                                broadcastLibraryGameSelection()
                            },
                            onFilterOptionTapped = { index ->
                                dualHomeViewModel.moveFilterFocus(
                                    index - dualHomeViewModel.uiState.value.filterFocusedIndex
                                )
                                dualHomeViewModel.confirmFilter()
                            },
                            onFilterCategoryTapped = { category ->
                                dualHomeViewModel.setFilterCategory(category)
                            },
                            onDetailBack = { returnToHome() },
                            onOptionAction = { vm, option ->
                                handleOption(vm, option)
                            },
                            onScreenshotViewed = { index ->
                                broadcastScreenshotSelected(index)
                            },
                            onDimTapped = { refocusMain() },
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
        isGameActive = sessionStateStore.hasActiveSession()
        isHardcore = sessionStateStore.isHardcore()
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
        displayAffinityHelper = DisplayAffinityHelper(applicationContext)

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
            collectionDao = collectionDao,
            downloadQueueDao = database.downloadQueueDao(),
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
        val overlayCloseFilter = IntentFilter(DualScreenBroadcasts.ACTION_CLOSE_OVERLAY).apply {
            addAction(DualScreenBroadcasts.ACTION_BACKGROUND_FORWARD)
        }
        val refocusFilter = IntentFilter(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
        val modalResultFilter = IntentFilter(DualScreenBroadcasts.ACTION_MODAL_RESULT)
        val directActionFilter = IntentFilter(DualScreenBroadcasts.ACTION_DIRECT_ACTION)
        val saveDataFilter = IntentFilter(DualScreenBroadcasts.ACTION_SAVE_DATA)

        val flag = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, foregroundReceiver, foregroundFilter, flag)
        ContextCompat.registerReceiver(this, saveStateReceiver, saveStateFilter, flag)
        ContextCompat.registerReceiver(this, sessionReceiver, sessionFilter, flag)
        ContextCompat.registerReceiver(this, homeAppsReceiver, homeAppsFilter, flag)
        ContextCompat.registerReceiver(this, libraryRefreshReceiver, libraryRefreshFilter, flag)
        ContextCompat.registerReceiver(this, overlayCloseReceiver, overlayCloseFilter, flag)
        ContextCompat.registerReceiver(this, refocusReceiver, refocusFilter, flag)
        ContextCompat.registerReceiver(this, modalResultReceiver, modalResultFilter, flag)
        ContextCompat.registerReceiver(this, directActionResultReceiver, directActionFilter, flag)
        ContextCompat.registerReceiver(this, saveDataReceiver, saveDataFilter, flag)

        if (isShowcaseRole) {
            val showcaseGameFilter = IntentFilter(DualScreenBroadcasts.ACTION_GAME_SELECTED)
            val showcaseViewModeFilter = IntentFilter().apply {
                addAction(DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED)
                addAction(DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED)
            }
            val showcaseDetailFilter = IntentFilter().apply {
                addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED)
                addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED)
                addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED)
                addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR)
                addAction(DualScreenBroadcasts.ACTION_INLINE_UPDATE)
            }
            ContextCompat.registerReceiver(this, showcaseGameSelectedReceiver, showcaseGameFilter, flag)
            ContextCompat.registerReceiver(this, showcaseViewModeReceiver, showcaseViewModeFilter, flag)
            ContextCompat.registerReceiver(this, showcaseGameDetailReceiver, showcaseDetailFilter, flag)
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
        sessionStateStore.setCompanionScreen("GAME_DETAIL", gameId)
        broadcastGameDetailOpened(gameId)
    }

    private fun returnToHome() {
        isScreenshotViewerOpen = false
        currentScreen = CompanionScreen.HOME
        dualGameDetailViewModel = null
        sessionStateStore.setCompanionScreen("HOME")
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

    private fun broadcastViewModeChange(drawerOpen: Boolean? = null) {
        val state = dualHomeViewModel.uiState.value
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_VIEW_MODE, state.viewMode.name)
                putExtra(DualScreenBroadcasts.EXTRA_IS_APP_BAR_FOCUSED,
                    state.focusZone == DualHomeFocusZone.APP_BAR)
                putExtra(DualScreenBroadcasts.EXTRA_IS_DRAWER_OPEN,
                    drawerOpen ?: viewModel.uiState.value.isDrawerOpen)
            }
        )
    }

    private fun broadcastCollectionFocused() {
        val item = dualHomeViewModel.selectedCollectionItem() ?: return
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_NAME_DISPLAY, item.name)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_DESCRIPTION, item.description)
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_COVER_PATHS,
                    ArrayList(item.coverPaths)
                )
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_GAME_COUNT, item.gameCount)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_PLATFORM_SUMMARY, item.platformSummary)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_TOTAL_PLAYTIME, item.totalPlaytimeMinutes)
            }
        )
    }

    private fun broadcastLibraryGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.libraryGames.getOrNull(state.libraryFocusedIndex) ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(this, game)
    }

    private fun broadcastCollectionGameSelection() {
        val game = dualHomeViewModel.focusedCollectionGame() ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(this, game)
    }

    private fun broadcastCurrentGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.selectedGame ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(this, game)
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

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val result = super.dispatchTouchEvent(event)
        if (!isShowcaseRole &&
            event.action == android.view.MotionEvent.ACTION_UP &&
            dualHomeViewModel.forwardingMode.value == com.nendo.argosy.ui.dualscreen.home.ForwardingMode.BACKGROUND
        ) {
            window.decorView.post {
                if (currentScreen == CompanionScreen.HOME) {
                    refocusMain()
                }
            }
        }
        return result
    }

    private var dualScreenInputFocus = "AUTO"

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isShowcaseRole) return super.onKeyDown(keyCode, event)
        if (dualScreenInputFocus == "TOP") return super.onKeyDown(keyCode, event)

        if (event.repeatCount == 0) {
            android.util.Log.d("SecondaryInput", "keyCode=$keyCode (${android.view.KeyEvent.keyCodeToString(keyCode)}), swapAB=$swapAB, swapXY=$swapXY")
            val gamepadEvent = mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
            if (gamepadEvent != null) {
                val result = if (useDualScreenMode && isArgosyForeground && !isGameActive) {
                    when (currentScreen) {
                        CompanionScreen.HOME -> handleDualHomeInput(gamepadEvent)
                        CompanionScreen.GAME_DETAIL -> handleDualDetailInput(gamepadEvent)
                    }
                } else if (useDualScreenMode && isGameActive) {
                    handleCompanionInput(gamepadEvent)
                } else {
                    handleGridInput(gamepadEvent)
                }
                if (result.handled) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleDualHomeInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)
        if (dualHomeViewModel.forwardingMode.value != com.nendo.argosy.ui.dualscreen.home.ForwardingMode.NONE) {
            return InputResult.HANDLED
        }

        return when (dualHomeViewModel.uiState.value.viewMode) {
            com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.CAROUSEL ->
                handleCarouselInput(event)
            com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.COLLECTIONS ->
                handleCollectionsInput(event)
            com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.COLLECTION_GAMES ->
                handleCollectionGamesInput(event)
            com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.LIBRARY_GRID ->
                handleLibraryGridInput(event)
        }
    }

    private fun handleCarouselInput(event: GamepadEvent): InputResult {
        val state = dualHomeViewModel.uiState.value
        val inAppBar = state.focusZone == DualHomeFocusZone.APP_BAR

        return when (event) {
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                dualHomeViewModel.startDrawerForwarding()
                sendBroadcast(Intent(DualScreenBroadcasts.ACTION_OPEN_OVERLAY).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME, event::class.simpleName)
                })
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                if (inAppBar) dualHomeViewModel.selectPreviousApp()
                else {
                    dualHomeViewModel.selectPrevious()
                    persistCarouselPosition()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                if (inAppBar) dualHomeViewModel.selectNextApp(homeApps.size)
                else {
                    dualHomeViewModel.selectNext()
                    persistCarouselPosition()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                if (!inAppBar) {
                    dualHomeViewModel.focusAppBar(homeApps.size)
                    broadcastViewModeChange()
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.Up -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcastViewModeChange()
                    InputResult.HANDLED
                } else {
                    dualHomeViewModel.enterCollections()
                    broadcastViewModeChange()
                    broadcastCollectionFocused()
                    InputResult.HANDLED
                }
            }
            GamepadEvent.PrevSection -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcastViewModeChange()
                }
                dualHomeViewModel.previousSection()
                persistCarouselPosition()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcastViewModeChange()
                }
                dualHomeViewModel.nextSection()
                persistCarouselPosition()
                InputResult.HANDLED
            }
            GamepadEvent.Select -> {
                if (inAppBar) {
                    viewModel.openDrawer()
                    broadcastViewModeChange(drawerOpen = true)
                } else {
                    dualHomeViewModel.toggleLibraryGrid {
                        broadcastViewModeChange()
                        val state = dualHomeViewModel.uiState.value
                        if (state.viewMode == com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode.LIBRARY_GRID)
                            broadcastLibraryGameSelection()
                        else
                            broadcastCurrentGameSelection()
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (inAppBar && state.appBarIndex == -1) {
                    viewModel.openDrawer()
                    broadcastViewModeChange(drawerOpen = true)
                    InputResult.HANDLED
                } else if (inAppBar) {
                    val packageName = homeApps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        launchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else if (state.isViewAllFocused) {
                    val platformId = state.currentPlatformId
                    if (platformId != null) {
                        dualHomeViewModel.enterLibraryGridForPlatform(platformId) {
                            broadcastViewModeChange()
                            broadcastLibraryGameSelection()
                        }
                    } else {
                        dualHomeViewModel.enterLibraryGrid {
                            broadcastViewModeChange()
                            broadcastLibraryGameSelection()
                        }
                    }
                    InputResult.HANDLED
                } else {
                    val game = state.selectedGame
                    if (game != null) {
                        val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                        broadcastDirectAction(action, game.id)
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

    private fun handleCollectionsInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                dualHomeViewModel.moveCollectionFocus(-1)
                broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveCollectionFocus(1)
                broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val collection = dualHomeViewModel.selectedCollectionItem()
                if (collection != null) {
                    dualHomeViewModel.enterCollectionGames(collection.id) {
                        broadcastViewModeChange()
                        broadcastCollectionGameSelection()
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitToCarousel()
                broadcastViewModeChange()
                broadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                dualHomeViewModel.startDrawerForwarding()
                sendBroadcast(Intent(DualScreenBroadcasts.ACTION_OPEN_OVERLAY).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME, event::class.simpleName)
                })
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleCollectionGamesInput(event: GamepadEvent): InputResult {
        val columns = dualHomeViewModel.uiState.value.libraryColumns
        return when (event) {
            GamepadEvent.Left -> {
                dualHomeViewModel.moveCollectionGamesFocus(-1)
                broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                dualHomeViewModel.moveCollectionGamesFocus(1)
                broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                dualHomeViewModel.moveCollectionGamesFocus(-columns)
                broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveCollectionGamesFocus(columns)
                broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val game = dualHomeViewModel.focusedCollectionGame()
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    broadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                val game = dualHomeViewModel.focusedCollectionGame()
                if (game != null) {
                    selectGame(game.id)
                    InputResult.HANDLED
                } else InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitCollectionGames()
                broadcastViewModeChange()
                broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                dualHomeViewModel.startDrawerForwarding()
                sendBroadcast(Intent(DualScreenBroadcasts.ACTION_OPEN_OVERLAY).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME, event::class.simpleName)
                })
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleLibraryGridInput(event: GamepadEvent): InputResult {
        if (dualHomeViewModel.uiState.value.showFilterOverlay) {
            return handleFilterInput(event)
        }

        val columns = dualHomeViewModel.uiState.value.libraryColumns
        return when (event) {
            GamepadEvent.Left -> {
                dualHomeViewModel.moveLibraryFocus(-1)
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                dualHomeViewModel.moveLibraryFocus(1)
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                dualHomeViewModel.moveLibraryFocus(-columns)
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveLibraryFocus(columns)
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.PrevTrigger -> {
                dualHomeViewModel.previousLetter()
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.NextTrigger -> {
                dualHomeViewModel.nextLetter()
                broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Select -> {
                dualHomeViewModel.toggleLibraryGrid {
                    broadcastViewModeChange()
                    broadcastCurrentGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitToCarousel()
                broadcastViewModeChange()
                broadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val state = dualHomeViewModel.uiState.value
                val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    broadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                val state = dualHomeViewModel.uiState.value
                val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
                if (game != null) {
                    selectGame(game.id)
                    InputResult.HANDLED
                } else InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                dualHomeViewModel.cycleLibraryPlatform(-1) {
                    broadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                dualHomeViewModel.cycleLibraryPlatform(1) {
                    broadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> {
                dualHomeViewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                dualHomeViewModel.startDrawerForwarding()
                sendBroadcast(Intent(DualScreenBroadcasts.ACTION_OPEN_OVERLAY).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME, event::class.simpleName)
                })
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleFilterInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                dualHomeViewModel.moveFilterFocus(-1)
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveFilterFocus(1)
                InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                dualHomeViewModel.previousFilterCategory()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                dualHomeViewModel.nextFilterCategory()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                dualHomeViewModel.confirmFilter()
                InputResult.HANDLED
            }
            GamepadEvent.Back, GamepadEvent.SecondaryAction -> {
                dualHomeViewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                dualHomeViewModel.clearCategoryFilters()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
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
                    broadcastDirectAction("DOWNLOAD", gameId)
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

    private fun refocusMain() {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_REFOCUS_UPPER).setPackage(packageName)
        )
    }

    private fun handleGridInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)

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
            GamepadEvent.Select -> {
                viewModel.openDrawer()
                broadcastViewModeChange(drawerOpen = true)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleDrawerInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                viewModel.moveDrawerFocusUp()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                viewModel.moveDrawerFocusDown()
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                viewModel.moveDrawerFocusLeft()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.moveDrawerFocusRight()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val (intent, options) = viewModel.launchDrawerApp() ?: return InputResult.HANDLED
                viewModel.closeDrawer()
                broadcastViewModeChange(drawerOpen = false)
                if (options != null) startActivity(intent, options) else intent?.let { startActivity(it) }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                viewModel.toggleDrawerFocusedPin()
                InputResult.HANDLED
            }
            GamepadEvent.Back, GamepadEvent.Select -> {
                viewModel.closeDrawer()
                broadcastViewModeChange(drawerOpen = false)
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleCompanionInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)

        val state = viewModel.uiState.value
        val appBarIndex = state.companionAppBarIndex

        return when (event) {
            GamepadEvent.Left -> {
                viewModel.companionSelectPreviousApp()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.companionSelectNextApp(homeApps.size)
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (appBarIndex == -1) {
                    viewModel.openDrawer()
                    InputResult.HANDLED
                } else {
                    val packageName = homeApps.getOrNull(appBarIndex)
                    if (packageName != null) {
                        launchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                }
            }
            GamepadEvent.Select -> {
                viewModel.openDrawer()
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
            unregisterReceiver(overlayCloseReceiver)
            unregisterReceiver(refocusReceiver)
            unregisterReceiver(modalResultReceiver)
            unregisterReceiver(directActionResultReceiver)
            unregisterReceiver(saveDataReceiver)
            if (isShowcaseRole) {
                unregisterReceiver(showcaseGameSelectedReceiver)
                unregisterReceiver(showcaseViewModeReceiver)
                unregisterReceiver(showcaseGameDetailReceiver)
            }
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
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
    companionInGameState: CompanionInGameState,
    companionSessionTimer: CompanionSessionTimer?,
    homeApps: List<String>,
    viewModel: SecondaryHomeViewModel,
    dualHomeViewModel: DualHomeViewModel,
    useDualScreenMode: Boolean,
    currentScreen: SecondaryHomeActivity.CompanionScreen,
    dualGameDetailViewModel: DualGameDetailViewModel?,
    onAppClick: (String) -> Unit,
    onGameSelected: (Long) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (com.nendo.argosy.ui.dualscreen.home.DualFilterCategory) -> Unit,
    onDetailBack: () -> Unit,
    onOptionAction: (DualGameDetailViewModel, GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onDimTapped: () -> Unit = {},
    onTabChanged: (CompanionPanel) -> Unit = {}
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
                            onAppClick = onAppClick,
                            onCollectionsClick = onCollectionsClick,
                            onLibraryToggle = onLibraryToggle,
                            onViewAllClick = onViewAllClick,
                            onCollectionTapped = onCollectionTapped,
                            onGridGameTapped = onGridGameTapped,
                            onLetterClick = onLetterClick,
                            onFilterOptionTapped = onFilterOptionTapped,
                            onFilterCategoryTapped = onFilterCategoryTapped,
                            onOpenDrawer = { viewModel.openDrawer() },
                            onDimTapped = onDimTapped
                        )
                    }
                    SecondaryHomeActivity.CompanionScreen.GAME_DETAIL -> {
                        if (dualGameDetailViewModel != null) {
                            val detailState by dualGameDetailViewModel.uiState.collectAsState()
                            key(detailState.gameId) {
                                DualGameDetailContent(
                                    viewModel = dualGameDetailViewModel,
                                    onOptionAction = { option ->
                                        onOptionAction(
                                            dualGameDetailViewModel, option
                                        )
                                    },
                                    onScreenshotViewed = onScreenshotViewed,
                                    onBack = onDetailBack,
                                    onDimTapped = onDimTapped
                                )
                            }
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
                state = companionInGameState,
                sessionTimer = companionSessionTimer,
                homeApps = homeApps,
                onAppClick = onAppClick,
                onTabChanged = onTabChanged,
                onOpenDrawer = { viewModel.openDrawer() }
            )
        }

        // All Apps Drawer overlay (shared across library and companion modes)
        val drawerState by viewModel.uiState.collectAsState()
        AnimatedVisibility(
            visible = drawerState.isDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.closeDrawer() }
                    )
            )
        }

        AnimatedVisibility(
            visible = drawerState.isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            com.nendo.argosy.ui.screens.secondaryhome.AllAppsDrawerOverlay(
                apps = drawerState.allApps,
                focusedIndex = drawerState.drawerFocusedIndex,
                screenWidthDp = drawerState.screenWidthDp,
                onPinToggle = { viewModel.togglePinFromDrawer(it) },
                onAppClick = { pkg ->
                    viewModel.closeDrawer()
                    onAppClick(pkg)
                },
                onClose = { viewModel.closeDrawer() }
            )
        }
    }
}

@Composable
private fun ShowcaseRoleContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    showcaseState: kotlinx.coroutines.flow.StateFlow<DualHomeShowcaseState>,
    showcaseViewMode: kotlinx.coroutines.flow.StateFlow<String>,
    collectionShowcaseState: kotlinx.coroutines.flow.StateFlow<DualCollectionShowcaseState>,
    gameDetailState: kotlinx.coroutines.flow.StateFlow<DualGameDetailUpperState?>
) {
    BackHandler(enabled = true) { }

    val showShowcase = isInitialized && isArgosyForeground && !isGameActive
    val showSplash = !isInitialized

    val showcase by showcaseState.collectAsState()
    val viewMode by showcaseViewMode.collectAsState()
    val collectionState by collectionShowcaseState.collectAsState()
    val detailState by gameDetailState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        AnimatedVisibility(
            visible = showShowcase,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val emptyFooter: @Composable () -> Unit = {}

            if (detailState != null) {
                DualGameDetailUpperScreen(
                    state = detailState!!,
                    footerHints = emptyFooter
                )
            } else if (viewMode == "COLLECTIONS") {
                DualCollectionShowcase(
                    state = collectionState,
                    footerHints = emptyFooter
                )
            } else {
                DualHomeUpperScreen(
                    state = showcase,
                    footerHints = emptyFooter
                )
            }
        }

        // When not in showcase mode (game running, background, etc), show splash
        AnimatedVisibility(
            visible = isInitialized && !showShowcase,
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
    }
}

@Composable
private fun DualGameDetailContent(
    viewModel: DualGameDetailViewModel,
    onOptionAction: (GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onBack: () -> Unit,
    onDimTapped: () -> Unit = {}
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
        onDimTapped = onDimTapped,
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
