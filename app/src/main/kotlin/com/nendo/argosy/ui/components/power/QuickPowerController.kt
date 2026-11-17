package com.nendo.argosy.ui.components.power

import android.os.Build
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.background.RunningGamesSnapshot
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.hardware.PowerActions
import com.nendo.argosy.hardware.PowerRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A game left open, as the Power page lists it. [emulatorLabel] is null for a built-in game.
 */
data class QuickOpenGame(
    val taskId: Int,
    val title: String,
    val emulatorLabel: String?,
    val isBuiltIn: Boolean
)

data class QuickPowerState(
    val route: PowerRoute = PowerRoute.NONE,
    val sleepAvailable: Boolean = false,
    val openGames: List<QuickOpenGame> = emptyList()
)

enum class PowerCommand { RESTART, POWER_OFF }

/**
 * The restart or power off confirm. [command] is the last one asked and outlives [visible].
 * [working] holds while saves are pushed and the command runs.
 */
data class QuickPowerPrompt(
    val command: PowerCommand = PowerCommand.RESTART,
    val visible: Boolean = false,
    val working: Boolean = false
)

/**
 * State and actions of the quick panel's Power page. Every action that leaves the panel takes
 * the host's close callback and runs it before the action starts.
 */
class QuickPowerController(
    private val actions: PowerActions,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(QuickPowerState())
    val state: StateFlow<QuickPowerState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow(QuickPowerPrompt())
    val prompt: StateFlow<QuickPowerPrompt> = _prompt.asStateFlow()

    private val labels = mutableMapOf<String, String?>()

    fun refreshRoute() {
        scope.launch {
            val route = actions.currentRoute()
            _state.update {
                it.copy(route = route, sleepAvailable = PowerRoute.sleepAvailable(route, Build.VERSION.SDK_INT))
            }
        }
    }

    /**
     * Keeps [QuickPowerState.openGames] current until the calling coroutine is cancelled.
     */
    suspend fun watchOpenGames(): Unit = coroutineScope {
        launch { actions.watchRunning() }
        actions.running.collectLatest { snapshot ->
            val games = describe(snapshot)
            _state.update { it.copy(openGames = games) }
        }
    }

    private suspend fun describe(snapshot: RunningGamesSnapshot): List<QuickOpenGame> =
        snapshot.apps
            .filter { it.game != null && it.packageName != StreamSessionManager.STREAM_PACKAGE }
            .map { app ->
                val isBuiltIn = app.packageName == EmulatorRegistry.BUILTIN_PACKAGE
                val emulator = if (isBuiltIn) null else labelOf(app.packageName)
                QuickOpenGame(
                    taskId = app.taskId,
                    title = app.game?.let { actions.gameTitle(it.gameId) } ?: emulator ?: app.packageName,
                    emulatorLabel = emulator,
                    isBuiltIn = isBuiltIn
                )
            }
            .distinctBy { it.taskId }

    private suspend fun labelOf(packageName: String): String? {
        if (packageName in labels) return labels[packageName]
        val label = actions.appLabel(packageName)
        labels[packageName] = label
        return label
    }

    fun sleep(close: () -> Unit) {
        val route = _state.value.route
        close()
        actions.sleep(route)
    }

    fun openSystemPowerMenu(close: () -> Unit) {
        close()
        actions.openSystemPowerMenu()
    }

    fun openAccessibilitySettings(close: () -> Unit) {
        close()
        actions.openAccessibilitySettings()
    }

    fun request(command: PowerCommand) {
        _prompt.update { current -> if (current.working) current else QuickPowerPrompt(command = command, visible = true) }
    }

    fun dismiss() {
        _prompt.update { current -> if (current.working) current else current.copy(visible = false) }
    }

    fun confirm() {
        var chosen: PowerCommand? = null
        _prompt.update { current ->
            chosen = current.command.takeIf { current.visible && !current.working }
            if (chosen == null) current else current.copy(working = true)
        }
        val command = chosen ?: return
        scope.launch {
            val ran = when (command) {
                PowerCommand.RESTART -> actions.restart()
                PowerCommand.POWER_OFF -> actions.powerOff()
            }.await()
            if (!ran) _prompt.update { it.copy(visible = false, working = false) }
        }
    }
}
