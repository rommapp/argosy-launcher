package com.nendo.argosy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.emulator.LaunchRetryTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import com.nendo.argosy.ui.ArgosyApp
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualCollectionItem
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.ui.dualscreen.gamedetail.toJsonString
import com.nendo.argosy.ui.dualscreen.gamedetail.toSaveEntryData
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.hardware.RecoveryDisplayService
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayRoleResolver
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.nendo.argosy.ui.theme.ALauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _pendingDeepLink = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: kotlinx.coroutines.flow.StateFlow<android.net.Uri?> = _pendingDeepLink

    private val _dualScreenShowcase = kotlinx.coroutines.flow.MutableStateFlow(DualHomeShowcaseState())
    val dualScreenShowcase: kotlinx.coroutines.flow.StateFlow<DualHomeShowcaseState> = _dualScreenShowcase

    private val _dualGameDetailState = kotlinx.coroutines.flow.MutableStateFlow<DualGameDetailUpperState?>(null)
    val dualGameDetailState: kotlinx.coroutines.flow.StateFlow<DualGameDetailUpperState?> = _dualGameDetailState

    private val _isCompanionActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isCompanionActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isCompanionActive

    private val _dualViewMode = kotlinx.coroutines.flow.MutableStateFlow("CAROUSEL")
    val dualViewMode: kotlinx.coroutines.flow.StateFlow<String> = _dualViewMode

    private val _dualCollectionShowcase = kotlinx.coroutines.flow.MutableStateFlow(
        com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState()
    )
    val dualCollectionShowcase: kotlinx.coroutines.flow.StateFlow<com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState> =
        _dualCollectionShowcase

    var isOverlayFocused = false
    var isRolesSwapped = false
        private set
    var swappedDualHomeViewModel: com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel? = null
        private set

    val homeAppsList: List<String>
        get() = sessionStateStore.getHomeApps()?.toList() ?: emptyList()

    private val _pendingOverlayEvent = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pendingOverlayEvent: kotlinx.coroutines.flow.StateFlow<String?> = _pendingOverlayEvent

    fun clearPendingOverlay() { _pendingOverlayEvent.value = null }

    private val overlayOpenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_OPEN_OVERLAY -> {
                    isOverlayFocused = true
                    _pendingOverlayEvent.value =
                        intent.getStringExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME) ?: "Menu"
                    refocusMain()
                }
                DualScreenBroadcasts.ACTION_REFOCUS_UPPER -> {
                    refocusMain()
                }
            }
        }
    }

    private val companionLifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_COMPANION_RESUMED -> {
                    _isCompanionActive.value = true
                }
                DualScreenBroadcasts.ACTION_COMPANION_PAUSED -> {
                    _isCompanionActive.value = false
                }
            }
        }
    }

    private val dualViewModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED -> {
                    val mode = intent.getStringExtra(DualScreenBroadcasts.EXTRA_VIEW_MODE) ?: "CAROUSEL"
                    _dualViewMode.value = mode
                }
                DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED -> {
                    _dualCollectionShowcase.value = com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState(
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

    private val dualGameSelectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.nendo.argosy.DUAL_GAME_SELECTED") {
                _dualScreenShowcase.value = DualHomeShowcaseState(
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
                    isFavorite = intent.getBooleanExtra("is_favorite", false)
                )
            }
        }
    }

    private val dualGameDetailReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED -> {
                    val gameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
                    if (gameId == -1L) return
                    val showcase = _dualScreenShowcase.value
                    if (showcase.gameId == gameId) {
                        _dualGameDetailState.value = DualGameDetailUpperState(
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
                        _dualGameDetailState.value = DualGameDetailUpperState(gameId = gameId)
                    }
                    broadcastUnifiedSaves(gameId)
                    activityScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val game = gameDao.getById(gameId)
                            ?: return@launch
                        if (showcase.gameId != gameId) {
                            val platform = platformDao.getById(game.platformId)
                            _dualGameDetailState.update { state ->
                                state?.copy(
                                    title = game.title,
                                    coverPath = game.coverPath,
                                    backgroundPath = game.backgroundPath,
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
                                    titleId = game.raId?.toString()
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
                DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED -> {
                    _dualGameDetailState.value = null
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED -> {
                    val index = intent.getIntExtra(
                        DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, -1
                    )
                    _dualGameDetailState.update { state ->
                        state?.copy(
                            viewerScreenshotIndex = index
                                .takeIf { it >= 0 }
                        )
                    }
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR -> {
                    _dualGameDetailState.update { state ->
                        state?.copy(viewerScreenshotIndex = null)
                    }
                }
                DualScreenBroadcasts.ACTION_MODAL_OPEN -> {
                    val modalType = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_TYPE
                    ) ?: return
                    val type = ActiveModal.entries.find {
                        it.name == modalType
                    } ?: return
                    when (type) {
                        ActiveModal.EMULATOR -> {
                            val names = intent.getStringArrayListExtra(
                                DualScreenBroadcasts.EXTRA_EMULATOR_NAMES
                            ) ?: arrayListOf()
                            val versions = intent.getStringArrayListExtra(
                                DualScreenBroadcasts.EXTRA_EMULATOR_VERSIONS
                            ) ?: arrayListOf()
                            val current = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_EMULATOR_CURRENT
                            )
                            _dualGameDetailState.update { state ->
                                state?.copy(
                                    modalType = ActiveModal.EMULATOR,
                                    emulatorNames = names,
                                    emulatorVersions = versions,
                                    emulatorFocusIndex = 0,
                                    emulatorCurrentName = current
                                )
                            }
                        }
                        ActiveModal.COLLECTION -> {
                            val ids = intent.getLongArrayExtra(
                                DualScreenBroadcasts.EXTRA_COLLECTION_IDS
                            )?.toList() ?: emptyList()
                            val names = intent.getStringArrayListExtra(
                                DualScreenBroadcasts.EXTRA_COLLECTION_NAMES
                            ) ?: arrayListOf()
                            val checked = intent.getBooleanArrayExtra(
                                DualScreenBroadcasts.EXTRA_COLLECTION_CHECKED
                            )?.toList() ?: emptyList()
                            _dualGameDetailState.update { state ->
                                state?.copy(
                                    modalType = ActiveModal.COLLECTION,
                                    collectionItems = ids.mapIndexed { i, id ->
                                        DualCollectionItem(
                                            id,
                                            names.getOrElse(i) { "" },
                                            checked.getOrElse(i) { false }
                                        )
                                    },
                                    collectionFocusIndex = 0
                                )
                            }
                        }
                        ActiveModal.SAVE_NAME -> {
                            val actionType = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_ACTION_TYPE
                            )
                            val cacheId = intent.getLongExtra(
                                DualScreenBroadcasts.EXTRA_SAVE_CACHE_ID, -1
                            )
                            _dualGameDetailState.update { state ->
                                state?.copy(
                                    modalType = ActiveModal.SAVE_NAME,
                                    saveNamePromptAction = actionType,
                                    saveNameCacheId = if (cacheId > 0) cacheId
                                        else null,
                                    saveNameText = ""
                                )
                            }
                        }
                        else -> handleDualModalOpen(
                            type = type,
                            value = intent.getIntExtra(
                                DualScreenBroadcasts.EXTRA_MODAL_VALUE, 0
                            ),
                            statusSelected = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED
                            ),
                            statusCurrent = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_MODAL_STATUS_CURRENT
                            )
                        )
                    }
                    startActivity(
                        Intent(
                            this@MainActivity, MainActivity::class.java
                        ).addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                }
                DualScreenBroadcasts.ACTION_MODAL_RESULT -> {
                    val dismissed = intent.getBooleanExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, false
                    )
                    val typeStr = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_TYPE
                    )
                    val isCollectionAction =
                        typeStr == ActiveModal.COLLECTION.name &&
                            (intent.hasExtra(DualScreenBroadcasts.EXTRA_COLLECTION_TOGGLE_ID) ||
                                intent.hasExtra(DualScreenBroadcasts.EXTRA_COLLECTION_CREATE_NAME))

                    if (dismissed) {
                        _dualGameDetailState.update { state ->
                            state?.copy(modalType = ActiveModal.NONE)
                        }
                    } else if (isCollectionAction) {
                        // State already updated by the caller
                        // (toggleDualCollectionAtFocus / confirmDualCollectionCreate).
                        // This is our own broadcast echoing back -- ignore it.
                    } else {
                        _dualGameDetailState.update { state ->
                            when (typeStr) {
                                ActiveModal.RATING.name -> state?.copy(
                                    modalType = ActiveModal.NONE,
                                    rating = intent.getIntExtra(
                                        DualScreenBroadcasts.EXTRA_MODAL_VALUE, 0
                                    ).takeIf { it > 0 }
                                )
                                ActiveModal.STATUS.name -> state?.copy(
                                    modalType = ActiveModal.NONE,
                                    status = intent.getStringExtra(
                                        DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED
                                    )
                                )
                                ActiveModal.EMULATOR.name -> state?.copy(
                                    modalType = ActiveModal.NONE,
                                    emulatorCurrentName = run {
                                        val idx = intent.getIntExtra(
                                            DualScreenBroadcasts.EXTRA_SELECTED_INDEX, 0
                                        )
                                        if (idx == 0) null
                                        else state.emulatorNames.getOrNull(idx - 1)
                                    }
                                )
                                else -> state?.copy(
                                    modalType = ActiveModal.NONE
                                )
                            }
                        }
                    }
                    if (!isCollectionAction && !isRolesSwapped) {
                        sendBroadcast(
                            Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
                                .setPackage(packageName)
                        )
                    }
                }
                DualScreenBroadcasts.ACTION_DIRECT_ACTION -> {
                    val type = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_ACTION_TYPE
                    )
                    val gameId = intent.getLongExtra(
                        DualScreenBroadcasts.EXTRA_GAME_ID, -1
                    )
                    if (gameId < 0) return
                    when (type) {
                        "PLAY" -> handleDualPlay(gameId)
                        "DOWNLOAD" -> handleDualDownload(gameId)
                        "REFRESH_METADATA" -> handleDualRefresh(gameId)
                        "DELETE" -> handleDualDelete(gameId)
                        "HIDE" -> handleDualHide(gameId)
                        "SAVE_SWITCH_CHANNEL" -> {
                            val channelName = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_CHANNEL_NAME
                            )
                            handleSaveSwitchChannel(gameId, channelName)
                        }
                        "SAVE_SET_RESTORE_POINT" -> {
                            val channelName = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_CHANNEL_NAME
                            )
                            val timestamp = intent.getLongExtra(
                                DualScreenBroadcasts.EXTRA_SAVE_TIMESTAMP, 0
                            )
                            handleSaveSetRestorePoint(
                                gameId, channelName, timestamp
                            )
                        }
                    }
                }
                DualScreenBroadcasts.ACTION_INLINE_UPDATE -> {
                    val field = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_INLINE_FIELD
                    ) ?: return
                    when (field) {
                        "rating" -> {
                            val v = intent.getIntExtra(
                                DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0
                            )
                            _dualGameDetailState.update { s ->
                                s?.copy(rating = v.takeIf { it > 0 })
                            }
                        }
                        "difficulty" -> {
                            val v = intent.getIntExtra(
                                DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0
                            )
                            _dualGameDetailState.update { s ->
                                s?.copy(userDifficulty = v)
                            }
                        }
                        "status" -> {
                            val v = intent.getStringExtra(
                                DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE
                            )
                            _dualGameDetailState.update { s ->
                                s?.copy(status = v)
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject
    lateinit var gameDao: com.nendo.argosy.data.local.dao.GameDao

    @Inject
    lateinit var platformDao: com.nendo.argosy.data.local.dao.PlatformDao

    @Inject
    lateinit var collectionDao: com.nendo.argosy.data.local.dao.CollectionDao

    @Inject
    lateinit var gamepadInputHandler: GamepadInputHandler

    @Inject
    lateinit var imageCacheManager: ImageCacheManager

    @Inject
    lateinit var romMRepository: RomMRepository

    @Inject
    lateinit var launchRetryTracker: LaunchRetryTracker

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var ambientAudioManager: AmbientAudioManager

    @Inject
    lateinit var ambientLedManager: AmbientLedManager

    @Inject
    lateinit var screenCaptureManager: ScreenCaptureManager

    @Inject
    lateinit var displayAffinityHelper: DisplayAffinityHelper

    @Inject
    lateinit var permissionHelper: PermissionHelper

    @Inject
    lateinit var gameActionsDelegate: GameActionsDelegate

    @Inject
    lateinit var gameLaunchDelegate: GameLaunchDelegate

    @Inject
    lateinit var saveCacheManager: SaveCacheManager

    @Inject
    lateinit var getUnifiedSavesUseCase: GetUnifiedSavesUseCase

    @Inject
    lateinit var restoreCachedSaveUseCase: RestoreCachedSaveUseCase

    @Inject
    lateinit var emulatorResolver: EmulatorResolver

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(this) }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCapturePromptedThisSession = false
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureManager.onPermissionResult(result.resultCode, result.data)
        if (screenCaptureManager.hasPermission.value) {
            screenCaptureManager.startCapture()
        }
    }
    private var hasResumedBefore = false
    private var hadFocusBefore = false
    private var focusLostTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldYieldToEmulator()) {
            Log.d(TAG, "Persisted session found - finishing to avoid stealing focus")
            finish()
            return
        }

        enableEdgeToEdge()
        hideSystemUI()

        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        isRolesSwapped = resolver.isSwapped
        sessionStateStore.setRolesSwapped(isRolesSwapped)

        if (isRolesSwapped) {
            swappedDualHomeViewModel = com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel(
                gameDao = gameDao,
                platformDao = platformDao,
                collectionDao = collectionDao,
                displayAffinityHelper = displayAffinityHelper,
                context = applicationContext
            )
        }

        registerDualScreenReceiver()

        activityScope.launch {
            val prefs = preferencesRepository.preferences.first()
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)

            val validationResult = imageCacheManager.validateAndCleanCache()
            if (validationResult.deletedFiles > 0 || validationResult.clearedPaths > 0) {
                Log.i(TAG, "Cache validation: ${validationResult.deletedFiles} files deleted, ${validationResult.clearedPaths} paths cleared")
            }

            imageCacheManager.resumePendingCache()
            imageCacheManager.resumePendingCoverCache()
            imageCacheManager.resumePendingLogoCache()
            imageCacheManager.resumePendingBadgeCache()

            if (prefs.ambientLedEnabled && !screenCaptureManager.hasPermission.value && !screenCapturePromptedThisSession) {
                screenCapturePromptedThisSession = true
                screenCaptureManager.requestPermission(this@MainActivity, screenCaptureLauncher)
            }
        }

        activityScope.launch {
            launchRetryTracker.retryEvents.collect { intent ->
                Log.d("MainActivity", "Retrying launch intent after quick return")
                startActivity(intent)
            }
        }

        // Schedule pending RA achievement submission if any are queued
        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)


        activityScope.launch {
            var previousHomeApps: Set<String>? = null
            var previousPrimaryColor: Int? = null
            preferencesRepository.preferences.collect { prefs ->
                Logger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.fileLoggingEnabled,
                    level = prefs.fileLogLevel
                )
                SaveDebugLogger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.saveDebugLoggingEnabled
                )
                ambientAudioManager.setEnabled(prefs.ambientAudioEnabled)
                ambientAudioManager.setVolume(prefs.ambientAudioVolume)
                ambientAudioManager.setShuffle(prefs.ambientAudioShuffle)
                ambientAudioManager.setAudioSource(prefs.ambientAudioUri)
                if (prefs.ambientAudioEnabled && prefs.ambientAudioUri != null && hasWindowFocus()) {
                    ambientAudioManager.fadeIn()
                }

                // Update home apps for companion process
                if (previousHomeApps != null && prefs.secondaryHomeApps != previousHomeApps) {
                    updateHomeApps(prefs.secondaryHomeApps)
                }
                previousHomeApps = prefs.secondaryHomeApps

                // Update primary color for companion process
                if (prefs.primaryColor != previousPrimaryColor) {
                    sessionStateStore.setPrimaryColor(prefs.primaryColor)
                }
                previousPrimaryColor = prefs.primaryColor

                // Sync input swap preferences for companion process
                sessionStateStore.setInputSwapPreferences(
                    swapAB = prefs.swapAB,
                    swapXY = prefs.swapXY,
                    swapStartSelect = prefs.swapStartSelect
                )
            }
        }

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArgosyApp(
                        isDualScreenDevice = displayAffinityHelper.hasSecondaryDisplay,
                        isRolesSwapped = isRolesSwapped,
                        isCompanionActive = isCompanionActive,
                        dualScreenShowcase = dualScreenShowcase,
                        dualGameDetailState = dualGameDetailState,
                        dualViewMode = dualViewMode,
                        dualCollectionShowcase = dualCollectionShowcase
                    )
                }
            }
        }

    }

    private fun shouldYieldToEmulator(): Boolean {
        // Don't yield if user explicitly navigated here
        if (intent.data != null || intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            return false
        }
        // Check if a persisted session exists
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return false

        // Only yield if emulator is still in foreground (used within last 15 seconds)
        // If emulator isn't in foreground, the game has ended - clear session and proceed
        val emulatorInForeground = permissionHelper.isPackageInForeground(
            this, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(TAG, "Emulator ${session.emulatorPackage} not in foreground - clearing session")
            runBlocking { preferencesRepository.clearActiveSession() }
            return false
        }

        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) {
            handleHomeIntent(intent)
        }
    }

    private fun handleDeepLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme == "argosy") {
            Log.d(TAG, "Received deep link: $uri")
            _pendingDeepLink.value = uri
            return true
        }
        return false
    }

    fun clearPendingDeepLink() {
        _pendingDeepLink.value = null
    }

    override fun onResume() {
        super.onResume()

        // Notify secondary display that Argosy is in foreground
        broadcastForegroundState(true)


        // Check if we should yield to a running emulator
        if (shouldYieldOnResume()) {
            Log.d(TAG, "Persisted session found on resume - yielding to emulator")
            moveTaskToBack(true)
            return
        }

        // Clear stale session if emulator is no longer running
        clearStaleSession()

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch {
                romMRepository.initialize()
            }
            ambientAudioManager.fadeIn()
        } else {
            // First resume - push input focus to lower screen after startup completes
            // (only in standard mode -- swapped mode keeps focus on main display)
            if (displayAffinityHelper.hasSecondaryDisplay && !isRolesSwapped) {
                window.decorView.postDelayed({
                    sendBroadcast(
                        Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER).setPackage(packageName)
                    )
                }, 500)
            }
        }
        hasResumedBefore = true
    }

    private fun clearStaleSession() {
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return
        val emulatorInForeground = permissionHelper.isPackageInForeground(
            this, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(TAG, "Emulator ${session.emulatorPackage} not in foreground - clearing stale session")
            runBlocking { preferencesRepository.clearActiveSession() }
            broadcastSessionCleared()

            // Stop recovery service if running (SECONDARY_HOME handles display now)
            if (displayAffinityHelper.hasSecondaryDisplay) {
                RecoveryDisplayService.stop(this)
            }
        }
    }

    private fun broadcastSessionCleared() {
        val intent = Intent("com.nendo.argosy.SESSION_CHANGED").apply {
            setPackage(packageName)
            putExtra("game_id", -1L)
        }
        sendBroadcast(intent)
    }

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
        if (type == ActiveModal.NONE) return

        when (type) {
            ActiveModal.EMULATOR -> {
                confirmDualEmulatorSelection()
                return
            }
            ActiveModal.COLLECTION -> {
                toggleDualCollectionAtFocus()
                return
            }
            else -> {}
        }

        val resultIntent = Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
            setPackage(packageName)
            putExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE, type.name)
            when (type) {
                ActiveModal.RATING, ActiveModal.DIFFICULTY ->
                    putExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_VALUE,
                        state.modalRatingValue
                    )
                ActiveModal.STATUS ->
                    putExtra(
                        DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED,
                        state.modalStatusSelected
                    )
                else -> {}
            }
        }
        sendBroadcast(resultIntent)

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
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, true)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    _dualGameDetailState.value?.modalType?.name
                )
            }
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
                emulatorFocusIndex = (state.emulatorFocusIndex + delta)
                    .coerceIn(0, max)
            )
        }
    }

    fun confirmDualEmulatorSelection() {
        val state = _dualGameDetailState.value ?: return
        val index = state.emulatorFocusIndex
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.EMULATOR.name
                )
                putExtra(DualScreenBroadcasts.EXTRA_SELECTED_INDEX, index)
            }
        )
        _dualGameDetailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                emulatorCurrentName = if (index == 0) null
                    else state.emulatorNames.getOrNull(index - 1)
            )
        }
    }

    fun moveDualCollectionFocus(delta: Int) {
        _dualGameDetailState.update { state ->
            val max = state?.collectionItems?.size ?: 0
            state?.copy(
                collectionFocusIndex = (state.collectionFocusIndex + delta)
                    .coerceIn(0, max)
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
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.COLLECTION.name
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_TOGGLE_ID,
                    item.id
                )
            }
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
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.COLLECTION.name
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_CREATE_NAME,
                    name
                )
            }
        )
    }

    private fun handleDualPlay(gameId: Long) {
        gameLaunchDelegate.launchGame(
            scope = activityScope,
            gameId = gameId,
            onLaunch = { intent ->
                val options = displayAffinityHelper.getActivityOptions(
                    forEmulator = true,
                    rolesSwapped = isRolesSwapped
                )
                if (options != null) startActivity(intent, options)
                else startActivity(intent)
            }
        )
    }

    private fun handleDualDownload(gameId: Long) {
        activityScope.launch(Dispatchers.IO) {
            gameActionsDelegate.queueDownload(gameId)
        }
    }

    private fun handleDualRefresh(gameId: Long) {
        activityScope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val isAndroid = game.source == GameSource.ANDROID_APP
            if (isAndroid) gameActionsDelegate.refreshAndroidGameData(gameId)
            else gameActionsDelegate.refreshGameData(gameId)
            sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, "REFRESH_DONE")
                    putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                }
            )
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

    private fun handleDualDelete(gameId: Long) {
        activityScope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (game.source == GameSource.ANDROID_APP) {
                val uninstall = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${game.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(uninstall)
            } else {
                gameActionsDelegate.deleteLocalFile(gameId)
            }
            sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, "DELETE_DONE")
                    putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                }
            )
        }
    }

    private fun handleDualHide(gameId: Long) {
        activityScope.launch(Dispatchers.IO) {
            gameActionsDelegate.deleteLocalFile(gameId)
            gameActionsDelegate.hideGame(gameId)
            _dualGameDetailState.value = null
            sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, "HIDE_DONE")
                }
            )
        }
    }

    private fun handleSaveSwitchChannel(gameId: Long, channelName: String?) {
        activityScope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, game.platformId, game.platformSlug
            )

            gameDao.updateActiveSaveChannel(gameId, channelName)
            gameDao.updateActiveSaveTimestamp(gameId, null)

            if (emulatorId != null) {
                val entries = getUnifiedSavesUseCase(gameId)
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
                            gameDao.updateActiveSaveApplied(gameId, true)
                        }
                        is RestoreCachedSaveUseCase.Result.Error -> {
                            Log.w(TAG, "Channel switch restore failed: ${result.message}")
                        }
                    }
                }
            }

            broadcastSaveActionResult("SAVE_SWITCH_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun handleSaveSetRestorePoint(
        gameId: Long,
        channelName: String?,
        timestamp: Long
    ) {
        activityScope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, game.platformId, game.platformSlug
            )

            gameDao.updateActiveSaveChannel(gameId, channelName)
            gameDao.updateActiveSaveTimestamp(gameId, timestamp)

            if (emulatorId != null) {
                val entries = getUnifiedSavesUseCase(gameId)
                val targetEntry = entries.find {
                    it.channelName == channelName &&
                        it.timestamp.toEpochMilli() == timestamp
                }

                if (targetEntry != null) {
                    val result = restoreCachedSaveUseCase(
                        targetEntry, gameId, emulatorId, false
                    )
                    when (result) {
                        is RestoreCachedSaveUseCase.Result.Restored,
                        is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                            gameDao.updateActiveSaveApplied(gameId, true)
                        }
                        is RestoreCachedSaveUseCase.Result.Error -> {
                            Log.w(TAG, "Restore point apply failed: ${result.message}")
                        }
                    }
                }
            }

            broadcastSaveActionResult("SAVE_RESTORE_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun handleCreateSlot(gameId: Long, name: String) {
        activityScope.launch(Dispatchers.IO) {
            gameDao.updateActiveSaveChannel(gameId, name)
            gameDao.updateActiveSaveTimestamp(gameId, null)
            broadcastSaveActionResult("SAVE_CREATE_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun handleLockAsSlot(gameId: Long, cacheId: Long?, name: String) {
        if (cacheId == null) return
        activityScope.launch(Dispatchers.IO) {
            saveCacheManager.copyToChannel(cacheId, name)
            broadcastSaveActionResult("SAVE_LOCK_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun broadcastSaveActionResult(type: String, gameId: Long) {
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
            }
        )
    }

    private fun broadcastUnifiedSaves(gameId: Long) {
        activityScope.launch(Dispatchers.IO) {
            try {
                val entries = getUnifiedSavesUseCase(gameId)
                val json = entries.map { it.toSaveEntryData() }.toJsonString()
                val game = gameDao.getById(gameId)
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_SAVE_DATA).apply {
                        setPackage(packageName)
                        putExtra(DualScreenBroadcasts.EXTRA_SAVE_DATA_JSON, json)
                        putExtra(
                            DualScreenBroadcasts.EXTRA_ACTIVE_CHANNEL,
                            game?.activeSaveChannel
                        )
                        game?.activeSaveTimestamp?.let {
                            putExtra(
                                DualScreenBroadcasts.EXTRA_ACTIVE_SAVE_TIMESTAMP,
                                it
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast unified saves", e)
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_SAVE_DATA).apply {
                        setPackage(packageName)
                        putExtra(DualScreenBroadcasts.EXTRA_SAVE_DATA_JSON, "[]")
                    }
                )
            }
        }
    }

    fun updateDualSaveNameText(text: String) {
        _dualGameDetailState.update { it?.copy(saveNameText = text) }
    }

    fun confirmDualSaveName() {
        val state = _dualGameDetailState.value ?: return
        val name = state.saveNameText.trim()
        if (name.isBlank()) return
        val gameId = state.gameId

        when (state.saveNamePromptAction) {
            "CREATE_SLOT" -> handleCreateSlot(gameId, name)
            "LOCK_AS_SLOT" -> handleLockAsSlot(
                gameId, state.saveNameCacheId, name
            )
        }

        _dualGameDetailState.update { it?.copy(modalType = ActiveModal.NONE) }
        sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.SAVE_NAME.name
                )
            }
        )
    }

    private fun shouldYieldOnResume(): Boolean {
        // Don't yield if this is the first resume (onCreate just ran)
        if (!hasResumedBefore) return false
        // Check if a game session is active
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return false
        // Only yield if we regained focus very quickly (< 2 sec) - indicates OOM recovery
        val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
        return focusLostTime > 0 && timeSinceFocusLost < 2000
    }

    private fun handleHomeIntent(intent: Intent): Boolean {
        if (intent.hasCategory(Intent.CATEGORY_HOME) && hasResumedBefore) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        ambientAudioManager.suspend()
        // Notify secondary display that Argosy is going to background
        broadcastForegroundState(false)
    }

    private fun broadcastForegroundState(isForeground: Boolean) {
        // Write to SharedPreferences for companion process to read on startup
        sessionStateStore.setArgosyForeground(isForeground)

        val action = if (isForeground) {
            "com.nendo.argosy.FOREGROUND"
        } else {
            "com.nendo.argosy.BACKGROUND"
        }
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)

        // Also update home apps when going to foreground
        if (isForeground) {
            activityScope.launch {
                val prefs = preferencesRepository.preferences.first()
                updateHomeApps(prefs.secondaryHomeApps)
            }
        }
    }

    private fun updateHomeApps(homeApps: Set<String>) {
        // Write to SharedPreferences for companion process to read on startup
        sessionStateStore.setHomeApps(homeApps)

        // Broadcast for live updates
        val intent = Intent("com.nendo.argosy.HOME_APPS_CHANGED").apply {
            setPackage(packageName)
            putStringArrayListExtra("home_apps", ArrayList(homeApps))
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager.stopCapture()
        activityScope.cancel()
        try {
            unregisterReceiver(dualGameSelectedReceiver)
            unregisterReceiver(overlayOpenReceiver)
            unregisterReceiver(dualGameDetailReceiver)
            unregisterReceiver(companionLifecycleReceiver)
            unregisterReceiver(dualViewModeReceiver)
        } catch (_: Exception) {}
    }

    private fun registerDualScreenReceiver() {
        val showcaseFilter = IntentFilter(DualScreenBroadcasts.ACTION_GAME_SELECTED)
        val overlayOpenFilter = IntentFilter().apply {
            addAction(DualScreenBroadcasts.ACTION_OPEN_OVERLAY)
            addAction(DualScreenBroadcasts.ACTION_REFOCUS_UPPER)
        }
        val detailFilter = IntentFilter().apply {
            addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED)
            addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED)
            addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED)
            addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR)
            addAction(DualScreenBroadcasts.ACTION_MODAL_OPEN)
            addAction(DualScreenBroadcasts.ACTION_MODAL_RESULT)
            addAction(DualScreenBroadcasts.ACTION_DIRECT_ACTION)
            addAction(DualScreenBroadcasts.ACTION_INLINE_UPDATE)
        }
        val companionFilter = IntentFilter().apply {
            addAction(DualScreenBroadcasts.ACTION_COMPANION_RESUMED)
            addAction(DualScreenBroadcasts.ACTION_COMPANION_PAUSED)
        }
        val viewModeFilter = IntentFilter().apply {
            addAction(DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED)
            addAction(DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED)
        }
        val flag = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, dualGameSelectedReceiver, showcaseFilter, flag)
        ContextCompat.registerReceiver(this, overlayOpenReceiver, overlayOpenFilter, flag)
        ContextCompat.registerReceiver(this, dualGameDetailReceiver, detailFilter, flag)
        ContextCompat.registerReceiver(this, companionLifecycleReceiver, companionFilter, flag)
        ContextCompat.registerReceiver(this, dualViewModeReceiver, viewModeFilter, flag)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        if (gamepadInputHandler.handleKeyEvent(event)) {
            return true
        }
        // Only handle Home key when not in emulator (gamepad handler didn't consume it)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_HOME) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
            if (_isCompanionActive.value && !isOverlayFocused && !isRolesSwapped) {
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER).setPackage(packageName)
                )
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun refocusMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        )
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInputHandler.handleMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
            // Only emit if we had focus before and lost it briefly (< 1 second = Home while visible)
            if (hadFocusBefore && focusLostTime > 0 && timeSinceFocusLost < 1000) {
                gamepadInputHandler.emitHomeEvent()
            }
            hadFocusBefore = true
            focusLostTime = 0L
            hideSystemUI()
            window.decorView.requestFocus()
            launchRetryTracker.onFocusGained()
            ambientAudioManager.fadeIn()
            ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
            ambientLedManager.clearInGameColors()
            if (!isOverlayFocused) {
                gamepadInputHandler.blockInputFor(200)
            }
        } else {
            focusLostTime = System.currentTimeMillis()
            launchRetryTracker.onFocusLost()
            ambientAudioManager.fadeOut()
            ambientLedManager.setContext(AmbientLedContext.IN_GAME)
        }
    }

    fun requestScreenCapturePermission() {
        screenCaptureManager.requestPermission(this, screenCaptureLauncher)
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
