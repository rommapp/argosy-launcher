package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.libretro.ControllerSeating
import com.nendo.argosy.ui.input.GamepadEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SeatHolder(
    val controllerId: String,
    val name: String?,
    val deviceId: Int? = null
)

data class ControllerSeatState(
    val seats: List<SeatHolder?> = List(ControllerSeating.PORT_COUNT) { null }
)

enum class SeatKeyOutcome { NONE, CLAIMED, RELEASED, CLEARED, READY, CLOSE }

fun claimSeat(seats: List<SeatHolder?>, holder: SeatHolder): List<SeatHolder?> {
    if (seats.any { it?.controllerId == holder.controllerId }) return seats
    val open = seats.indexOfFirst { it == null }
    if (open < 0) return seats
    return seats.toMutableList().also { it[open] = holder }
}

/**
 * Switch-style seating: a controller takes the next open seat with L and R held together, or
 * with A. A on a seated controller finishes. Each change is written to the saved controller order.
 */
class ControllerSeatController(
    private val repository: InputConfigRepository,
    private val scope: CoroutineScope,
    private val onSeatsChanged: suspend () -> Unit = {}
) {
    private val _state = MutableStateFlow(ControllerSeatState())
    val state: StateFlow<ControllerSeatState> = _state.asStateFlow()
    private val writes = Mutex()
    private val heldShoulders = mutableMapOf<String, Set<GamepadEvent>>()
    private var replacesSavedOrder = false

    fun open() {
        replacesSavedOrder = true
        heldShoulders.clear()
        _state.value = ControllerSeatState()
    }

    fun onPadKey(event: GamepadEvent?, isDown: Boolean, pad: InputDevice?): SeatKeyOutcome =
        onPadKey(event, isDown, pad?.let { SeatHolder(controllerIdOf(it), it.name, it.id) })

    /**
     * Applies one key transition from the seat screen, from [pad] or from a source that is not a
     * controller when null.
     */
    fun onPadKey(event: GamepadEvent?, isDown: Boolean, pad: SeatHolder?): SeatKeyOutcome {
        if (event == GamepadEvent.PrevSection || event == GamepadEvent.NextSection) {
            return pad?.let { trackShoulder(it, event, isDown) } ?: SeatKeyOutcome.NONE
        }
        if (!isDown) return SeatKeyOutcome.NONE
        return when (event) {
            GamepadEvent.Back -> if (pad != null && isSeated(pad.controllerId)) {
                release(pad.controllerId)
                SeatKeyOutcome.RELEASED
            } else {
                SeatKeyOutcome.CLOSE
            }
            GamepadEvent.SecondaryAction -> {
                clearAll()
                SeatKeyOutcome.CLEARED
            }
            GamepadEvent.Confirm -> when {
                pad == null || isSeated(pad.controllerId) -> SeatKeyOutcome.READY
                else -> claim(pad)
            }
            else -> SeatKeyOutcome.NONE
        }
    }

    private fun trackShoulder(pad: SeatHolder, shoulder: GamepadEvent, isDown: Boolean): SeatKeyOutcome {
        val held = heldShoulders[pad.controllerId].orEmpty()
        val now = if (isDown) held + shoulder else held - shoulder
        heldShoulders[pad.controllerId] = now
        val chord = isDown && GamepadEvent.PrevSection in now && GamepadEvent.NextSection in now
        return if (chord) claim(pad) else SeatKeyOutcome.NONE
    }

    private fun isSeated(controllerId: String): Boolean =
        _state.value.seats.any { it?.controllerId == controllerId }

    private fun claim(pad: SeatHolder): SeatKeyOutcome {
        val before = _state.value.seats
        val after = claimSeat(before, pad)
        if (after == before) return SeatKeyOutcome.NONE
        _state.update { it.copy(seats = after) }
        persist(pad.controllerId, pad.name ?: pad.controllerId, after.indexOfFirst { it?.controllerId == pad.controllerId })
        return SeatKeyOutcome.CLAIMED
    }

    private fun release(controllerId: String) {
        val port = _state.value.seats.indexOfFirst { it?.controllerId == controllerId }
        if (port >= 0) releaseSeat(port)
    }

    fun releaseSeat(port: Int) {
        val holder = _state.value.seats.getOrNull(port) ?: return
        _state.update { state -> state.copy(seats = state.seats.toMutableList().also { it[port] = null }) }
        persist(holder.controllerId, holder.name ?: holder.controllerId, null)
    }

    fun clearAll() {
        replacesSavedOrder = false
        _state.update { state -> state.copy(seats = List(ControllerSeating.PORT_COUNT) { null }) }
        scope.launch {
            writes.withLock {
                repository.clearControllerOrder()
                onSeatsChanged()
            }
        }
    }

    private fun persist(controllerId: String, name: String, port: Int?) {
        val clearFirst = replacesSavedOrder
        replacesSavedOrder = false
        scope.launch {
            writes.withLock {
                if (clearFirst) repository.clearControllerOrder()
                repository.seatController(controllerId, name, port)
                onSeatsChanged()
            }
        }
    }
}
