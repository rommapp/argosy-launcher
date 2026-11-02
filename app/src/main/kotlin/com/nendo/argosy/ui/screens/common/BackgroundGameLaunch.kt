package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import com.nendo.argosy.data.emulator.startExternal
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.RunningGameQuestion
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The launch steps for an emulator kept alive between sessions: resuming the game it still holds,
 * and clearing it when a launch needs it for another game.
 */
class BackgroundGameLaunch @Inject constructor(
    private val backgroundGames: BackgroundGames,
    private val emulatorResolver: EmulatorResolver,
    private val saveSyncRepository: SaveSyncRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val playSessionTracker: PlaySessionTracker,
    private val launchProgressTracker: LaunchProgressTracker,
    private val gameRepository: GameRepository
) {
    enum class Resume { RESUMED, FRESH_LAUNCH, CANCELLED }

    enum class Clear { NOT_RUNNING, CLOSED, CANCELLED }

    suspend fun findLive(gameId: Long, variantFileId: Long?, emulatorPackage: String?): RunningApp? {
        if (emulatorPackage == null || !backgroundGames.keepsAlive(emulatorPackage)) return null
        return backgroundGames.liveGame(gameId, variantFileId, emulatorPackage)
    }

    /**
     * Resumes [game] in the task [live] holds, after checking the server for a newer save without
     * touching the one the emulator has open. A newer save asks whether to load it, which closes
     * the emulator and hands back to a fresh launch.
     */
    suspend fun resume(
        ticket: LaunchProgressTracker.Ticket,
        game: GameEntity,
        live: RunningApp,
        channelName: String?,
        origin: LaunchOrigin
    ): Resume {
        val decision = liveSaveDecision(ticket, game, live, channelName)
        if (decision is PreLaunchSyncResult.ServerIsNewer || decision is PreLaunchSyncResult.LocalModified) {
            val question = LaunchStep.RunningGame(
                kind = RunningGameQuestion.NEWER_SAVE,
                emulatorName = emulatorName(live.packageName),
                runningGameTitle = game.title,
                options = listOf(LaunchPromptOption.LOAD_NEWER_SAVE, LaunchPromptOption.KEEP_PLAYING)
            )
            when (ticket.askAboutRunningGame(question)) {
                LaunchPromptOption.LOAD_NEWER_SAVE -> {
                    backgroundGames.close(live.packageName)
                    return Resume.FRESH_LAUNCH
                }
                LaunchPromptOption.KEEP_PLAYING -> Unit
                else -> return Resume.CANCELLED
            }
        }
        if (ticket.isCancelled) return Resume.CANCELLED
        DualScreenManagerHolder.instance?.setEmulatorDisplay(live.displayId)
        if (!backgroundGames.bringToFront(live.taskId)) {
            DualScreenManagerHolder.instance?.setEmulatorDisplay(null)
            return Resume.FRESH_LAUNCH
        }
        launchProgressTracker.finish(ticket, launched = true)
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
     * Asks before closing [emulatorPackage] when it is kept alive and still running a game.
     * NOT_RUNNING leaves the caller's own stop to run.
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
        backgroundGames.close(emulatorPackage)
        return Clear.CLOSED
    }

    suspend fun keepsAlive(emulatorPackage: String): Boolean = backgroundGames.keepsAlive(emulatorPackage)

    suspend fun remember(session: ActiveSession) = backgroundGames.remember(session)

    private suspend fun liveSaveDecision(
        ticket: LaunchProgressTracker.Ticket,
        game: GameEntity,
        live: RunningApp,
        channelName: String?
    ): PreLaunchSyncResult? {
        val rommId = game.rommId ?: return null
        if (live.game?.variantFileId != null) return null
        val emulatorId = emulatorResolver.resolveEmulatorId(live.packageName) ?: return null
        val prefs = preferencesRepository.preferences.first()
        if (!SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)) return null
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(channelName)))
        return saveSyncRepository.preLaunchSyncForGame(
            gameId = game.id,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = channelName,
            secureSaves = prefs.secureSaves,
            liveProcess = true
        )
    }

    private fun emulatorName(packageName: String): String =
        emulatorResolver.resolveEmulatorId(packageName)?.let { EmulatorRegistry.getById(it)?.displayName } ?: packageName

    private companion object {
        const val TAG = "BackgroundGameLaunch"
    }
}
