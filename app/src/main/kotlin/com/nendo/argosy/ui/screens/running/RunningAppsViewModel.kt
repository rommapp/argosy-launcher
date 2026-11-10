package com.nendo.argosy.ui.screens.running

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import com.nendo.argosy.data.emulator.background.TaskSnapshot
import com.nendo.argosy.data.emulator.background.TaskSnapshots
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.PlayStatsRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.social.SocialRepository
import java.time.Instant
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.GameStart
import com.nendo.argosy.util.AttachedScreen
import com.nendo.argosy.util.ScreenCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RunningAppUi(
    val taskId: Int,
    val packageName: String,
    val iconPackageName: String,
    val label: String,
    val gameId: Long?,
    val gameTitle: String?,
    val memoryMb: Long?,
    val screenNumber: Int?,
    val snapshotPath: String?,
    val snapshotTakenAtMillis: Long,
    val screenSnapshotPath: String?,
    val screenSnapshotTakenAtMillis: Long,
    val details: RunningGameDetails?,
    val slotIndex: Int?,
    val platformSlug: String?
)

data class RunningGameDetails(
    val platformName: String?,
    val playedThisRunMs: Long,
    val raEarned: Int,
    val raTotal: Int,
    val hardcore: Boolean,
    val lastSavedAtMillis: Long?,
    val friendsPlaying: List<RunningFriendUi>
)

data class RunningFriendUi(
    val userId: String,
    val displayName: String,
    val avatarColor: String,
    val avatarPngBase64: String?
)

enum class RunningMenuAction(@StringRes val labelRes: Int) {
    SWITCH_TO(R.string.running_menu_switch_to),
    CLOSE(R.string.running_menu_close)
}

data class RunningMenu(val taskId: Int, val focusIndex: Int = 0)

sealed interface RunningConfirm {
    data class CloseGame(val taskId: Int, val title: String) : RunningConfirm
    data class CloseAll(val taskIds: List<Int>) : RunningConfirm
}

data class RunningAppsUiState(
    val apps: List<RunningAppUi> = emptyList(),
    val isLoading: Boolean = true,
    val showsSnapshots: Boolean = false,
    val focusedIndex: Int = 0,
    val menu: RunningMenu? = null,
    val confirm: RunningConfirm? = null,
    val closing: Map<Int, MakeRoomStage> = emptyMap(),
    val resumingTaskId: Int? = null
) {
    val focusedApp: RunningAppUi? get() = apps.getOrNull(focusedIndex)

    val menuApp: RunningAppUi? get() = menu?.let { open -> apps.firstOrNull { it.taskId == open.taskId } }
}

@HiltViewModel
class RunningAppsViewModel @Inject constructor(
    private val backgroundGames: BackgroundGames,
    private val taskSnapshots: TaskSnapshots,
    private val appsRepository: AppsRepository,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val saveCacheManager: SaveCacheManager,
    private val socialRepository: SocialRepository,
    private val screenCatalog: ScreenCatalog,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningAppsUiState())
    val uiState: StateFlow<RunningAppsUiState> = _uiState.asStateFlow()

    private val refreshLock = Mutex()
    private val labels = mutableMapOf<String, String>()
    private val platformNames = mutableMapOf<Long, String>()
    private var pollJob: Job? = null

    private val _openGame = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openGame: SharedFlow<Long> = _openGame.asSharedFlow()

    init {
        viewModelScope.launch {
            merge(
                gameLaunchDelegate.discPickerState.map { it?.gameId },
                gameLaunchDelegate.variantPickerState.map { it?.gameId },
                gameLaunchDelegate.memcardPickerState.map { it?.gameId }
            ).filterNotNull().collect { gameId ->
                gameLaunchDelegate.dismissDiscPicker()
                gameLaunchDelegate.dismissVariantPicker()
                gameLaunchDelegate.dismissMemcardPicker()
                _openGame.emit(gameId)
            }
        }
    }

    fun onVisible() {
        _uiState.update { it.copy(resumingTaskId = null) }
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refresh(duringClose: Boolean = false) {
        if (!duringClose && _uiState.value.closing.isNotEmpty()) return
        val showsSnapshots = backgroundGames.isSupported
        val rows = refreshLock.withLock {
            withContext(Dispatchers.IO) {
                val screens = screenCatalog.attachedScreens().takeIf { it.size > 1 }
                val apps = backgroundGames.refresh()
                val snapshots = if (showsSnapshots) taskSnapshots.fetch(apps.map { it.taskId }) else emptyMap()
                apps.map { it.toUi(screens, snapshots[it.taskId]) }
            }
        }
        _uiState.update { state ->
            val focusedTask = state.focusedApp?.taskId
            val kept = rows.indexOfFirst { it.taskId == focusedTask }
            val focusedIndex = if (kept >= 0) kept else state.focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            val menu = state.menu?.takeIf { open -> rows.any { it.taskId == open.taskId } }
            state.copy(
                apps = rows,
                isLoading = false,
                showsSnapshots = showsSnapshots,
                focusedIndex = focusedIndex,
                menu = menu
            )
        }
    }

    private suspend fun RunningApp.toUi(screens: List<AttachedScreen>?, snapshot: TaskSnapshot?): RunningAppUi {
        val entity = game?.gameId?.let { gameRepository.getById(it) }
        return RunningAppUi(
            taskId = taskId,
            packageName = packageName,
            iconPackageName = if (packageName == EmulatorRegistry.BUILTIN_PACKAGE) {
                com.nendo.argosy.BuildConfig.APPLICATION_ID
            } else {
                packageName
            },
            label = labels.getOrPut(packageName) {
                appsRepository.getAppLabel(packageName)
                    ?: EmulatorRegistry.getByPackage(packageName)?.displayName
                    ?: packageName
            },
            gameId = entity?.id,
            gameTitle = entity?.title,
            memoryMb = rssKb?.let { it / KB_PER_MB },
            screenNumber = screens?.firstOrNull { it.displayId == displayId }?.number,
            snapshotPath = snapshot?.path,
            snapshotTakenAtMillis = snapshot?.takenAtMillis ?: 0L,
            screenSnapshotPath = snapshot?.screen?.path,
            screenSnapshotTakenAtMillis = snapshot?.screen?.takenAtMillis ?: 0L,
            details = entity?.let { gameDetails(it, this) },
            slotIndex = slotIndex,
            platformSlug = entity?.platformSlug
        )
    }

    private suspend fun gameDetails(game: GameEntity, app: RunningApp): RunningGameDetails {
        val playedThisRunMs = app.runStartedAtMillis
            ?.let { playStatsRepository.activePlayMsSince(game.id, Instant.ofEpochMilli(it)) }
            ?: 0L
        return RunningGameDetails(
            platformName = platformName(game.platformId),
            playedThisRunMs = playedThisRunMs,
            raEarned = game.earnedAchievementCount,
            raTotal = game.achievementCount,
            hardcore = app.hardcore,
            lastSavedAtMillis = saveCacheManager.getMostRecentSave(game.id)?.cachedAt?.toEpochMilli(),
            friendsPlaying = friendsPlaying(game.igdbId)
        )
    }

    private suspend fun platformName(platformId: Long): String? =
        platformNames[platformId]
            ?: platformRepository.getById(platformId)?.name?.also { platformNames[platformId] = it }

    private fun friendsPlaying(igdbId: Long?): List<RunningFriendUi> {
        if (igdbId == null) return emptyList()
        return socialRepository.friends.value
            .filter { it.currentGame?.igdbId?.toLong() == igdbId }
            .map { friend ->
                RunningFriendUi(
                    userId = friend.id,
                    displayName = friend.displayName,
                    avatarColor = friend.avatarColor,
                    avatarPngBase64 = friend.quayPassAvatar
                )
            }
    }

    fun moveFocus(delta: Int) {
        _uiState.update { state ->
            if (state.apps.isEmpty()) state
            else state.copy(focusedIndex = (state.focusedIndex + delta).mod(state.apps.size))
        }
    }

    fun switchTo(index: Int) {
        val state = _uiState.value
        if (state.resumingTaskId != null) return
        val app = state.apps.getOrNull(index) ?: return
        if (state.showsSnapshots && app.snapshotPath != null) {
            _uiState.update { it.copy(focusedIndex = index, resumingTaskId = app.taskId) }
            return
        }
        _uiState.update { it.copy(focusedIndex = index) }
        bringForward(app)
    }

    /**
     * Brings the game the Resume zoom grew out of to the front, once the zoom has filled the screen.
     */
    fun onResumeZoomFinished(taskId: Int) {
        val app = _uiState.value.apps.firstOrNull { it.taskId == taskId }
        if (app == null) {
            _uiState.update { it.copy(resumingTaskId = null) }
            return
        }
        bringForward(app)
        viewModelScope.launch {
            delay(RESUME_SETTLE_MS)
            if (pollJob?.isActive == true) _uiState.update { it.copy(resumingTaskId = null) }
        }
    }

    private fun bringForward(app: RunningAppUi) {
        val gameId = app.gameId
        if (gameId != null) {
            gameLaunchDelegate.launchGame(
                scope = viewModelScope,
                gameId = gameId,
                allowVariantPrompt = false,
                start = GameStart(gameId, onFailure = { showGameLaunchFailed() })
            )
            return
        }
        viewModelScope.launch {
            val moved = backgroundGames.bringToFront(app.taskId)
            if (!moved) {
                _uiState.update { it.copy(resumingTaskId = null) }
                notificationManager.showError(
                    NotificationText.Res(R.string.running_notice_switch_failed, listOf(app.label))
                )
                refresh()
            }
        }
    }

    private fun showGameLaunchFailed() {
        _uiState.update { it.copy(resumingTaskId = null) }
        notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
    }

    fun requestClose(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        if (app.gameId == null) {
            _uiState.update { it.copy(focusedIndex = index) }
            close(listOf(app.taskId))
            return
        }
        _uiState.update {
            it.copy(
                focusedIndex = index,
                confirm = RunningConfirm.CloseGame(app.taskId, app.gameTitle ?: app.label)
            )
        }
    }

    fun requestCloseAll() {
        val taskIds = _uiState.value.apps.map { it.taskId }
        if (taskIds.isEmpty()) return
        _uiState.update { it.copy(confirm = RunningConfirm.CloseAll(taskIds)) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirm = null) }
    }

    fun confirmClose() {
        val confirm = _uiState.value.confirm ?: return
        _uiState.update { it.copy(confirm = null) }
        when (confirm) {
            is RunningConfirm.CloseGame -> close(listOf(confirm.taskId))
            is RunningConfirm.CloseAll -> close(confirm.taskIds)
        }
    }

    private fun close(taskIds: List<Int>) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(closing = state.closing + taskIds.associateWith { MakeRoomStage.SAVING }) }
            withContext(Dispatchers.IO) {
                val running = backgroundGames.refresh().associateBy { it.taskId }
                taskIds.mapNotNull { running[it] }.forEach { backgroundGames.close(it) }
                awaitClosed(taskIds.toSet())
            }
            _uiState.update { state -> state.copy(closing = state.closing + taskIds.associateWith { MakeRoomStage.CLOSED }) }
            delay(ComponentDefaults.LaunchOverlay.makeRoomClosedHoldMs.toLong())
            refresh(duringClose = true)
            _uiState.update { state -> state.copy(closing = state.closing - taskIds.toSet()) }
        }
    }

    private suspend fun awaitClosed(taskIds: Set<Int>) {
        val deadline = System.currentTimeMillis() + CLOSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (backgroundGames.refresh().none { it.taskId in taskIds }) return
            delay(CLOSE_POLL_MS)
        }
    }

    fun openMenu(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        _uiState.update { it.copy(focusedIndex = index, menu = RunningMenu(app.taskId)) }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissMenu() {
        _uiState.update { it.copy(menu = null) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun moveMenuFocus(delta: Int) {
        _uiState.update { state ->
            val menu = state.menu ?: return@update state
            state.copy(menu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(RunningMenuAction.entries.size)))
        }
    }

    fun selectMenuAction(actionIndex: Int) {
        val state = _uiState.value
        val menu = state.menu ?: return
        val appIndex = state.apps.indexOfFirst { it.taskId == menu.taskId }
        _uiState.update { it.copy(menu = null) }
        if (appIndex < 0) return
        when (RunningMenuAction.entries.getOrNull(actionIndex)) {
            RunningMenuAction.SWITCH_TO -> switchTo(appIndex)
            RunningMenuAction.CLOSE -> requestClose(appIndex)
            null -> Unit
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        private fun move(delta: Int, alongCards: Boolean): InputResult {
            val state = _uiState.value
            if (state.apps.isEmpty() || state.showsSnapshots != alongCards) return InputResult.UNHANDLED
            moveFocus(delta)
            return InputResult.HANDLED
        }

        override fun onUp(): InputResult = move(-1, alongCards = false)

        override fun onDown(): InputResult = move(1, alongCards = false)

        override fun onLeft(): InputResult = move(-1, alongCards = true)

        override fun onRight(): InputResult = move(1, alongCards = true)

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            if (state.focusedApp == null) return InputResult.UNHANDLED
            switchTo(state.focusedIndex)
            return InputResult.HANDLED
        }

        override fun onLongConfirm(): InputResult {
            val state = _uiState.value
            if (state.focusedApp == null) return InputResult.UNHANDLED
            openMenu(state.focusedIndex)
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            if (state.focusedApp == null) return InputResult.UNHANDLED
            requestClose(state.focusedIndex)
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (_uiState.value.apps.isEmpty()) return InputResult.UNHANDLED
            requestCloseAll()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }

    fun createMenuInputHandler(): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            moveMenuFocus(-1)
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            moveMenuFocus(1)
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            _uiState.value.menu?.let { selectMenuAction(it.focusIndex) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            dismissMenu()
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult = InputResult.HANDLED
        override fun onRight(): InputResult = InputResult.HANDLED
        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 3_000L
        const val RESUME_SETTLE_MS = 1_500L
        const val KB_PER_MB = 1024L
        const val CLOSE_TIMEOUT_MS = 20_000L
        const val CLOSE_POLL_MS = 250L
    }
}
