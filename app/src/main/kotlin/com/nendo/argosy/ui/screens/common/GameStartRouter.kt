package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameStartRouter"

/**
 * Process-lifetime entry point that resolves a game's placement and starts it. A start that
 * fails cancels the play session its launch opened.
 */
@Singleton
class GameStartRouter internal constructor(
    context: Context,
    private val launchTargetResolver: EmulatorLaunchTargetResolver,
    private val playSessionTracker: PlaySessionTracker,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        launchTargetResolver: EmulatorLaunchTargetResolver,
        playSessionTracker: PlaySessionTracker
    ) : this(
        context,
        launchTargetResolver,
        playSessionTracker,
        SafeCoroutineScope(Dispatchers.Main.immediate, "GameStart")
    )

    private val fallbackStarter = GameActivityStarter(context) { null }

    fun start(
        gameId: Long,
        intent: Intent,
        overrideDisplayId: Int? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            val result = place(gameId, intent, overrideDisplayId)
                .fold(onSuccess = ::startPlaced, onFailure = { Result.failure(it) })
            result.exceptionOrNull()?.let { error ->
                cancelSessionOf(gameId)
                onFailure?.invoke(error)
            }
        }
    }

    /**
     * Resolves the display [gameId] opens on, records it as the game's display, and returns the
     * request that opens it there.
     */
    suspend fun place(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Result<GameLaunchRequest> =
        try {
            val options = launchTargetResolver.launchOptionsFor(
                gameId,
                overrideDisplayId,
                intent.component?.packageName ?: intent.`package`,
                builtInDrawsBothScreens = com.nendo.argosy.libretro.DualScreenOutput.forCore(
                    intent.getStringExtra(com.nendo.argosy.libretro.LibretroActivity.EXTRA_CORE_NAME)
                ) != null
            )
            Result.success(GameLaunchRequest(intent, options))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Placement for gameId=$gameId could not be resolved", e)
            Result.failure(e)
        }

    fun cancelSessionOf(gameId: Long) {
        if (playSessionTracker.activeSession.value?.gameId != gameId) return
        Logger.info(TAG, "Start failed for gameId=$gameId, cancelling its session")
        playSessionTracker.cancelSession()
    }

    /**
     * Starts [request] with no system window transition, so the game's window appears in place
     * over the launch veil. The flag is added to the request's own intent, keeping its other flags.
     */
    fun startPlaced(request: GameLaunchRequest): Result<Unit> {
        request.intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val manager = DualScreenManagerHolder.instance ?: return fallbackStarter.start(request)
        return manager.startGame(request)
    }
}
