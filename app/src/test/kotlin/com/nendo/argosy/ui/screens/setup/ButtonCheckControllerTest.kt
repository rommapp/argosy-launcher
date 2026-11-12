package com.nendo.argosy.ui.screens.setup

import android.view.KeyEvent
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ControllerPosition
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckController
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckExit
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckFocus
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.setup.components.checkedButton
import com.nendo.argosy.ui.screens.setup.components.checkedKeyButton
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonCheckControllerTest {

    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)

    private fun TestScope.controller(prefs: UserPreferences = UserPreferences(), showsBack: Boolean = true): ButtonCheckController {
        every { preferences.userPreferences } returns flowOf(prefs)
        return ButtonCheckController(preferences, this, showsBack).also { advanceUntilIdle() }
    }

    @Test
    fun `focus starts on the diagram and right or down enters the toggles`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        assertEquals(ButtonCheckFocus.DIAGRAM, check.state.value.focus)
        check.move(1, 0)
        assertEquals(ButtonCheckFocus.SWAP_AB, check.state.value.focus)
        check.reset()
        check.move(0, 1)
        assertEquals(ButtonCheckFocus.SWAP_AB, check.state.value.focus)
    }

    @Test
    fun `a on a toggle flips it and writes the swap preference`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.focus(ButtonCheckFocus.SWAP_AB)
        check.confirm()
        check.focus(ButtonCheckFocus.SWAP_XY)
        check.confirm()
        check.focus(ButtonCheckFocus.SWAP_START_SELECT)
        check.confirm()
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setSwapAB(true) }
        coVerify(exactly = 1) { preferences.setSwapXY(true) }
        coVerify(exactly = 1) { preferences.setSwapStartSelect(true) }
        assertTrue(check.state.value.swapAB)
    }

    @Test
    fun `left and right on a toggle never change it`() = runTest(StandardTestDispatcher()) {
        val check = controller(UserPreferences(swapAB = true))
        check.focus(ButtonCheckFocus.SWAP_AB)
        check.move(-1, 0)
        check.move(1, 0)
        advanceUntilIdle()
        coVerify(exactly = 0) { preferences.setSwapAB(any()) }
        assertTrue(check.state.value.swapAB)
        assertEquals(ButtonCheckFocus.SWAP_AB, check.state.value.focus)
    }

    @Test
    fun `the toggles start from the stored swaps`() = runTest(StandardTestDispatcher()) {
        val check = controller(UserPreferences(swapXY = true, swapStartSelect = true))
        assertFalse(check.state.value.swapAB)
        assertTrue(check.state.value.swapXY)
        assertTrue(check.state.value.swapStartSelect)
    }

    @Test
    fun `a test press on the diagram never exits or writes anything`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.light(ControllerPosition.BOTTOM, InputButton.A)
        val (_, exit) = check.confirm()
        advanceUntilIdle()

        assertNull(exit)
        assertEquals(ControllerPosition.BOTTOM, check.state.value.lit)
        assertEquals(InputButton.A, check.state.value.lastPress)
        coVerify(exactly = 0) { preferences.setSwapAB(any()) }
    }

    @Test
    fun `b returns to the diagram from the toggles and declines on the diagram`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.focus(ButtonCheckFocus.SWAP_XY)
        assertTrue(check.back())
        assertEquals(ButtonCheckFocus.DIAGRAM, check.state.value.focus)
        assertFalse(check.back())
    }

    @Test
    fun `back and continue name their exits`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.focus(ButtonCheckFocus.SWAP_START_SELECT)
        check.move(0, 1)
        assertEquals(ButtonCheckFocus.CONTINUE, check.state.value.focus)
        assertEquals(ButtonCheckExit.CONTINUE, check.confirm().second)
        check.move(-1, 0)
        assertEquals(ButtonCheckFocus.BACK, check.state.value.focus)
        assertEquals(ButtonCheckExit.BACK, check.confirm().second)
    }

    @Test
    fun `without back the column ends on continue`() = runTest(StandardTestDispatcher()) {
        val check = controller(showsBack = false)
        check.focus(ButtonCheckFocus.SWAP_START_SELECT)
        check.move(0, 1)
        assertEquals(ButtonCheckFocus.CONTINUE, check.state.value.focus)
        check.move(-1, 0)
        assertEquals(ButtonCheckFocus.CONTINUE, check.state.value.focus)
    }

    @Test
    fun `dispatched face and menu events name the intent buttons`() {
        assertEquals(InputButton.A, GamepadEvent.Confirm.checkedButton())
        assertEquals(InputButton.B, GamepadEvent.Back.checkedButton())
        assertEquals(InputButton.X, GamepadEvent.ContextMenu.checkedButton())
        assertEquals(InputButton.Y, GamepadEvent.SecondaryAction.checkedButton())
        assertEquals(InputButton.START, GamepadEvent.Menu.checkedButton())
        assertEquals(InputButton.SELECT, GamepadEvent.Select.checkedButton())
        assertNull(GamepadEvent.Up.checkedButton())
    }

    @Test
    fun `d-pad keys are never read as checked buttons`() {
        val dpad = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ENTER,
        )
        dpad.forEach { assertNull(checkedKeyButton(it) { code -> mapKeycodeToGamepadEvent(code) }) }
    }

    @Test
    fun `face start and select keys are read through the swap mapping`() {
        val swapped: (Int) -> GamepadEvent? = { mapKeycodeToGamepadEvent(it, swapAB = true) }
        assertEquals(InputButton.B, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_A, swapped))
        assertEquals(InputButton.A, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_B, swapped))
        assertEquals(InputButton.X, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_X, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.Y, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_Y, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.START, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_START, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.SELECT, checkedKeyButton(KeyEvent.KEYCODE_BUTTON_SELECT, ::mapKeycodeToGamepadEvent))
        assertNull(checkedKeyButton(KeyEvent.KEYCODE_BUTTON_L1, ::mapKeycodeToGamepadEvent))
    }

    @Test
    fun `a position stays lit only until its own button is released`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.light(ControllerPosition.BOTTOM, InputButton.A)
        check.release(ControllerPosition.RIGHT)
        assertEquals(ControllerPosition.BOTTOM, check.state.value.lit)

        check.release(ControllerPosition.BOTTOM)
        assertNull(check.state.value.lit)
        assertEquals(InputButton.A, check.state.value.lastPress)
    }

    @Test
    fun `leaving the diagram clears the lit position`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.light(ControllerPosition.TOP, InputButton.Y)
        check.move(1, 0)
        assertEquals(ButtonCheckFocus.SWAP_AB, check.state.value.focus)
        assertNull(check.state.value.lit)
    }
}
