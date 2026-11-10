package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import com.nendo.argosy.data.emulator.background.TaskSnapshots
import com.nendo.argosy.data.emulator.startExternal
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.MakeRoomCandidate
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.domain.model.RunningGameQuestion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * The launch steps for an emulator kept alive between sessions: resuming the game it still holds,
 * and clearing it when a launch needs it for another game.
 */
class BackgroundGameLaunch @Inject constructor(
    private val backgroundGames: BackgroundGames,
    private val emulatorResolver: EmulatorResolver,
    private val playSessionTracker: PlaySessionTracker,
    private val launchProgressTracker: LaunchProgressTracker,
    private val gameRepository: GameRepository,
    private val taskSnapshots: TaskSnapshots,
    private val saveCacheManager: SaveCacheManager
) {
    enum class Resume { RESUMED, FRESH_LAUNCH, CANCELLED }

    enum class Clear { NOT_RUNNING, CLOSED, CANCELLED }

    suspend fun findLive(gameId: Long, variantFileId: Long?, emulatorPackage: String?): RunningApp? {
        if (emulatorPackage == null || !backgroundGames.keepsAlive(emulatorPackage)) return null
        return backgroundGames.liveGame(gameId, variantFileId, emulatorPackage)
    }

    /**
     * Brings [game] back in the task [live] holds, with no save check: the emulator still has the
     * save open. A built-in game opens its own session when its screen returns.
     */
    suspend fun resume(
        ticket: LaunchProgressTracker.Ticket,
        game: GameEntity,
        live: RunningApp,
        origin: LaunchOrigin
    ): Resume {
        if (ticket.isCancelled) return Resume.CANCELLED
        val builtIn = live.packageName == EmulatorRegistry.BUILTIN_PACKAGE
        if (!builtIn) DualScreenManagerHolder.instance?.setEmulatorDisplay(live.displayId)
        if (!backgroundGames.bringToFront(live.taskId)) {
            if (!builtIn) DualScreenManagerHolder.instance?.setEmulatorDisplay(null)
            return Resume.FRESH_LAUNCH
        }
        launchProgressTracker.finish(ticket, launched = true)
        if (builtIn) {
            Logger.debug(TAG, "resumed gameId=${game.id} in the built-in game's task ${live.taskId}")
            return Resume.RESUMED
        }
        playSessionTracker.startExternal(
            ExternalSessionStart(
                gameId = game.id,
                emulatorPackage = live.packageName,
                coreName = null,
                variantFileId = live.game?.variantFileId,
                origin = origin
            )
        )
        Logger.debug(TAG, "resumed gameId=${game.id} in task ${live.taskId} (${live.packageName})")
        return Resume.RESUMED
    }

    /**
     * Asks before closing external [emulatorPackage] when it is kept alive and still running a
     * game. NOT_RUNNING leaves the caller's own stop to run.
     */
    suspend fun clearForLaunch(ticket: LaunchProgressTracker.Ticket, emulatorPackage: String): Clear {
        if (!backgroundGames.keepsAlive(emulatorPackage)) return Clear.NOT_RUNNING
        val running = backgroundGames.liveApp(emulatorPackage) ?: return Clear.NOT_RUNNING
        val question = LaunchStep.RunningGame(
            kind = RunningGameQuestion.EMULATOR_BUSY,
            emulatorName = emulatorName(emulatorPackage),
            runningGameTitle = running.game?.let { gameRepository.getById(it.gameId)?.title },
            options = listOf(LaunchPromptOption.CLOSE_AND_LAUNCH)
        )
        if (ticket.askAboutRunningGame(question) != LaunchPromptOption.CLOSE_AND_LAUNCH) return Clear.CANCELLED
        backgroundGames.close(running)
        return Clear.CLOSED
    }

    /**
     * True once a built-in [platformSlug] game has room to start, after asking the player which
     * open game to close for as long as room is short. False when the player declines or a closed
     * slot never frees up.
     */
    suspend fun makeRoomForBuiltIn(ticket: LaunchProgressTracker.Ticket, platformSlug: String?): Boolean {
        while (!backgroundGames.hasRoomFor(platformSlug)) {
            val open = backgroundGames.builtInApps()
                .filter { (it.slotIndex ?: 0) > 0 && it.game != null }
                .sortedBy { it.slotIndex }
            if (open.isEmpty()) return true
            val question = makeRoomQuestion(ticket.gameTitle, open)
            val chosenTaskId = ticket.chooseGameToClose(question) ?: return false
            val chosen = open.firstOrNull { it.taskId == chosenTaskId } ?: return false
            ticket.step(question.copy(stage = MakeRoomStage.SAVING, chosenTaskId = chosenTaskId))
            backgroundGames.close(chosen)
            if (!awaitSlotClosed(chosen)) return false
            ticket.step(question.copy(stage = MakeRoomStage.CLOSED, chosenTaskId = chosenTaskId))
            delay(ComponentDefaults.LaunchOverlay.makeRoomClosedHoldMs.toLong())
        }
        return true
    }

    private suspend fun makeRoomQuestion(launchingTitle: String?, open: List<RunningApp>): LaunchStep.MakeRoom {
        val snapshots = taskSnapshots.fetch(open.map { it.taskId })
        val candidates = open.mapNotNull { app ->
            val game = app.game ?: return@mapNotNull null
            val snapshot = snapshots[app.taskId]
            MakeRoomCandidate(
                taskId = app.taskId,
                slotIndex = app.slotIndex ?: return@mapNotNull null,
                gameId = game.gameId,
                title = gameRepository.getById(game.gameId)?.title,
                snapshotPath = snapshot?.path,
                snapshotTakenAtMillis = snapshot?.takenAtMillis ?: 0L,
                lastSavedAtMillis = saveCacheManager.getMostRecentSave(game.gameId)?.cachedAt?.toEpochMilli()
            )
        }
        val oldestTaskId = open.minByOrNull { it.runStartedAtMillis ?: Long.MAX_VALUE }?.taskId
        return LaunchStep.MakeRoom(
            launchingTitle = launchingTitle,
            candidates = candidates,
            initialFocus = candidates.indexOfFirst { it.taskId == oldestTaskId }.coerceAtLeast(0),
            stage = MakeRoomStage.CHOOSE,
            chosenTaskId = null
        )
    }

    private suspend fun awaitSlotClosed(app: RunningApp): Boolean {
        val deadline = System.currentTimeMillis() + SLOT_CLOSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (backgroundGames.builtInApps().none { it.slotIndex == app.slotIndex }) return true
            delay(SLOT_CLOSE_POLL_MS)
        }
        Logger.warn(TAG, "slot ${app.slotIndex} did not close within ${SLOT_CLOSE_TIMEOUT_MS}ms")
        return false
    }

    suspend fun keepsAlive(emulatorPackage: String): Boolean = backgroundGames.keepsAlive(emulatorPackage)

    suspend fun remember(session: ActiveSession) = backgroundGames.remember(session)

    private fun emulatorName(packageName: String): String =
        emulatorResolver.resolveEmulatorId(packageName)?.let { EmulatorRegistry.getById(it)?.displayName } ?: packageName

    private companion object {
        const val TAG = "BackgroundGameLaunch"
        const val SLOT_CLOSE_TIMEOUT_MS = 20_000L
        const val SLOT_CLOSE_POLL_MS = 200L
    }
}
