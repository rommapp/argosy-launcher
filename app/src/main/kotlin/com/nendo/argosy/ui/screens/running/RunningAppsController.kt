package com.nendo.argosy.ui.screens.running

import android.graphics.Bitmap
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import com.nendo.argosy.data.emulator.background.RunningGames
import com.nendo.argosy.data.emulator.background.TaskSnapshot
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.GameStart
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.AttachedScreen
import com.nendo.argosy.util.ScreenCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider

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

data class RunningScreenUi(val displayId: Int, val number: Int)

sealed interface RunningMenuEntry {
    data object SwitchTo : RunningMenuEntry
    data class MoveTo(val target: RunningMoveTarget) : RunningMenuEntry
    data object Close : RunningMenuEntry
}

data class RunningMenu(val taskId: Int, val entries: List<RunningMenuEntry>, val focusIndex: Int = 0)

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
    val resumingTaskId: Int? = null,
    val screens: List<RunningScreenUi> = emptyList(),
    val frontTaskId: Int? = null,
    val frontFrame: Bitmap? = null
) {
    val focusedApp: RunningAppUi? get() = apps.getOrNull(focusedIndex)

    val menuApp: RunningAppUi? get() = menu?.let { open -> apps.firstOrNull { it.taskId == open.taskId } }
}

/**
 * The Resume list's state and actions, shared by Argosy's Resume screen and the overlay drawn over
 * a running game. A controller built with a dismiss callback runs over a game: it marks the game in
 * front, closes the overlay when that game is picked, and closes it once another game is in front.
 */
class RunningAppsController private constructor(
    private val deps: Factory,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val scope: CoroutineScope,
    private val launchScope: CoroutineScope,
    private val dismissOverlay: (() -> Unit)?
) {
    private val _uiState = MutableStateFlow(RunningAppsUiState())
    val uiState: StateFlow<RunningAppsUiState> = _uiState.asStateFlow()

    private val refreshLock = Mutex()
    private val labels = mutableMapOf<String, String>()
    private val platformNames = mutableMapOf<Long, String>()
    private var pollJob: Job? = null

    private val _openGame = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openGame: SharedFlow<Long> = _openGame.asSharedFlow()

    init {
        scope.launch {
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

    /**
     * Shows [frame] on the card of the game in front, the one an overlay was opened over, until a
     * newer snapshot of it arrives.
     */
    fun showFrontFrame(frame: Bitmap) {
        _uiState.update { it.copy(frontFrame = frame) }
    }

    fun onVisible() {
        _uiState.update { it.copy(resumingTaskId = null) }
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
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

    private class Refreshed(
        val rows: List<RunningAppUi>,
        val screens: List<RunningScreenUi>,
        val frontTaskId: Int?
    )

    private suspend fun refresh(duringClose: Boolean = false) {
        if (!duringClose && _uiState.value.closing.isNotEmpty()) return
        val showsSnapshots = deps.backgroundGames.isSupported
        val refreshed = refreshLock.withLock {
            withContext(Dispatchers.IO) {
                val screens = deps.screenCatalog.attachedScreens().takeIf { it.size > 1 }
                val apps = deps.runningGames.refresh()
                val read = deps.runningGames.state.value
                val played = read.playedThisRunMs
                val snapshots = if (showsSnapshots) read.snapshots else emptyMap()
                Refreshed(
                    rows = apps.map { it.toUi(screens, snapshots[it.taskId], played[it.taskId] ?: 0L) },
                    screens = screens.orEmpty().map { RunningScreenUi(it.displayId, it.number) },
                    frontTaskId = if (dismissOverlay != null) frontTaskOf(apps) else null
                )
            }
        }
        val rows = refreshed.rows
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
                menu = menu,
                screens = refreshed.screens,
                frontTaskId = refreshed.frontTaskId
            )
        }
    }

    private fun frontTaskOf(apps: List<RunningApp>): Int? {
        val session = deps.playSessionTracker.activeSession.value ?: return null
        return apps.firstOrNull { app ->
            isSessionFront(session, app.packageName, app.game?.gameId, app.game?.variantFileId)
        }?.taskId
    }

    private suspend fun RunningApp.toUi(
        screens: List<AttachedScreen>?,
        snapshot: TaskSnapshot?,
        playedThisRunMs: Long
    ): RunningAppUi {
        val entity = game?.gameId?.let { deps.gameRepository.getById(it) }
        val isStream = packageName == StreamSessionManager.STREAM_PACKAGE
        return RunningAppUi(
            taskId = taskId,
            packageName = packageName,
            iconPackageName = if (packageName == EmulatorRegistry.BUILTIN_PACKAGE || isStream) {
                com.nendo.argosy.BuildConfig.APPLICATION_ID
            } else {
                packageName
            },
            label = if (isStream) {
                deps.streamSessions.session.value?.target?.containerLabel
                    ?.let { deps.context.getString(R.string.stream_running_label, it) }
                    ?: packageName
            } else {
                labels.getOrPut(packageName) {
                    deps.appsRepository.getAppLabel(packageName)
                        ?: EmulatorRegistry.getByPackage(packageName)?.displayName
                        ?: packageName
                }
            },
            gameId = entity?.id,
            gameTitle = entity?.title,
            memoryMb = rssKb?.let { it / KB_PER_MB },
            screenNumber = screens?.firstOrNull { it.displayId == displayId }?.number,
            snapshotPath = snapshot?.path,
            snapshotTakenAtMillis = snapshot?.takenAtMillis ?: 0L,
            screenSnapshotPath = snapshot?.screen?.path,
            screenSnapshotTakenAtMillis = snapshot?.screen?.takenAtMillis ?: 0L,
            details = entity?.let { gameDetails(it, this, playedThisRunMs) },
            slotIndex = slotIndex,
            platformSlug = entity?.platformSlug
        )
    }

    private suspend fun gameDetails(game: GameEntity, app: RunningApp, playedThisRunMs: Long): RunningGameDetails {
        return RunningGameDetails(
            platformName = platformName(game.platformId),
            playedThisRunMs = playedThisRunMs,
            raEarned = game.earnedAchievementCount,
            raTotal = game.achievementCount,
            hardcore = app.hardcore,
            lastSavedAtMillis = deps.saveCacheManager.getMostRecentSave(game.id)?.cachedAt?.toEpochMilli(),
            friendsPlaying = friendsPlaying(game.igdbId)
        )
    }

    private suspend fun platformName(platformId: Long): String? =
        platformNames[platformId]
            ?: deps.platformRepository.getById(platformId)?.name?.also { platformNames[platformId] = it }

    private fun friendsPlaying(igdbId: Long?): List<RunningFriendUi> {
        if (igdbId == null) return emptyList()
        return deps.socialRepository.friends.value
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
        if (app.taskId == state.frontTaskId) {
            dismissOverlay?.invoke()
            return
        }
        if ((state.showsSnapshots && app.snapshotPath != null) || dismissOverlay != null) {
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
        val dismiss = dismissOverlay
        scope.launch {
            if (dismiss != null) {
                dismissOnceInFront(app, dismiss)
                return@launch
            }
            delay(RESUME_SETTLE_MS)
            if (pollJob?.isActive == true) _uiState.update { it.copy(resumingTaskId = null) }
        }
    }

    private suspend fun dismissOnceInFront(app: RunningAppUi, dismiss: () -> Unit) {
        val gameId = app.gameId
        if (gameId == null) {
            delay(RESUME_SETTLE_MS)
        } else {
            withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                combine(deps.playSessionTracker.activeSession, _uiState) { session, state ->
                    session?.gameId == gameId || state.resumingTaskId != app.taskId
                }.first { it }
            }
        }
        if (_uiState.value.resumingTaskId != app.taskId) return
        if (gameId != null && deps.playSessionTracker.activeSession.value?.gameId != gameId) {
            _uiState.update { it.copy(resumingTaskId = null) }
            return
        }
        delay(SWITCH_SETTLE_MS)
        dismiss()
    }

    private fun bringForward(app: RunningAppUi) {
        val gameId = app.gameId
        if (gameId != null && app.packageName != StreamSessionManager.STREAM_PACKAGE) {
            gameLaunchDelegate.launchGame(
                scope = launchScope,
                gameId = gameId,
                allowVariantPrompt = false,
                start = GameStart(gameId, onFailure = { showGameLaunchFailed() })
            )
            return
        }
        launchScope.launch {
            val moved = deps.backgroundGames.bringToFront(app.taskId)
            if (!moved) {
                _uiState.update { it.copy(resumingTaskId = null) }
                deps.notificationManager.showError(
                    NotificationText.Res(R.string.running_notice_switch_failed, listOf(app.label))
                )
                refresh()
            }
        }
    }

    private fun showGameLaunchFailed() {
        _uiState.update { it.copy(resumingTaskId = null) }
        deps.notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
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
        scope.launch {
            _uiState.update { state -> state.copy(closing = state.closing + taskIds.associateWith { MakeRoomStage.SAVING }) }
            launchScope.launch(Dispatchers.IO) { deps.runningGames.close(taskIds) }.join()
            _uiState.update { state -> state.copy(closing = state.closing + taskIds.associateWith { MakeRoomStage.CLOSED }) }
            delay(ComponentDefaults.LaunchOverlay.makeRoomClosedHoldMs.toLong())
            refresh(duringClose = true)
            _uiState.update { state -> state.copy(closing = state.closing - taskIds.toSet()) }
        }
    }

    fun openMenu(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        _uiState.update { state ->
            state.copy(focusedIndex = index, menu = RunningMenu(app.taskId, menuEntries(app, state)))
        }
        deps.soundManager.play(SoundType.OPEN_MODAL)
    }

    private fun menuEntries(app: RunningAppUi, state: RunningAppsUiState): List<RunningMenuEntry> =
        listOf<RunningMenuEntry>(RunningMenuEntry.SwitchTo) + moveEntries(app, state) + RunningMenuEntry.Close

    private fun moveEntries(app: RunningAppUi, state: RunningAppsUiState): List<RunningMenuEntry.MoveTo> =
        runningMoveTargets(
            isFront = app.taskId == state.frontTaskId,
            slotIndex = app.slotIndex,
            packageName = app.packageName,
            currentScreenNumber = app.screenNumber,
            screens = state.screens
        ).map { RunningMenuEntry.MoveTo(it) }

    fun dismissMenu() {
        _uiState.update { it.copy(menu = null) }
        deps.soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun moveMenuFocus(delta: Int) {
        _uiState.update { state ->
            val menu = state.menu ?: return@update state
            if (menu.entries.isEmpty()) return@update state
            state.copy(menu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(menu.entries.size)))
        }
    }

    fun selectMenuAction(actionIndex: Int) {
        val state = _uiState.value
        val menu = state.menu ?: return
        val appIndex = state.apps.indexOfFirst { it.taskId == menu.taskId }
        _uiState.update { it.copy(menu = null) }
        if (appIndex < 0) return
        when (val entry = menu.entries.getOrNull(actionIndex)) {
            RunningMenuEntry.SwitchTo -> switchTo(appIndex)
            RunningMenuEntry.Close -> requestClose(appIndex)
            is RunningMenuEntry.MoveTo -> moveTo(state.apps[appIndex], entry.target)
            null -> Unit
        }
    }

    private fun moveTo(app: RunningAppUi, target: RunningMoveTarget) {
        if (target.swapsRoles) {
            DualScreenManagerHolder.instance?.swapRoles() ?: return
            dismissOverlay?.invoke()
            return
        }
        launchScope.launch {
            if (!moveRunningTask(app.taskId, target)) {
                deps.notificationManager.showError(
                    NotificationText.Res(R.string.running_notice_move_failed, listOf(app.gameTitle ?: app.label))
                )
            }
            refresh()
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

    class Factory @Inject constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext internal val context: android.content.Context,
        internal val streamSessions: StreamSessionManager,
        internal val backgroundGames: BackgroundGames,
        internal val runningGames: RunningGames,
        internal val appsRepository: AppsRepository,
        internal val gameRepository: GameRepository,
        internal val platformRepository: PlatformRepository,
        internal val saveCacheManager: SaveCacheManager,
        internal val socialRepository: SocialRepository,
        internal val screenCatalog: ScreenCatalog,
        internal val playSessionTracker: PlaySessionTracker,
        internal val notificationManager: NotificationManager,
        internal val soundManager: SoundFeedbackManager,
        private val gameLaunchDelegates: Provider<GameLaunchDelegate>
    ) {
        /**
         * A controller whose list work runs in [scope] and whose game switches, closes and moves
         * run in [launchScope], which has to outlive the host when the host can close first.
         * [dismissOverlay] is set only for the overlay drawn over a running game.
         */
        fun create(
            scope: CoroutineScope,
            launchScope: CoroutineScope = scope,
            dismissOverlay: (() -> Unit)? = null
        ): RunningAppsController = RunningAppsController(
            deps = this,
            gameLaunchDelegate = gameLaunchDelegates.get(),
            scope = scope,
            launchScope = launchScope,
            dismissOverlay = dismissOverlay
        )
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 3_000L
        const val RESUME_SETTLE_MS = 1_500L
        const val SWITCH_TIMEOUT_MS = 30_000L
        const val SWITCH_SETTLE_MS = 250L
        const val KB_PER_MB = 1024L
    }
}
