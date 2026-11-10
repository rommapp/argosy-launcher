package com.nendo.argosy.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.ui.components.launch.LaunchHostSignal
import com.nendo.argosy.ui.components.launch.recordsHostStop
import com.nendo.argosy.ui.components.launch.shouldReportHostStopped
import com.nendo.argosy.ui.components.recap.LaunchRecapCursor
import com.nendo.argosy.ui.components.recap.RecapCursor
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

private data class PromptFocusSeed(val anchor: Any, val initialFocus: Int)

private fun focusSeed(step: LaunchStep): PromptFocusSeed? = when (step) {
    is LaunchStep.Question -> PromptFocusSeed(step, 0)
    is LaunchStep.MakeRoom -> PromptFocusSeed(step.candidates, step.initialFocus)
    else -> null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LaunchOverlayViewModel @Inject constructor(
    private val tracker: LaunchProgressTracker,
    private val recapCursor: LaunchRecapCursor
) : ViewModel() {

    val progress: StateFlow<LaunchProgress?> = tracker.progress

    private val _promptFocus = MutableStateFlow(0)
    val promptFocus: StateFlow<Int> = _promptFocus.asStateFlow()

    val recapCursorState: StateFlow<RecapCursor> = recapCursor.state

    /**
     * Whether the screen layout gives the launcher a presentation surface, followed across a
     * dual-screen manager created or replaced after this ViewModel.
     */
    val hasPresentationSurface: StateFlow<Boolean> = DualScreenManagerHolder.instanceFlow
        .flatMapLatest { it?.hasPresentationScreen ?: flowOf(false) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DualScreenManagerHolder.instance?.hasPresentationScreen?.value ?: false
        )

    /**
     * The display the launch placed the game on, null with no dual-screen manager.
     */
    val gameDisplayId: StateFlow<Int?> = DualScreenManagerHolder.instanceFlow
        .flatMapLatest { it?.emulatorDisplay ?: flowOf(null) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DualScreenManagerHolder.instance?.emulatorDisplay?.value
        )

    init {
        viewModelScope.launch {
            tracker.progress
                .map { it?.step?.let(::focusSeed) }
                .distinctUntilChanged()
                .collect { seed -> _promptFocus.value = seed?.initialFocus ?: 0 }
        }
        viewModelScope.launch {
            tracker.progress
                .map { (it?.step as? LaunchStep.Recap)?.recap }
                .distinctUntilChanged()
                .collect { recapCursor.reset() }
        }
    }

    fun answer(option: LaunchPromptOption) = tracker.answer(option)

    fun cancel(): Boolean = tracker.cancel()

    /**
     * A tap on the make-room card at [index]: the focused card closes, any other takes focus.
     */
    fun tapMakeRoomCard(index: Int) {
        val step = choosingRoom ?: return
        val candidate = step.candidates.getOrNull(index) ?: return
        if (index == _promptFocus.value) {
            tracker.closeToMakeRoom(candidate.taskId)
        } else {
            _promptFocus.update { index }
        }
    }

    fun closeFocusedToMakeRoom() {
        val candidate = choosingRoom?.candidates?.getOrNull(_promptFocus.value) ?: return
        tracker.closeToMakeRoom(candidate.taskId)
    }

    private var hostStopRecorded = false

    /**
     * Passes [signal] from the overlay's host to the tracker as a stop, unless the host is being
     * recreated or, for a return, no real stop came before it.
     */
    fun hostSignal(signal: LaunchHostSignal, isChangingConfigurations: Boolean) {
        if (!shouldReportHostStopped(isChangingConfigurations, signal, hostStopRecorded)) return
        hostStopRecorded = recordsHostStop(signal)
        tracker.hostStopped()
    }

    fun recapCurrent(index: Int) {
        val count = recap?.unlocks?.size ?: return
        recapCursor.centered(index, count)
    }

    private val prompt: LaunchStep.Question? get() = progress.value?.step as? LaunchStep.Question

    private val recap: LaunchRecap? get() = (progress.value?.step as? LaunchStep.Recap)?.recap

    private val makeRoom: LaunchStep.MakeRoom? get() = progress.value?.step as? LaunchStep.MakeRoom

    private val choosingRoom: LaunchStep.MakeRoom?
        get() = makeRoom?.takeIf { it.stage == MakeRoomStage.CHOOSE && it.candidates.isNotEmpty() }

    private fun moveRoomFocus(delta: Int): InputResult {
        val step = choosingRoom ?: return InputResult.HANDLED
        if (step.candidates.size < 2) return InputResult.handled(SoundType.BOUNDARY)
        _promptFocus.update { (it + delta).mod(step.candidates.size) }
        return InputResult.handled(SoundType.NAVIGATE)
    }

    private fun moveSideways(delta: Int): InputResult =
        if (makeRoom != null) moveRoomFocus(delta) else scrubRecap(delta)

    private fun movePromptFocus(delta: Int): InputResult {
        val step = prompt ?: return InputResult.HANDLED
        _promptFocus.update { (it + delta).coerceIn(0, step.options.size) }
        return InputResult.handled(SoundType.NAVIGATE)
    }

    private fun scrubRecap(delta: Int): InputResult {
        val count = recap?.unlocks?.size ?: return InputResult.HANDLED
        if (count == 0) return InputResult.handled(SoundType.SILENT)
        val moved = recapCursor.scrub(delta, count)
        return InputResult.handled(if (moved) SoundType.NAVIGATE else SoundType.BOUNDARY)
    }

    private fun scrollRecap(direction: Int): InputResult {
        if (recap == null) return InputResult.HANDLED
        recapCursor.scrollBody(direction)
        return InputResult.handled(SoundType.SILENT)
    }

    val inputHandler: InputHandler = object : InputHandler {
        override fun onUp(): InputResult = if (recap != null) scrollRecap(-1) else movePromptFocus(-1)
        override fun onDown(): InputResult = if (recap != null) scrollRecap(1) else movePromptFocus(1)
        override fun onRightStickUp(): InputResult = scrollRecap(-1)
        override fun onRightStickDown(): InputResult = scrollRecap(1)

        override fun onConfirm(): InputResult {
            if (recap != null) {
                answer(LaunchPromptOption.START_GAME)
                return InputResult.handled(SoundType.SELECT)
            }
            if (makeRoom != null) {
                if (choosingRoom == null) return InputResult.HANDLED
                closeFocusedToMakeRoom()
                return InputResult.handled(SoundType.SELECT)
            }
            val step = prompt ?: return InputResult.HANDLED
            val option = step.options.getOrNull(_promptFocus.value)
            if (option != null) answer(option) else cancel()
            return InputResult.handled(SoundType.SELECT)
        }

        override fun onBack(): InputResult =
            if (cancel()) InputResult.handled(SoundType.CLOSE_MODAL) else InputResult.HANDLED

        override fun onLeft(): InputResult = moveSideways(-1)
        override fun onRight(): InputResult = moveSideways(1)
        override fun onRightStickLeft(): InputResult = scrubRecap(-1)
        override fun onRightStickRight(): InputResult = scrubRecap(1)
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
    }
}
