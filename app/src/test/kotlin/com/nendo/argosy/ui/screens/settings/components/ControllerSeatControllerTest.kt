package com.nendo.argosy.ui.screens.settings.components

import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.ui.input.GamepadEvent
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerSeatControllerTest {

    private val empty: List<SeatHolder?> = List(4) { null }
    private val padA = SeatHolder("pad-a", "A")
    private val padB = SeatHolder("pad-b", "B")

    private val repository = mockk<InputConfigRepository>(relaxed = true)
    private val seats = ControllerSeatController(repository, CoroutineScope(Dispatchers.Unconfined)).also { it.open() }

    private fun press(event: GamepadEvent, pad: SeatHolder?) = seats.onPadKey(event, true, pad)
    private fun release(event: GamepadEvent, pad: SeatHolder?) = seats.onPadKey(event, false, pad)

    @Test
    fun `seats fill in the order controllers claim`() {
        val seated = claimSeat(claimSeat(empty, padB), padA)

        assertEquals(listOf(padB, padA, null, null), seated)
    }

    @Test
    fun `a seated controller claiming again keeps its seat`() {
        val seated = claimSeat(empty, padA)

        assertEquals(seated, claimSeat(seated, padA))
    }

    @Test
    fun `a fifth controller finds no seat`() {
        val full = listOf("a", "b", "c", "d").fold(empty) { acc, id -> claimSeat(acc, SeatHolder(id, id)) }

        assertEquals(full, claimSeat(full, SeatHolder("e", "e")))
    }

    @Test
    fun `holding L then R claims, in either order`() {
        assertEquals(SeatKeyOutcome.NONE, press(GamepadEvent.PrevSection, padA))
        assertEquals(SeatKeyOutcome.CLAIMED, press(GamepadEvent.NextSection, padA))
        assertEquals(SeatKeyOutcome.NONE, press(GamepadEvent.NextSection, padB))
        assertEquals(SeatKeyOutcome.CLAIMED, press(GamepadEvent.PrevSection, padB))

        assertEquals(listOf(padA, padB, null, null), seats.state.value.seats)
        coVerify { repository.seatController("pad-a", "A", 0) }
        coVerify { repository.seatController("pad-b", "B", 1) }
    }

    @Test
    fun `L and R pressed one after the other without holding do not claim`() {
        press(GamepadEvent.PrevSection, padA)
        release(GamepadEvent.PrevSection, padA)

        assertEquals(SeatKeyOutcome.NONE, press(GamepadEvent.NextSection, padA))
        assertEquals(empty, seats.state.value.seats)
    }

    @Test
    fun `shoulders held on two different controllers do not claim`() {
        press(GamepadEvent.PrevSection, padA)

        assertEquals(SeatKeyOutcome.NONE, press(GamepadEvent.NextSection, padB))
        assertEquals(empty, seats.state.value.seats)
    }

    @Test
    fun `A claims on an unseated controller and finishes on a seated one`() {
        assertEquals(SeatKeyOutcome.CLAIMED, press(GamepadEvent.Confirm, padA))
        assertEquals(SeatKeyOutcome.READY, press(GamepadEvent.Confirm, padA))
        assertEquals(listOf(padA, null, null, null), seats.state.value.seats)
    }

    @Test
    fun `A from a source that is not a controller finishes without claiming`() {
        assertEquals(SeatKeyOutcome.READY, press(GamepadEvent.Confirm, null))
        assertEquals(empty, seats.state.value.seats)
    }

    @Test
    fun `B from an unseated pad closes and Y clears every seat`() {
        press(GamepadEvent.Confirm, padA)

        assertEquals(SeatKeyOutcome.CLEARED, press(GamepadEvent.SecondaryAction, padB))
        assertEquals(empty, seats.state.value.seats)
        assertEquals(SeatKeyOutcome.CLOSE, press(GamepadEvent.Back, padB))
    }

    @Test
    fun `B from a seated pad gives up its seat and keeps the others`() {
        press(GamepadEvent.Confirm, padA)
        press(GamepadEvent.Confirm, padB)

        assertEquals(SeatKeyOutcome.RELEASED, press(GamepadEvent.Back, padA))
        assertEquals(listOf(null, padB, null, null), seats.state.value.seats)
        assertEquals(SeatKeyOutcome.CLOSE, press(GamepadEvent.Back, padA))
    }

    @Test
    fun `B from a source that is not a controller closes`() {
        press(GamepadEvent.Confirm, padA)

        assertEquals(SeatKeyOutcome.CLOSE, press(GamepadEvent.Back, null))
        assertEquals(listOf(padA, null, null, null), seats.state.value.seats)
    }

    @Test
    fun `other buttons and key releases change nothing`() {
        assertEquals(SeatKeyOutcome.NONE, press(GamepadEvent.ContextMenu, padA))
        assertEquals(SeatKeyOutcome.NONE, release(GamepadEvent.Confirm, padA))
        assertEquals(empty, seats.state.value.seats)
    }
}
