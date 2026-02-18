package com.nendo.argosy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
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
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DualScreenManager"

class DualScreenManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gameDao: GameDao,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val gameFileDao: GameFileDao,
    private val downloadManager: DownloadManager,
    private val gameActionsDelegate: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val saveCacheManager: SaveCacheManager,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val emulatorResolver: EmulatorResolver,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val sessionStateStore: SessionStateStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val edenContentManager: com.nendo.argosy.data.emulator.EdenContentManager,
    private val notificationManager: com.nendo.argosy.ui.notification.NotificationManager,
    var isRolesSwapped: Boolean = false
) {

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

    private val _pendingOverlayEvent = MutableStateFlow<String?>(null)
    val pendingOverlayEvent: StateFlow<String?> = _pendingOverlayEvent

    var isOverlayFocused = false
    var swappedDualHomeViewModel: DualHomeViewModel? = null
        private set

    private var companionWatchdogJob: Job? = null

    val homeAppsList: List<String>
        get() = sessionStateStore.getHomeApps()?.toList() ?: emptyList()

    fun clearPendingOverlay() {
        _pendingOverlayEvent.value = null
    }

    fun initSwappedViewModel() {
        swappedDualHomeViewModel = DualHomeViewModel(
            gameDao = gameDao,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            downloadQueueDao = downloadQueueDao,
            displayAffinityHelper = displayAffinityHelper,
            context = context
        )
    }

    // --- Broadcast Receivers ---

    private val overlayOpenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_OPEN_OVERLAY -> {
                    isOverlayFocused = true
                    _pendingOverlayEvent.value =
                        intent.getStringExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME)
                            ?: DualScreenBroadcasts.OVERLAY_MENU
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
                    companionWatchdogJob?.cancel()
                    _isCompanionActive.value = true
                    resyncCompanionState()
                }
                DualScreenBroadcasts.ACTION_COMPANION_PAUSED -> {
                    _isCompanionActive.value = false
                    companionWatchdogJob?.cancel()
                    companionWatchdogJob = scope.launch {
                        delay(COMPANION_WATCHDOG_TIMEOUT_MS)
                        val state = _dualGameDetailState.value
                        if (state?.modalType != null &&
                            state.modalType != ActiveModal.NONE
                        ) {
                            _dualGameDetailState.update {
                                it?.copy(modalType = ActiveModal.NONE)
                            }
                            Log.w(TAG,
                                "Companion watchdog: auto-dismissed stale modal")
                        }
                    }
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
                    _dualAppBarFocused.value = intent.getBooleanExtra(
                        DualScreenBroadcasts.EXTRA_IS_APP_BAR_FOCUSED, false
                    )
                    _dualDrawerOpen.value = intent.getBooleanExtra(
                        DualScreenBroadcasts.EXTRA_IS_DRAWER_OPEN, false
                    )
                }
                DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED -> {
                    _dualCollectionShowcase.value = DualCollectionShowcaseState(
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
                    isFavorite = intent.getBooleanExtra("is_favorite", false),
                    isDownloaded = intent.getBooleanExtra("is_downloaded", true)
                )

                val gameId = intent.getLongExtra("game_id", -1)
                if (gameId > 0) {
                    scope.launch(Dispatchers.IO) {
                        val entity = gameDao.getById(gameId) ?: return@launch
                        val rommId = entity.rommId ?: return@launch
                        fetchAchievementsUseCase(rommId, gameId)
                    }
                }
            }
        }
    }

    private val dualGameDetailReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED -> handleGameDetailOpened(intent)
                DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED -> {
                    Log.d("UpdatesDLC", "ACTION_GAME_DETAIL_CLOSED received, currentModal=${_dualGameDetailState.value?.modalType}", Exception("stacktrace"))
                    _dualGameDetailState.value = null
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED -> {
                    val index = intent.getIntExtra(
                        DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, -1
                    )
                    _dualGameDetailState.update { state ->
                        state?.copy(
                            viewerScreenshotIndex = index.takeIf { it >= 0 }
                        )
                    }
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR -> {
                    _dualGameDetailState.update { state ->
                        state?.copy(viewerScreenshotIndex = null)
                    }
                }
                DualScreenBroadcasts.ACTION_MODAL_OPEN -> handleModalOpen(intent)
                DualScreenBroadcasts.ACTION_MODAL_RESULT -> handleModalResult(intent)
                DualScreenBroadcasts.ACTION_DIRECT_ACTION -> handleDirectAction(intent)
                DualScreenBroadcasts.ACTION_INLINE_UPDATE -> handleInlineUpdate(intent)
                "com.nendo.argosy.DOWNLOAD_COMPLETED" -> {
                    val gameId = intent.getLongExtra("game_id", -1)
                    if (gameId > 0 && _dualScreenShowcase.value.gameId == gameId) {
                        _dualScreenShowcase.update { it.copy(isDownloaded = true) }
                    }
                }
            }
        }
    }

    private val companionHomeAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val apps = intent?.getStringArrayListExtra("home_apps")?.toSet() ?: return
            scope.launch {
                preferencesRepository.setSecondaryHomeApps(apps)
            }
        }
    }

    // --- Receiver Handlers ---

    private fun handleGameDetailOpened(intent: Intent) {
        val gameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
        if (gameId == -1L) return
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
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (showcase.gameId != gameId) {
                val platform = platformRepository.getById(game.platformId)
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

    private fun handleModalOpen(intent: Intent) {
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
                        saveNameCacheId = if (cacheId > 0) cacheId else null,
                        saveNameText = ""
                    )
                }
            }
            ActiveModal.UPDATES_DLC -> {
                Log.d("UpdatesDLC", "handleModalOpen: UPDATES_DLC")
                val names = intent.getStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_NAMES
                ) ?: arrayListOf()
                val sizes = intent.getLongArrayExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_SIZES
                ) ?: longArrayOf()
                val types = intent.getStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_TYPES
                ) ?: arrayListOf()
                val downloaded = intent.getBooleanArrayExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_DOWNLOADED
                ) ?: booleanArrayOf()
                val gameFileIds = intent.getLongArrayExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_GAME_FILE_IDS
                ) ?: longArrayOf()

                val allFiles = names.mapIndexed { i, name ->
                    UpdateFileUi(
                        fileName = name,
                        filePath = "",
                        sizeBytes = sizes.getOrElse(i) { 0L },
                        type = try {
                            UpdateFileType.valueOf(types.getOrElse(i) { "UPDATE" })
                        } catch (_: Exception) { UpdateFileType.UPDATE },
                        isDownloaded = downloaded.getOrElse(i) { false },
                        isAppliedToEmulator = false,
                        gameFileId = gameFileIds.getOrElse(i) { -1L }
                            .takeIf { it >= 0 }
                    )
                }

                val updateFiles = allFiles.filter { it.type == UpdateFileType.UPDATE }
                val dlcFiles = allFiles.filter { it.type == UpdateFileType.DLC }

                Log.d("UpdatesDLC", "handleModalOpen: ${updateFiles.size} updates, ${dlcFiles.size} dlc, downloaded=${allFiles.map { "${it.fileName}:${it.isDownloaded}" }}")

                _dualGameDetailState.update { state ->
                    state?.copy(
                        modalType = ActiveModal.UPDATES_DLC,
                        updateFiles = updateFiles,
                        dlcFiles = dlcFiles,
                        updatesPickerFocusIndex = 0,
                        isEdenGame = false
                    )
                }

                val currentGameId = _dualGameDetailState.value?.gameId ?: -1L
                Log.d("UpdatesDLC", "handleModalOpen: checking Eden for gameId=$currentGameId")
                if (currentGameId > 0) {
                    scope.launch(Dispatchers.IO) {
                        val game = gameDao.getById(currentGameId) ?: return@launch
                        val emId = emulatorResolver.getEmulatorIdForGame(
                            currentGameId, game.platformId, game.platformSlug
                        )
                        Log.d("UpdatesDLC", "handleModalOpen: emulatorId=$emId, isEden=${emId == "eden"}")
                        if (emId == "eden") {
                            _dualGameDetailState.update { s ->
                                s?.copy(isEdenGame = true)
                            }
                        }
                    }
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
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    private fun handleModalResult(intent: Intent) {
        val typeStr = intent.getStringExtra(
            DualScreenBroadcasts.EXTRA_MODAL_TYPE
        )
        val dismissed = intent.getBooleanExtra(
            DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, false
        )
        Log.d("UpdatesDLC", "handleModalResult: type=$typeStr, dismissed=$dismissed, currentModal=${_dualGameDetailState.value?.modalType}", Exception("stacktrace"))
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
                    else -> state?.copy(modalType = ActiveModal.NONE)
                }
            }
        }
        if (!isCollectionAction && !isRolesSwapped) {
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
                    .setPackage(context.packageName)
            )
        }
    }

    private fun handleDirectAction(intent: Intent) {
        val type = intent.getStringExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE)
        val gameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
        if (gameId < 0) return
        when (type) {
            "PLAY" -> {
                val channelName = intent.getStringExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME)
                handleDualPlay(gameId, channelName)
            }
            "DOWNLOAD" -> handleDualDownload(gameId)
            "REFRESH_METADATA" -> handleDualRefresh(gameId)
            "DELETE" -> handleDualDelete(gameId)
            "HIDE" -> handleDualHide(gameId)
            "SAVE_SWITCH_CHANNEL" -> {
                val channelName = intent.getStringExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME)
                handleSaveSwitchChannel(gameId, channelName)
            }
            "SAVE_SET_RESTORE_POINT" -> {
                val channelName = intent.getStringExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME)
                val timestamp = intent.getLongExtra(DualScreenBroadcasts.EXTRA_SAVE_TIMESTAMP, 0)
                handleSaveSetRestorePoint(gameId, channelName, timestamp)
            }
            "DOWNLOAD_UPDATE_FILE" -> {
                val fileIdStr = intent.getStringExtra(
                    DualScreenBroadcasts.EXTRA_CHANNEL_NAME
                )
                val fileId = fileIdStr?.toLongOrNull()
                if (fileId != null) {
                    scope.launch(Dispatchers.IO) {
                        val gameFile = gameFileDao.getById(fileId) ?: return@launch
                        val game = gameDao.getById(gameId) ?: return@launch
                        val rommFileId = gameFile.rommFileId ?: return@launch
                        downloadManager.enqueueGameFileDownload(
                            gameId = gameId,
                            gameFileId = fileId,
                            rommFileId = rommFileId,
                            fileName = gameFile.fileName,
                            category = gameFile.category,
                            gameTitle = game.title,
                            platformSlug = game.platformSlug,
                            coverPath = game.coverPath,
                            expectedSizeBytes = gameFile.fileSize
                        )
                    }
                }
            }
            "INSTALL_UPDATE_FILE" -> {
                Log.d("UpdatesDLC", "handleDirectAction: INSTALL_UPDATE_FILE gameId=$gameId")
                scope.launch(Dispatchers.IO) {
                    val game = gameDao.getById(gameId)
                    Log.d("UpdatesDLC", "INSTALL: game=${game?.title}, localPath=${game?.localPath}")
                    if (game == null) return@launch
                    val localPath = game.localPath ?: return@launch
                    val gameDir = java.io.File(localPath).parent ?: return@launch
                    Log.d("UpdatesDLC", "INSTALL: registering dir=$gameDir with Eden")
                    val success = edenContentManager.registerDirectory(gameDir)
                    Log.d("UpdatesDLC", "INSTALL: Eden registerDirectory result=$success")
                    if (success) {
                        _dualGameDetailState.update { s ->
                            s?.copy(
                                updateFiles = s.updateFiles.map {
                                    it.copy(isAppliedToEmulator = true)
                                },
                                dlcFiles = s.dlcFiles.map {
                                    it.copy(isAppliedToEmulator = true)
                                }
                            )
                        }
                        notificationManager.showSuccess(
                            "Applied to Eden. Restart Eden to load changes."
                        )
                    } else {
                        notificationManager.showError(
                            "Failed to register directory with Eden"
                        )
                    }
                }
            }
        }
    }

    private fun handleInlineUpdate(intent: Intent) {
        val field = intent.getStringExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD) ?: return
        when (field) {
            "rating" -> {
                val v = intent.getIntExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0)
                _dualGameDetailState.update { s ->
                    s?.copy(rating = v.takeIf { it > 0 })
                }
            }
            "difficulty" -> {
                val v = intent.getIntExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0)
                _dualGameDetailState.update { s ->
                    s?.copy(userDifficulty = v)
                }
            }
            "status" -> {
                val v = intent.getStringExtra(DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE)
                _dualGameDetailState.update { s ->
                    s?.copy(status = v)
                }
            }
            "updates_focus" -> {
                val idx = intent.getIntExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0)
                _dualGameDetailState.update { s ->
                    s?.copy(updatesPickerFocusIndex = idx)
                }
            }
        }
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
            ActiveModal.COLLECTION -> {
                toggleDualCollectionAtFocus()
                return
            }
            else -> {}
        }

        val resultIntent = Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
            setPackage(context.packageName)
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
        context.sendBroadcast(resultIntent)

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
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
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
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
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
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
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
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
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
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.SAVE_NAME.name
                )
            }
        )
    }

    // --- Game Actions ---

    private fun handleDualPlay(gameId: Long, channelName: String? = null) {
        gameLaunchDelegate.launchGame(
            scope = scope,
            gameId = gameId,
            channelName = channelName,
            onLaunch = { intent ->
                val options = displayAffinityHelper.getActivityOptions(
                    forEmulator = true,
                    rolesSwapped = isRolesSwapped
                )
                if (options != null) context.startActivity(intent, options)
                else context.startActivity(intent)
            }
        )
    }

    private fun handleDualDownload(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            gameActionsDelegate.queueDownload(gameId)
        }
    }

    private fun handleDualRefresh(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            val isAndroid = game.source == GameSource.ANDROID_APP
            if (isAndroid) gameActionsDelegate.refreshAndroidGameData(gameId)
            else gameActionsDelegate.refreshGameData(gameId)
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(context.packageName)
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
        scope.launch(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@launch
            if (game.source == GameSource.ANDROID_APP) {
                val uninstall = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${game.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uninstall)
            } else {
                gameActionsDelegate.deleteLocalFile(gameId)
            }
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(context.packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, "DELETE_DONE")
                    putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                }
            )
        }
    }

    private fun handleDualHide(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            gameActionsDelegate.deleteLocalFile(gameId)
            gameActionsDelegate.hideGame(gameId)
            _dualGameDetailState.value = null
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                    setPackage(context.packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, "HIDE_DONE")
                }
            )
        }
    }

    // --- Save Operations ---

    private fun handleSaveSwitchChannel(gameId: Long, channelName: String?) {
        scope.launch(Dispatchers.IO) {
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
                } else {
                    restoreCachedSaveUseCase.clearActiveSave(gameId, emulatorId)
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
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
            gameDao.updateActiveSaveChannel(gameId, name)
            gameDao.updateActiveSaveTimestamp(gameId, null)
            broadcastSaveActionResult("SAVE_CREATE_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun handleLockAsSlot(gameId: Long, cacheId: Long?, name: String) {
        if (cacheId == null) return
        scope.launch(Dispatchers.IO) {
            saveCacheManager.copyToChannel(cacheId, name)
            broadcastSaveActionResult("SAVE_LOCK_DONE", gameId)
            broadcastUnifiedSaves(gameId)
        }
    }

    private fun broadcastSaveActionResult(type: String, gameId: Long) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
            }
        )
    }

    private fun broadcastUnifiedSaves(gameId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val entries = getUnifiedSavesUseCase(gameId)
                val json = entries.map { it.toSaveEntryData() }.toJsonString()
                val game = gameDao.getById(gameId)
                context.sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_SAVE_DATA).apply {
                        setPackage(context.packageName)
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
            }
        }
    }

    // --- Companion Sync ---

    fun resyncCompanionState() {
        broadcastForegroundState(true)
        val detailState = _dualGameDetailState.value
        if (detailState?.modalType != null &&
            detailState.modalType != ActiveModal.NONE
        ) {
            _dualGameDetailState.update {
                it?.copy(modalType = ActiveModal.NONE)
            }
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                    setPackage(context.packageName)
                    putExtra(DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, true)
                }
            )
        }
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_CLOSE_OVERLAY).apply {
                setPackage(context.packageName)
            }
        )
        if (detailState != null && detailState.gameId > 0) {
            context.sendBroadcast(
                Intent(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED).apply {
                    setPackage(context.packageName)
                    putExtra(
                        DualScreenBroadcasts.EXTRA_GAME_ID,
                        detailState.gameId
                    )
                }
            )
            broadcastUnifiedSaves(detailState.gameId)
        }
    }

    fun broadcastForegroundState(isForeground: Boolean) {
        sessionStateStore.setArgosyForeground(isForeground)
        val isWizard = sessionStateStore.isWizardActive()
        val action = if (isForeground) {
            "com.nendo.argosy.FOREGROUND"
        } else {
            "com.nendo.argosy.BACKGROUND"
        }
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_WIZARD_ACTIVE, isWizard)
            }
        )
        if (isForeground) {
            scope.launch {
                val prefs = preferencesRepository.preferences.first()
                updateHomeApps(prefs.secondaryHomeApps)
            }
        }
    }

    fun broadcastWizardState(isActive: Boolean) {
        sessionStateStore.setWizardActive(isActive)
        if (!isActive) {
            sessionStateStore.setFirstRunComplete(true)
        }
        context.sendBroadcast(
            Intent(ACTION_WIZARD_STATE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_WIZARD_ACTIVE, isActive)
            }
        )
    }

    companion object {
        const val ACTION_WIZARD_STATE = "com.nendo.argosy.WIZARD_STATE"
        const val EXTRA_WIZARD_ACTIVE = "wizard_active"
        private const val COMPANION_WATCHDOG_TIMEOUT_MS = 5000L
    }

    fun updateHomeApps(homeApps: Set<String>) {
        sessionStateStore.setHomeApps(homeApps)
        context.sendBroadcast(
            Intent("com.nendo.argosy.HOME_APPS_CHANGED").apply {
                setPackage(context.packageName)
                putStringArrayListExtra("home_apps", ArrayList(homeApps))
            }
        )
    }

    fun broadcastSessionCleared() {
        context.sendBroadcast(
            Intent("com.nendo.argosy.SESSION_CHANGED").apply {
                setPackage(context.packageName)
                putExtra("game_id", -1L)
            }
        )
    }

    // --- Registration ---

    fun registerReceivers() {
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
            addAction("com.nendo.argosy.DOWNLOAD_COMPLETED")
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
        ContextCompat.registerReceiver(context, dualGameSelectedReceiver, showcaseFilter, flag)
        ContextCompat.registerReceiver(context, overlayOpenReceiver, overlayOpenFilter, flag)
        ContextCompat.registerReceiver(context, dualGameDetailReceiver, detailFilter, flag)
        ContextCompat.registerReceiver(context, companionLifecycleReceiver, companionFilter, flag)
        ContextCompat.registerReceiver(context, dualViewModeReceiver, viewModeFilter, flag)
        val companionHomeAppsFilter = IntentFilter("com.nendo.argosy.COMPANION_HOME_APPS_CHANGED")
        ContextCompat.registerReceiver(context, companionHomeAppsReceiver, companionHomeAppsFilter, flag)
    }

    fun unregisterReceivers() {
        try {
            context.unregisterReceiver(dualGameSelectedReceiver)
            context.unregisterReceiver(overlayOpenReceiver)
            context.unregisterReceiver(dualGameDetailReceiver)
            context.unregisterReceiver(companionLifecycleReceiver)
            context.unregisterReceiver(dualViewModeReceiver)
            context.unregisterReceiver(companionHomeAppsReceiver)
        } catch (_: Exception) {}
    }

    private fun refocusMain() {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        )
    }
}
