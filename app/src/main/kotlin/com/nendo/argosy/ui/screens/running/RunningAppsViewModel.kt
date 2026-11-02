package com.nendo.argosy.ui.screens.running

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.GameRepository
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
    val label: String,
    val gameId: Long?,
    val gameTitle: String?,
    val memoryMb: Long?,
    val screenNumber: Int?
)

enum class RunningMenuAction(@StringRes val labelRes: Int) {
    SWITCH_TO(R.string.running_menu_switch_to),
    CLOSE(R.string.running_menu_close)
}

data class RunningMenu(val packageName: String, val focusIndex: Int = 0)

sealed interface RunningConfirm {
    data class CloseGame(val packageName: String, val title: String) : RunningConfirm
    data class CloseAll(val packageNames: List<String>) : RunningConfirm
}

data class RunningAppsUiState(
    val apps: List<RunningAppUi> = emptyList(),
    val isLoading: Boolean = true,
    val focusedIndex: Int = 0,
    val menu: RunningMenu? = null,
    val confirm: RunningConfirm? = null
) {
    val focusedApp: RunningAppUi? get() = apps.getOrNull(focusedIndex)

    val menuApp: RunningAppUi? get() = menu?.let { open -> apps.firstOrNull { it.packageName == open.packageName } }
}

@HiltViewModel
class RunningAppsViewModel @Inject constructor(
    private val backgroundGames: BackgroundGames,
    private val appsRepository: AppsRepository,
    private val gameRepository: GameRepository,
    private val screenCatalog: ScreenCatalog,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningAppsUiState())
    val uiState: StateFlow<RunningAppsUiState> = _uiState.asStateFlow()

    private val refreshLock = Mutex()
    private val labels = mutableMapOf<String, String>()
    private val gameTitles = mutableMapOf<Long, String>()
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

    private suspend fun refresh() {
        val rows = refreshLock.withLock {
            withContext(Dispatchers.IO) {
                val screens = screenCatalog.attachedScreens().takeIf { it.size > 1 }
                backgroundGames.refresh().map { it.toUi(screens) }
            }
        }
        _uiState.update { state ->
            val focusedPackage = state.focusedApp?.packageName
            val kept = rows.indexOfFirst { it.packageName == focusedPackage }
            val focusedIndex = if (kept >= 0) kept else state.focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            val menu = state.menu?.takeIf { open -> rows.any { it.packageName == open.packageName } }
            state.copy(apps = rows, isLoading = false, focusedIndex = focusedIndex, menu = menu)
        }
    }

    private suspend fun RunningApp.toUi(screens: List<AttachedScreen>?): RunningAppUi {
        val gameId = game?.gameId
        return RunningAppUi(
            taskId = taskId,
            packageName = packageName,
            label = labels.getOrPut(packageName) { appsRepository.getAppLabel(packageName) ?: packageName },
            gameId = gameId,
            gameTitle = gameId?.let { gameTitle(it) },
            memoryMb = rssKb?.let { it / KB_PER_MB },
            screenNumber = screens?.firstOrNull { it.displayId == displayId }?.number
        )
    }

    private suspend fun gameTitle(gameId: Long): String? =
        gameTitles[gameId] ?: gameRepository.getById(gameId)?.title?.also { gameTitles[gameId] = it }

    fun moveFocus(delta: Int) {
        _uiState.update { state ->
            if (state.apps.isEmpty()) state
            else state.copy(focusedIndex = (state.focusedIndex + delta).mod(state.apps.size))
        }
    }

    fun switchTo(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        _uiState.update { it.copy(focusedIndex = index) }
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
            val moved = withContext(Dispatchers.IO) { backgroundGames.bringToFront(app.taskId) }
            if (!moved) {
                notificationManager.showError(
                    NotificationText.Res(R.string.running_notice_switch_failed, listOf(app.label))
                )
                refresh()
            }
        }
    }

    private fun showGameLaunchFailed() {
        notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
    }

    fun requestClose(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        if (app.gameId == null) {
            _uiState.update { it.copy(focusedIndex = index) }
            close(listOf(app.packageName))
            return
        }
        _uiState.update {
            it.copy(
                focusedIndex = index,
                confirm = RunningConfirm.CloseGame(app.packageName, app.gameTitle ?: app.label)
            )
        }
    }

    fun requestCloseAll() {
        val packages = _uiState.value.apps.map { it.packageName }
        if (packages.isEmpty()) return
        _uiState.update { it.copy(confirm = RunningConfirm.CloseAll(packages)) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirm = null) }
    }

    fun confirmClose() {
        val confirm = _uiState.value.confirm ?: return
        _uiState.update { it.copy(confirm = null) }
        when (confirm) {
            is RunningConfirm.CloseGame -> close(listOf(confirm.packageName))
            is RunningConfirm.CloseAll -> close(confirm.packageNames)
        }
    }

    private fun close(packageNames: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                packageNames.forEach { backgroundGames.close(it) }
            }
            refresh()
        }
    }

    fun openMenu(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        _uiState.update { it.copy(focusedIndex = index, menu = RunningMenu(app.packageName)) }
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
        val appIndex = state.apps.indexOfFirst { it.packageName == menu.packageName }
        _uiState.update { it.copy(menu = null) }
        if (appIndex < 0) return
        when (RunningMenuAction.entries.getOrNull(actionIndex)) {
            RunningMenuAction.SWITCH_TO -> switchTo(appIndex)
            RunningMenuAction.CLOSE -> requestClose(appIndex)
            null -> Unit
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            if (_uiState.value.apps.isEmpty()) return InputResult.UNHANDLED
            moveFocus(-1)
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            if (_uiState.value.apps.isEmpty()) return InputResult.UNHANDLED
            moveFocus(1)
            return InputResult.HANDLED
        }

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
        const val KB_PER_MB = 1024L
    }
}
