package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.parseSaveEntryDataList
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class SecondaryHomeBroadcastReceiverManager(
    private val context: Context,
    private val viewModel: SecondaryHomeViewModel,
    private val dualHomeViewModel: DualHomeViewModel,
    private val broadcasts: SecondaryHomeBroadcastHelper,
    private val inputHandler: SecondaryHomeInputHandler,
    private val host: ReceiverHost,
    private val showcaseState: MutableStateFlow<DualHomeShowcaseState>,
    private val showcaseViewMode: MutableStateFlow<String>,
    private val showcaseCollectionState: MutableStateFlow<DualCollectionShowcaseState>,
    private val showcaseGameDetailState: MutableStateFlow<DualGameDetailUpperState?>
) {

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
        const val ACTION_WIZARD_STATE = "com.nendo.argosy.WIZARD_STATE"
        const val EXTRA_WIZARD_ACTIVE = "wizard_active"
    }

    interface ReceiverHost {
        val isShowcaseRole: Boolean
        val useDualScreenMode: Boolean
        val swapAB: Boolean
        val swapXY: Boolean
        val swapStartSelect: Boolean
        val isArgosyForeground: Boolean
        val isGameActive: Boolean
        val currentScreen: CompanionScreen
        val dualGameDetailViewModel: DualGameDetailViewModel?
        val homeApps: List<String>

        fun onForegroundChanged(isForeground: Boolean)
        fun onWizardStateChanged(isActive: Boolean)
        fun onSaveDirtyChanged(isDirty: Boolean)
        fun onSessionStarted(
            gameId: Long, isHardcore: Boolean, channelName: String?
        )
        fun onSessionEnded()
        fun onHomeAppsChanged(apps: List<String>)
        fun onLibraryRefresh()
        fun refocusSelf()
        fun returnToHome()
        fun lifecycleLaunch(block: suspend () -> Unit)
    }

    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ARGOSY_FOREGROUND -> {
                    host.onForegroundChanged(true)
                    if (intent.hasExtra(EXTRA_WIZARD_ACTIVE)) {
                        host.onWizardStateChanged(intent.getBooleanExtra(EXTRA_WIZARD_ACTIVE, false))
                    }
                }
                ACTION_ARGOSY_BACKGROUND ->
                    host.onForegroundChanged(false)
            }
        }
    }

    private val wizardStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WIZARD_STATE) {
                host.onWizardStateChanged(
                    intent.getBooleanExtra(EXTRA_WIZARD_ACTIVE, false)
                )
            }
        }
    }

    private val saveStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SAVE_STATE_CHANGED) {
                host.onSaveDirtyChanged(
                    intent.getBooleanExtra(EXTRA_IS_DIRTY, false)
                )
            }
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_SESSION_CHANGED) return
            val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)
            if (gameId > 0) {
                host.onSessionStarted(
                    gameId = gameId,
                    isHardcore = intent.getBooleanExtra(
                        EXTRA_IS_HARDCORE, false
                    ),
                    channelName = intent.getStringExtra(
                        EXTRA_CHANNEL_NAME
                    )
                )
            } else {
                host.onSessionEnded()
            }
        }
    }

    private val homeAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HOME_APPS_CHANGED) {
                val apps = intent.getStringArrayListExtra(
                    EXTRA_HOME_APPS
                )?.toList() ?: emptyList()
                host.onHomeAppsChanged(apps)
            }
        }
    }

    private val libraryRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LIBRARY_REFRESH,
                ACTION_DOWNLOAD_COMPLETED -> {
                    host.onLibraryRefresh()
                    if (intent.action == ACTION_DOWNLOAD_COMPLETED) {
                        val gameId = intent.getLongExtra(
                            EXTRA_GAME_ID, -1
                        )
                        if (gameId > 0 && showcaseState.value.gameId == gameId) {
                            showcaseState.value = showcaseState.value.copy(isDownloaded = true)
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

    private val forwardedKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_FORWARD_KEY) return
            val keyCode = intent.getIntExtra(DualScreenBroadcasts.EXTRA_KEY_CODE, 0)
            if (keyCode == 0) return
            val swapAB = intent.getBooleanExtra("swap_ab", host.swapAB)
            val swapXY = intent.getBooleanExtra("swap_xy", host.swapXY)
            val swapStartSelect = intent.getBooleanExtra("swap_start_select", host.swapStartSelect)
            val gamepadEvent = mapKeycodeToGamepadEvent(
                keyCode, swapAB, swapXY, swapStartSelect
            ) ?: return
            inputHandler.routeInput(
                gamepadEvent, host.useDualScreenMode, host.isArgosyForeground,
                host.isGameActive, host.currentScreen
            )
        }
    }

    private val refocusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DualScreenBroadcasts.ACTION_REFOCUS_LOWER) {
                host.refocusSelf()
            }
        }
    }

    private val modalResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_MODAL_RESULT) return
            val vm = host.dualGameDetailViewModel ?: return

            val dismissed = intent.getBooleanExtra(
                DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, false
            )
            if (dismissed) {
                when (vm.activeModal.value) {
                    ActiveModal.COLLECTION -> vm.dismissCollectionModal()
                    ActiveModal.EMULATOR -> vm.dismissPicker()
                    else -> vm.dismissPicker()
                }
                host.refocusSelf()
                return
            }

            handleModalResult(vm, intent)
        }
    }

    private fun handleModalResult(vm: DualGameDetailViewModel, intent: Intent) {
        val type = intent.getStringExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE)
        when (type) {
            ActiveModal.RATING.name, ActiveModal.DIFFICULTY.name -> {
                vm.setPickerValue(
                    intent.getIntExtra(DualScreenBroadcasts.EXTRA_MODAL_VALUE, 0)
                )
                vm.confirmPicker()
                host.refocusSelf()
            }
            ActiveModal.STATUS.name -> {
                val value = intent.getStringExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED
                ) ?: return
                vm.setStatusSelection(value)
                vm.confirmPicker()
                host.refocusSelf()
            }
            ActiveModal.EMULATOR.name -> {
                val index = intent.getIntExtra(
                    DualScreenBroadcasts.EXTRA_SELECTED_INDEX, -1
                )
                if (index >= 0) vm.confirmEmulatorByIndex(index)
                else vm.dismissPicker()
                host.refocusSelf()
            }
            ActiveModal.SAVE_NAME.name -> host.refocusSelf()
            ActiveModal.COLLECTION.name -> handleCollectionResult(vm, intent)
        }
    }

    private fun handleCollectionResult(vm: DualGameDetailViewModel, intent: Intent) {
        val createName = intent.getStringExtra(
            DualScreenBroadcasts.EXTRA_COLLECTION_CREATE_NAME
        )
        if (createName != null) {
            vm.createAndAddToCollection(createName)
            host.lifecycleLaunch {
                kotlinx.coroutines.delay(100)
                broadcasts.broadcastCollectionModalOpen(vm)
            }
            return
        }
        val toggleId = intent.getLongExtra(
            DualScreenBroadcasts.EXTRA_COLLECTION_TOGGLE_ID, -1
        )
        if (toggleId > 0) vm.toggleCollection(toggleId)
    }

    private val saveDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_SAVE_DATA) return
            val vm = host.dualGameDetailViewModel ?: return
            val json = intent.getStringExtra(
                DualScreenBroadcasts.EXTRA_SAVE_DATA_JSON
            ) ?: return
            val entries = parseSaveEntryDataList(json)
            val activeChannel = intent.getStringExtra(
                DualScreenBroadcasts.EXTRA_ACTIVE_CHANNEL
            )
            val activeTimestamp = if (intent.hasExtra(
                    DualScreenBroadcasts.EXTRA_ACTIVE_SAVE_TIMESTAMP
                )
            ) {
                intent.getLongExtra(DualScreenBroadcasts.EXTRA_ACTIVE_SAVE_TIMESTAMP, 0)
            } else null
            vm.loadUnifiedSaves(entries, activeChannel, activeTimestamp)
        }
    }

    private val directActionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_DIRECT_ACTION) return
            val type = intent.getStringExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE)
            val vm = host.dualGameDetailViewModel ?: return
            val actionGameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
            when (type) {
                "REFRESH_DONE", "DELETE_DONE" -> {
                    if (actionGameId > 0) vm.loadGame(actionGameId)
                }
                "HIDE_DONE" -> host.returnToHome()
                "SAVE_SWITCH_DONE", "SAVE_RESTORE_DONE",
                "SAVE_CREATE_DONE", "SAVE_LOCK_DONE" -> { }
            }
        }
    }

    private val showcaseGameSelectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DualScreenBroadcasts.ACTION_GAME_SELECTED) return
            showcaseState.value = DualHomeShowcaseState(
                gameId = intent.getLongExtra("game_id", -1),
                title = intent.getStringExtra("title") ?: "",
                coverPath = intent.getStringExtra("cover_path"),
                backgroundPath = intent.getStringExtra("background_path"),
                platformName = intent.getStringExtra("platform_name") ?: "",
                platformSlug = intent.getStringExtra("platform_slug") ?: "",
                playTimeMinutes = intent.getIntExtra("play_time_minutes", 0),
                lastPlayedAt = intent.getLongExtra("last_played_at", 0),
                status = intent.getStringExtra("status"),
                communityRating = intent.getFloatExtra("community_rating", 0f)
                    .takeIf { it > 0f },
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

    private val showcaseViewModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED -> {
                    showcaseViewMode.value = intent.getStringExtra(
                        DualScreenBroadcasts.EXTRA_VIEW_MODE
                    ) ?: "CAROUSEL"
                }
                DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED -> {
                    showcaseCollectionState.value = DualCollectionShowcaseState(
                        name = intent.getStringExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_NAME_DISPLAY
                        ) ?: "",
                        description = intent.getStringExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_DESCRIPTION
                        ),
                        coverPaths = intent.getStringArrayListExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_COVER_PATHS
                        )?.toList() ?: emptyList(),
                        gameCount = intent.getIntExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_GAME_COUNT, 0
                        ),
                        platformSummary = intent.getStringExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_PLATFORM_SUMMARY
                        ) ?: "",
                        totalPlaytimeMinutes = intent.getIntExtra(
                            DualScreenBroadcasts.EXTRA_COLLECTION_TOTAL_PLAYTIME, 0
                        )
                    )
                }
            }
        }
    }

    private val showcaseGameDetailReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED ->
                    handleShowcaseDetailOpened(intent)
                DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED ->
                    showcaseGameDetailState.value = null
                DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED -> {
                    val index = intent.getIntExtra(
                        DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, -1
                    )
                    showcaseGameDetailState.value =
                        showcaseGameDetailState.value?.copy(
                            viewerScreenshotIndex = index.takeIf { it >= 0 }
                        )
                }
                DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR ->
                    showcaseGameDetailState.value =
                        showcaseGameDetailState.value?.copy(viewerScreenshotIndex = null)
                DualScreenBroadcasts.ACTION_INLINE_UPDATE ->
                    handleShowcaseInlineUpdate(intent)
            }
        }
    }

    private fun handleShowcaseDetailOpened(intent: Intent) {
        val gameId = intent.getLongExtra(DualScreenBroadcasts.EXTRA_GAME_ID, -1)
        if (gameId == -1L) return
        val showcase = showcaseState.value
        showcaseGameDetailState.value = if (showcase.gameId == gameId) {
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

    private fun handleShowcaseInlineUpdate(intent: Intent) {
        val field = intent.getStringExtra(
            DualScreenBroadcasts.EXTRA_INLINE_FIELD
        ) ?: return
        when (field) {
            "rating" -> {
                val v = intent.getIntExtra(
                    DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0
                )
                showcaseGameDetailState.value =
                    showcaseGameDetailState.value?.copy(rating = v.takeIf { it > 0 })
            }
            "difficulty" -> {
                val v = intent.getIntExtra(
                    DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, 0
                )
                showcaseGameDetailState.value =
                    showcaseGameDetailState.value?.copy(userDifficulty = v)
            }
            "status" -> {
                val v = intent.getStringExtra(
                    DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE
                )
                showcaseGameDetailState.value =
                    showcaseGameDetailState.value?.copy(status = v)
            }
        }
    }

    private val allReceivers = mutableListOf<BroadcastReceiver>()

    fun registerAll() {
        val flag = ContextCompat.RECEIVER_NOT_EXPORTED

        register(foregroundReceiver, IntentFilter().apply {
            addAction(ACTION_ARGOSY_FOREGROUND)
            addAction(ACTION_ARGOSY_BACKGROUND)
        }, flag)

        register(wizardStateReceiver,
            IntentFilter(ACTION_WIZARD_STATE), flag)
        register(saveStateReceiver,
            IntentFilter(ACTION_SAVE_STATE_CHANGED), flag)
        register(sessionReceiver,
            IntentFilter(ACTION_SESSION_CHANGED), flag)
        register(homeAppsReceiver,
            IntentFilter(ACTION_HOME_APPS_CHANGED), flag)

        register(libraryRefreshReceiver, IntentFilter().apply {
            addAction(ACTION_LIBRARY_REFRESH)
            addAction(ACTION_DOWNLOAD_COMPLETED)
        }, flag)

        register(overlayCloseReceiver, IntentFilter().apply {
            addAction(DualScreenBroadcasts.ACTION_CLOSE_OVERLAY)
            addAction(DualScreenBroadcasts.ACTION_BACKGROUND_FORWARD)
        }, flag)

        register(forwardedKeyReceiver,
            IntentFilter(DualScreenBroadcasts.ACTION_FORWARD_KEY), flag)
        register(refocusReceiver,
            IntentFilter(DualScreenBroadcasts.ACTION_REFOCUS_LOWER), flag)
        register(modalResultReceiver,
            IntentFilter(DualScreenBroadcasts.ACTION_MODAL_RESULT), flag)
        register(directActionResultReceiver,
            IntentFilter(DualScreenBroadcasts.ACTION_DIRECT_ACTION), flag)
        register(saveDataReceiver,
            IntentFilter(DualScreenBroadcasts.ACTION_SAVE_DATA), flag)

        if (host.isShowcaseRole) {
            register(showcaseGameSelectedReceiver,
                IntentFilter(DualScreenBroadcasts.ACTION_GAME_SELECTED), flag)
            register(showcaseViewModeReceiver, IntentFilter().apply {
                addAction(DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED)
                addAction(DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED)
            }, flag)
            register(showcaseGameDetailReceiver, IntentFilter().apply {
                addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED)
                addAction(DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED)
                addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED)
                addAction(DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR)
                addAction(DualScreenBroadcasts.ACTION_INLINE_UPDATE)
            }, flag)
        }
    }

    fun unregisterAll() {
        for (receiver in allReceivers) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
        allReceivers.clear()
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter, flag: Int) {
        ContextCompat.registerReceiver(context, receiver, filter, flag)
        allReceivers.add(receiver)
    }
}
