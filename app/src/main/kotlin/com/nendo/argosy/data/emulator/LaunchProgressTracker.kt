package com.nendo.argosy.data.emulator

import android.os.SystemClock
import com.nendo.argosy.domain.model.LaunchFailure
import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.failure
import com.nendo.argosy.domain.model.phase
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LaunchProgressTracker"
private const val LAUNCHING_HOLD_MS = 8_000L
private const val PREVIOUS_LAUNCH_WAIT_MS = 15_000L

/**
 * The one launch in progress, from the press that started it until its game screen opens.
 *
 * A cancelled launch keeps running in the background until its current step finishes, but it
 * never opens the game; a launch started while it drains waits for it before doing anything.
 */
@Singleton
class LaunchProgressTracker internal constructor(
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {

    @Inject constructor() : this(SafeCoroutineScope(Dispatchers.Default, TAG))

    private val _progress = MutableStateFlow<LaunchProgress?>(null)
    val progress: StateFlow<LaunchProgress?> = _progress.asStateFlow()

    private val _launchLive = MutableStateFlow(false)

    /**
     * True from [begin] until the launch it opened is cleared: cancelled, failed, its game screen
     * taken over or never opened, or its launching hold run out.
     */
    val launchLive: StateFlow<Boolean> = _launchLive.asStateFlow()

    private val lock = Any()
    private var live: Ticket? = null
    private var draining: Ticket? = null
    private var launchingHold: Job? = null

    val current: Ticket?
        get() = synchronized(lock) { live?.takeUnless { it.isFinished } }

    inner class Ticket internal constructor(val gameId: Long?, val gameTitle: String?) {
        @Volatile
        var isCancelled = false
            private set

        var failure: LaunchFailure? = null
            internal set

        @Volatile
        internal var awaitsWindow = false

        private val finished = CompletableDeferred<Unit>()
        private var pendingAnswer: CompletableDeferred<LaunchPromptOption?>? = null

        val isFinished: Boolean get() = finished.isCompleted

        fun step(step: LaunchStep) = publish(this, step)

        /**
         * Shows [conflict] with [options] and returns the chosen option, or null when the launch
         * is cancelled instead.
         */
        suspend fun ask(conflict: SyncProgress, options: List<LaunchPromptOption>): LaunchPromptOption? =
            awaitAnswer(LaunchStep.Prompt(conflict, options))

        /**
         * Asks which of [options] launches the game and returns the chosen option, or null when the
         * launch is cancelled instead.
         */
        suspend fun chooseLauncher(options: List<LaunchPromptOption>): LaunchPromptOption? =
            awaitAnswer(LaunchStep.ChooseLauncher(options))

        /**
         * Asks [question] about a game an emulator keeps in the background and returns the chosen
         * option, or null when the launch is cancelled instead.
         */
        suspend fun askAboutRunningGame(question: LaunchStep.RunningGame): LaunchPromptOption? =
            awaitAnswer(question)

        /**
         * Shows [recap] until the player starts the game or cancels the launch. True when they
         * start it.
         */
        suspend fun offerRecap(recap: LaunchRecap): Boolean =
            awaitAnswer(LaunchStep.Recap(recap)) == LaunchPromptOption.START_GAME

        private suspend fun awaitAnswer(step: LaunchStep): LaunchPromptOption? {
            val answer = CompletableDeferred<LaunchPromptOption?>()
            synchronized(lock) {
                if (isCancelled) return null
                pendingAnswer = answer
            }
            publish(this, step)
            return answer.await().also { synchronized(lock) { pendingAnswer = null } }
        }

        internal fun answer(option: LaunchPromptOption) {
            pendingAnswer?.complete(option)
        }

        internal fun cancel() {
            isCancelled = true
            pendingAnswer?.complete(null)
        }

        internal fun markFinished() {
            finished.complete(Unit)
        }

        internal suspend fun awaitFinished() = finished.await()
    }

    /**
     * Opens a launch, waiting out a cancelled one that is still finishing. Null when another
     * launch is already in progress, or this one was cancelled while it waited.
     */
    suspend fun begin(gameId: Long?, gameTitle: String?): Ticket? {
        val ticket: Ticket
        val previous: Ticket?
        synchronized(lock) {
            if (live?.isFinished == false) return null
            launchingHold?.cancel()
            ticket = Ticket(gameId, gameTitle)
            live = ticket
            _launchLive.value = true
            previous = draining?.takeUnless { it.isFinished }
            draining = null
            _progress.value = null
        }
        if (previous != null) {
            ticket.step(LaunchStep.FinishingPrevious)
            if (withTimeoutOrNull(PREVIOUS_LAUNCH_WAIT_MS) { previous.awaitFinished() } == null) {
                Logger.warn(TAG, "A cancelled launch of ${previous.gameTitle} is still running after ${PREVIOUS_LAUNCH_WAIT_MS}ms, starting without it")
            }
        }
        if (ticket.isCancelled) {
            finish(ticket, launched = false)
            return null
        }
        return ticket
    }

    fun report(step: LaunchStep) {
        current?.step(step)
    }

    fun answer(option: LaunchPromptOption) {
        synchronized(lock) { live }?.answer(option)
    }

    /**
     * Cancels the launch in progress. False when there is none, or its game screen is already
     * opening.
     */
    fun cancel(): Boolean = synchronized(lock) {
        val ticket = live ?: return false
        if (ticket.isFinished) return false
        ticket.cancel()
        live = null
        _launchLive.value = false
        draining = ticket
        _progress.value = null
        true
    }

    /**
     * Ends [ticket]. A launch that opened its game keeps showing until the screen that started it
     * stops outside a [holdForWindow] hold, or briefly when it never does.
     */
    fun finish(ticket: Ticket, launched: Boolean) {
        synchronized(lock) {
            ticket.markFinished()
            if (draining === ticket) draining = null
            if (live !== ticket) return
            if (launched && !ticket.isCancelled) {
                _progress.value = LaunchProgress(
                    ticket.gameTitle, LaunchStep.Launching, ticket.gameId, clock(), ticket.failure
                )
                launchingHold?.cancel()
                launchingHold = scope.launch {
                    delay(LAUNCHING_HOLD_MS)
                    clearIfLive(ticket)
                }
            } else {
                live = null
                _launchLive.value = false
                _progress.value = null
            }
        }
    }

    /**
     * Keeps [ticket]'s Launching through a stop of the screen that started it until
     * [windowRequested] or [openFailed]. The Launching hold timeout still ends it.
     */
    fun holdForWindow(ticket: Ticket) {
        synchronized(lock) { ticket.awaitsWindow = true }
    }

    fun windowRequested(ticket: Ticket) {
        synchronized(lock) { ticket.awaitsWindow = false }
    }

    /**
     * Ends the finished live launch unless [holdForWindow] still holds it.
     */
    fun hostStopped() {
        synchronized(lock) { live?.takeIf { it.isFinished && !it.awaitsWindow } }?.let(::clearIfLive)
    }

    /**
     * Ends [ticket], a launch whose game screen was asked for but never opened. A launch begun
     * since is left alone.
     */
    fun openFailed(ticket: Ticket) = clearIfLive(ticket)

    private fun clearIfLive(ticket: Ticket) {
        synchronized(lock) {
            if (live !== ticket) return
            live = null
            _launchLive.value = false
            _progress.value = null
        }
    }

    private fun publish(ticket: Ticket, step: LaunchStep) {
        synchronized(lock) {
            if (live !== ticket || ticket.isCancelled) return
            ticket.failure = step.failure ?: ticket.failure
            _progress.value = LaunchProgress(
                ticket.gameTitle, step, ticket.gameId, startedAt(_progress.value, step), ticket.failure
            )
        }
    }

    private fun startedAt(shown: LaunchProgress?, step: LaunchStep): Long {
        val shownPhase = shown?.step?.phase
        val keepsClock = shownPhase == step.phase && shownPhase != LaunchPhase.QUESTION
        return if (shown != null && keepsClock) shown.stepStartedAtMs else clock()
    }
}
