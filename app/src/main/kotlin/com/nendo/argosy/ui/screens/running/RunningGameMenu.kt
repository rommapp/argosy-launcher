package com.nendo.argosy.ui.screens.running

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.RunningGames
import com.nendo.argosy.ui.common.CloseRunningPrompt
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.common.toRunningGamesUi
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.util.ScreenCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RunningMoveTarget(val displayId: Int, val screenNumber: Int, val swapsRoles: Boolean)

/**
 * The displays the app in a task can be carried to now: the display a role swap carries the
 * built-in game in Argosy's own task to while it is the session in front, or every other screen
 * for an external app in the background on a device that moves tasks. Empty for a game slot, a
 * built-in game in the background, and with fewer than two [screens].
 */
fun runningMoveTargets(
    isFront: Boolean,
    slotIndex: Int?,
    packageName: String,
    currentScreenNumber: Int?,
    screens: List<RunningScreenUi>
): List<RunningMoveTarget> {
    val dsm = DualScreenManagerHolder.instance ?: return emptyList()
    if (screens.size < 2) return emptyList()
    if (isFront) {
        if (slotIndex != 0) return emptyList()
        val target = dsm.liveMoveTargetDisplayId() ?: return emptyList()
        return screens.filter { it.displayId == target }
            .map { RunningMoveTarget(it.displayId, it.number, swapsRoles = true) }
    }
    if (packageName == EmulatorRegistry.BUILTIN_PACKAGE || !dsm.taskMoveAvailable.value) return emptyList()
    return screens.filter { it.number != currentScreenNumber }
        .map { RunningMoveTarget(it.displayId, it.number, swapsRoles = false) }
}

/**
 * Carries the app in [taskId] to [target]. False when the device refused the move.
 */
suspend fun moveRunningTask(taskId: Int, target: RunningMoveTarget): Boolean {
    val dsm = DualScreenManagerHolder.instance ?: return false
    if (target.swapsRoles) {
        withContext(Dispatchers.Main) { dsm.swapRoles() }
        return true
    }
    return dsm.moveTaskToDisplay(taskId, target.displayId)
}

/**
 * Whether the app [packageName] runs the game of [session]: any app of an external emulator, and
 * for the built-in emulator only the same game and variant.
 */
fun isSessionFront(session: ActiveSession?, packageName: String, gameId: Long?, variantFileId: Long?): Boolean {
    if (session == null || session.isStream || packageName != session.emulatorPackage) return false
    if (packageName != EmulatorRegistry.BUILTIN_PACKAGE) return true
    return gameId == session.gameId && variantFileId == session.variantFileId
}

/**
 * The running-game rows of one screen's game menu: the Close prompt it raises, and the displays the
 * game can be moved to. Each screen holds its own instance.
 */
class RunningGameMenu @Inject constructor(
    private val runningGames: RunningGames,
    private val screenCatalog: ScreenCatalog,
    private val playSessionTracker: PlaySessionTracker,
    private val notificationManager: NotificationManager
) {
    private val pending = MutableStateFlow<CloseRunningPrompt?>(null)

    /**
     * The Close prompt to show, dropped once its game has stopped or is closing.
     */
    val closePrompt: Flow<CloseRunningPrompt?> = combine(pending, runningGames.state) { prompt, snapshot ->
        prompt?.takeIf { open ->
            open.taskId !in snapshot.closingTaskIds && snapshot.apps.any { it.taskId == open.taskId }
        }
    }

    fun markFor(gameId: Long): RunningGameUi? = runningGames.state.value.toRunningGamesUi().markFor(gameId)

    fun requestClose(running: RunningGameUi?, title: String?) {
        if (running == null || title == null || running.isClosing) return
        pending.value = CloseRunningPrompt(running.taskId, title)
    }

    fun dismissClose() {
        pending.value = null
    }

    fun confirmClose() {
        val prompt = pending.value ?: return
        pending.value = null
        runningGames.closeDetached(listOf(prompt.taskId))
    }

    suspend fun moveTargets(running: RunningGameUi?): List<RunningMoveTarget> {
        if (running == null || running.isClosing) return emptyList()
        return withContext(Dispatchers.IO) {
            val screens = screenCatalog.attachedScreens().map { RunningScreenUi(it.displayId, it.number) }
            val front = isSessionFront(
                playSessionTracker.activeSession.value,
                running.emulatorPackage,
                running.gameId,
                running.variantFileId
            )
            runningMoveTargets(
                isFront = front,
                slotIndex = running.slotIndex,
                packageName = running.emulatorPackage,
                currentScreenNumber = screens.firstOrNull { it.displayId == running.displayId }?.number,
                screens = screens
            )
        }
    }

    fun moveTo(scope: CoroutineScope, running: RunningGameUi, target: RunningMoveTarget, title: String) {
        scope.launch {
            if (!moveRunningTask(running.taskId, target)) {
                notificationManager.showError(NotificationText.Res(R.string.running_notice_move_failed, listOf(title)))
            }
            runningGames.refresh()
        }
    }
}

@Composable
fun RunningGameCloseHost(menu: RunningGameMenu) {
    val prompt by menu.closePrompt.collectAsState(initial = null)
    ArgosyConfirmModalHost(
        visible = prompt != null,
        title = stringResource(R.string.running_confirm_close_title, prompt?.title.orEmpty()),
        message = stringResource(R.string.running_confirm_close_message),
        confirmLabel = stringResource(R.string.running_confirm_close_confirm),
        destructive = true,
        onConfirm = menu::confirmClose,
        onDismiss = menu::dismissClose
    )
}
