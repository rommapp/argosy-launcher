package com.nendo.argosy.libretro.slot

import com.nendo.argosy.core.notification.ResolvedNotice
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.ui.screens.running.ResumeOverlay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

data class SessionStartRequest(
    val gameId: Long,
    val coreName: String?,
    val isHardcore: Boolean,
    val isNewGame: Boolean,
    val isNetplayGuest: Boolean,
    val variantFileId: Long?,
    val origin: LaunchOrigin
)

/**
 * The emulator's line to the play session that Argosy's main process owns. A game in slot 0 holds
 * the tracker directly; a game in a slot process reaches the same tracker over [SlotHostService].
 */
interface GameSessionLink {
    val sessionStart: StateFlow<Instant?>
    val saveWrites: Flow<Unit>

    fun startSession(request: SessionStartRequest)
    fun hasActiveSession(): Boolean
    fun onResumed()
    fun onPaused()
    fun updateHardcore(gameId: Long, isHardcore: Boolean)
    fun reportSaveWritten()
    suspend fun cacheForQuit()

    /**
     * Ends the session when it is still [gameId]'s built-in session; another game's session is
     * left alone.
     */
    fun endSessionIfOwned(gameId: Long, skipSaveSync: Boolean)

    /**
     * Opens Argosy's Resume overlay over [displayId] in the main process. [onClosed] runs once:
     * with false at once when the overlay cannot open, with true when it closes.
     */
    fun showResumeOverlay(displayId: Int, onClosed: (shown: Boolean) -> Unit)

    fun release()
}

class LocalGameSessionLink(
    private val tracker: PlaySessionTracker,
    scope: CoroutineScope,
    private val onGuideKey: (pressed: Boolean) -> Unit,
    private val onNotice: (ResolvedNotice) -> Unit,
    private val resumeOverlay: () -> ResumeOverlay
) : GameSessionLink {

    override val sessionStart: StateFlow<Instant?> =
        tracker.activeSession.map { it?.startTime }.stateIn(scope, SharingStarted.Eagerly, tracker.activeSession.value?.startTime)

    override val saveWrites: Flow<Unit> = tracker.saveWrites

    override fun startSession(request: SessionStartRequest) {
        tracker.startSession(
            gameId = request.gameId,
            emulatorPackage = EmulatorRegistry.BUILTIN_PACKAGE,
            coreName = request.coreName,
            isHardcore = request.isHardcore,
            isNewGame = request.isNewGame,
            isNetplayGuest = request.isNetplayGuest,
            variantFileId = request.variantFileId,
            origin = request.origin
        )
    }

    override fun hasActiveSession(): Boolean = tracker.activeSession.value != null

    override fun onResumed() {
        ResumedGameRouter.claim(this, onGuideKey, onNotice)
        tracker.onBuiltInGameResumed()
    }

    override fun onPaused() {
        ResumedGameRouter.release(this)
        tracker.onBuiltInGamePaused()
    }

    override fun updateHardcore(gameId: Long, isHardcore: Boolean) = tracker.updateSessionHardcore(gameId, isHardcore)

    override fun reportSaveWritten() = tracker.reportSaveWritten()

    override suspend fun cacheForQuit() = cacheSessionForQuit(tracker)

    override fun endSessionIfOwned(gameId: Long, skipSaveSync: Boolean) =
        endBuiltInSessionIfOwned(tracker, gameId, skipSaveSync)

    override fun showResumeOverlay(displayId: Int, onClosed: (shown: Boolean) -> Unit) {
        if (!resumeOverlay().show(displayId) { onClosed(true) }) onClosed(false)
    }

    override fun release() = ResumedGameRouter.release(this)
}

internal suspend fun cacheSessionForQuit(tracker: PlaySessionTracker) {
    runCatching { tracker.cacheCurrentSessionForQuit() }
        .onFailure { com.nendo.argosy.util.Logger.warn(LINK_TAG, "Pre-quit save cache failed", it) }
    runCatching { tracker.cacheCurrentSessionStatesForQuit() }
        .onFailure { com.nendo.argosy.util.Logger.warn(LINK_TAG, "Pre-quit state cache failed", it) }
}

internal fun endBuiltInSessionIfOwned(tracker: PlaySessionTracker, gameId: Long, skipSaveSync: Boolean) {
    val owned = tracker.activeSession.value?.let {
        it.gameId == gameId && it.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE
    } ?: false
    if (!owned || gameId == -1L) return
    com.nendo.argosy.DualScreenManagerHolder.instance?.onSessionChanged(-1L)
    tracker.endSessionInBackground(skipSaveSync = skipSaveSync)
}

private const val LINK_TAG = "GameSessionLink"
