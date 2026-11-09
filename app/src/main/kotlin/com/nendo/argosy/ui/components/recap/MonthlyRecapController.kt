package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.usecase.recap.GetMonthlyRecapUseCase
import com.nendo.argosy.domain.usecase.recap.MonthlyRecapOverride
import com.nendo.argosy.domain.usecase.recap.RecapEligibility
import com.nendo.argosy.domain.usecase.recap.RecapGate
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MonthlyRecapCardState {
    val month: YearMonth

    data class Loading(override val month: YearMonth) : MonthlyRecapCardState
    data class Ready(override val month: YearMonth, val digest: MonthlyDigest) : MonthlyRecapCardState
    data class Empty(override val month: YearMonth) : MonthlyRecapCardState
    data class Failed(override val month: YearMonth) : MonthlyRecapCardState
}

/**
 * The monthly recap card shared by every surface: what it shows, its unlock spotlight, and the
 * input it takes while open. Home asks for the automatic card once per render; reopening shows
 * last month's card whatever the setting and the shown-once rule say.
 */
@Singleton
class MonthlyRecapController internal constructor(
    private val gate: RecapGate,
    private val getMonthlyRecap: GetMonthlyRecapUseCase,
    private val overrides: Set<MonthlyRecapOverride>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    @Inject constructor(
        gate: RecapGate,
        getMonthlyRecap: GetMonthlyRecapUseCase,
        overrides: Set<@JvmSuppressWildcards MonthlyRecapOverride>
    ) : this(
        gate,
        getMonthlyRecap,
        overrides,
        SafeCoroutineScope(Dispatchers.Main.immediate, TAG),
        Dispatchers.IO
    )

    private val _state = MutableStateFlow<MonthlyRecapCardState?>(null)
    val state: StateFlow<MonthlyRecapCardState?> = _state.asStateFlow()

    private val _cursor = MutableStateFlow(RecapCursor())
    val cursor: StateFlow<RecapCursor> = _cursor.asStateFlow()

    private var dueCheck: Job? = null
    private var load: Job? = null

    fun onHomeShown() {
        if (_state.value != null || dueCheck?.isActive == true) return
        dueCheck = scope.launch {
            try {
                val forced = withContext(ioDispatcher) { overrides.firstNotNullOfOrNull { it.take() } }
                if (forced != null) {
                    open(forced)
                    return@launch
                }
                val due = withContext(ioDispatcher) { gate.monthlyDigestDue() } ?: return@launch
                open(due, markShownWhenReady = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug(TAG, "Monthly recap check skipped: ${e.message}")
            }
        }
    }

    fun onHomeHidden() {
        dueCheck?.cancel()
        dueCheck = null
    }

    fun openLastMonth(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        open(RecapEligibility.digestMonthAt(now, zone))
    }

    fun close() {
        load?.cancel()
        load = null
        _state.value = null
    }

    fun centered(index: Int) {
        val count = unlockCount() ?: return
        _cursor.update { it.centered(index, count) }
    }

    private fun scrub(delta: Int): InputResult {
        val count = unlockCount() ?: return InputResult.handled(SoundType.SILENT)
        if (count == 0) return InputResult.handled(SoundType.SILENT)
        var moved = false
        _cursor.update { cursor -> cursor.scrubbed(delta, count).also { moved = it.currentIndex != cursor.currentIndex } }
        return InputResult.handled(if (moved) SoundType.NAVIGATE else SoundType.BOUNDARY)
    }

    private fun scrollBody(direction: Int): InputResult {
        if (_state.value == null) return InputResult.HANDLED
        _cursor.update { it.bodyScrolled(direction) }
        return InputResult.handled(SoundType.SILENT)
    }

    private fun unlockCount(): Int? = (_state.value as? MonthlyRecapCardState.Ready)?.digest?.unlocks?.size

    private fun open(month: YearMonth, markShownWhenReady: Boolean = false) {
        load?.cancel()
        _cursor.value = RecapCursor()
        _state.value = MonthlyRecapCardState.Loading(month)
        load = scope.launch {
            val loaded = try {
                val digest = withContext(ioDispatcher) { getMonthlyRecap(month) }
                if (digest.isEmpty) MonthlyRecapCardState.Empty(month) else MonthlyRecapCardState.Ready(month, digest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error(TAG, "Monthly recap for $month failed to load", e)
                MonthlyRecapCardState.Failed(month)
            }
            if (markShownWhenReady && loaded is MonthlyRecapCardState.Ready) markShown(month)
            _state.update { current -> if (current == MonthlyRecapCardState.Loading(month)) loaded else current }
        }
    }

    private suspend fun markShown(month: YearMonth) {
        try {
            withContext(ioDispatcher) { gate.markDigestShown(month) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "Monthly recap for $month not marked shown: ${e.message}")
        }
    }

    private fun closed(): InputResult {
        if (_state.value == null) return InputResult.HANDLED
        close()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    val inputHandler: InputHandler = object : InputHandler {
        override fun onConfirm(): InputResult = closed()
        override fun onBack(): InputResult = closed()
        override fun onRightStickLeft(): InputResult = scrub(-1)
        override fun onRightStickRight(): InputResult = scrub(1)
        override fun onUp(): InputResult = scrollBody(-1)
        override fun onDown(): InputResult = scrollBody(1)
        override fun onRightStickUp(): InputResult = scrollBody(-1)
        override fun onRightStickDown(): InputResult = scrollBody(1)
        override fun onLeft(): InputResult = scrub(-1)
        override fun onRight(): InputResult = scrub(1)
        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
    }

    private companion object {
        const val TAG = "MonthlyRecap"
    }
}
