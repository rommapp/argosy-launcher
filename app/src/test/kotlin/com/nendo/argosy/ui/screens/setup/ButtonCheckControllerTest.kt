package com.nendo.argosy.ui.screens.setup

import android.view.KeyEvent
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckController
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckExit
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckFocus
import com.nendo.argosy.ui.screens.setup.components.PadAxes
import com.nendo.argosy.ui.screens.setup.components.checkActionRes
import com.nendo.argosy.ui.screens.setup.components.checkedButton
import com.nendo.argosy.ui.screens.setup.components.checkedPadButton
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonCheckControllerTest {

    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)

    private val holdMs = ComponentDefaults.Setup.checkExitHoldMs.toLong()

    private fun TestScope.controller(prefs: UserPreferences = UserPreferences(), showsBack: Boolean = true): ButtonCheckController {
        every { preferences.userPreferences } returns flowOf(prefs)
        return ButtonCheckController(preferences, this, showsBack).also { advanceUntilIdle() }
    }

    private fun ButtonCheckController.down(keyCode: Int, button: InputButton?): Boolean =
        onKey(keyCode, KeyEvent.ACTION_DOWN, 0, button)

    private fun ButtonCheckController.up(keyCode: Int, button: InputButton?): Boolean =
        onKey(keyCode, KeyEvent.ACTION_UP, 0, button)

    @Test
    fun `the page opens in settings mode with focus on test buttons`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        assertEquals(ButtonCheckFocus.TEST, check.state.value.focus)
        assertFalse(check.state.value.testing)
        assertFalse(check.state.value.capturesKeys)
        assertEquals(SoundType.BOUNDARY, check.move(0, -1).soundOverride)
        check.move(0, 1)
        assertEquals(ButtonCheckFocus.SWAP_AB, check.state.value.focus)
    }

    @Test
    fun `a on test buttons enters test mode and nothing is written`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        val (_, exit) = check.confirm()
        advanceUntilIdle()

        assertNull(exit)
        assertTrue(check.state.value.testing)
        assertTrue(check.state.value.capturesKeys)
        coVerify(exactly = 0) { preferences.setSwapAB(any()) }
    }

    @Test
    fun `in test mode navigation and confirm do nothing`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()

        assertEquals(SoundType.SILENT, check.move(0, 1).soundOverride)
        assertEquals(ButtonCheckFocus.TEST, check.state.value.focus)
        assertEquals(null, check.confirm().second)
        assertNull(check.startPress())
        assertEquals(ButtonCheckFocus.TEST, check.state.value.focus)
        assertTrue(check.state.value.testing)
    }

    @Test
    fun `a button lights only while held and stays the last press`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()

        assertTrue(check.down(KeyEvent.KEYCODE_BUTTON_A, InputButton.A))
        assertTrue(InputButton.A in check.state.value.reading.held)
        assertTrue(check.up(KeyEvent.KEYCODE_BUTTON_A, InputButton.A))
        assertFalse(InputButton.A in check.state.value.reading.held)
        assertEquals(InputButton.A, check.state.value.reading.lastPress)
        assertTrue(check.state.value.testing)
    }

    @Test
    fun `holding one button past the threshold ends the test with focus on test buttons`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.move(0, 1)
        check.focus(ButtonCheckFocus.TEST)
        check.confirm()
        check.down(KeyEvent.KEYCODE_BUTTON_X, InputButton.X)
        assertEquals(InputButton.X, check.state.value.reading.holding)

        advanceTimeBy(holdMs - 1)
        runCurrent()
        assertTrue(check.state.value.testing)

        advanceTimeBy(2)
        runCurrent()
        assertFalse(check.state.value.testing)
        assertEquals(ButtonCheckFocus.TEST, check.state.value.focus)
        assertTrue(check.state.value.reading.held.isEmpty())
    }

    @Test
    fun `the release of the button that ended the test is swallowed once`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.down(KeyEvent.KEYCODE_BUTTON_B, InputButton.B)
        advanceTimeBy(holdMs + 1)
        runCurrent()
        assertTrue(check.state.value.releasing)
        assertTrue(check.state.value.capturesKeys)

        assertTrue(check.up(KeyEvent.KEYCODE_BUTTON_B, InputButton.B))
        assertFalse(check.state.value.releasing)
        assertFalse(check.state.value.capturesKeys)
        assertFalse(check.down(KeyEvent.KEYCODE_BUTTON_B, InputButton.B))
    }

    @Test
    fun `releasing early or holding two buttons never ends the test`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.down(KeyEvent.KEYCODE_BUTTON_A, InputButton.A)
        advanceTimeBy(holdMs / 2)
        check.up(KeyEvent.KEYCODE_BUTTON_A, InputButton.A)
        advanceTimeBy(holdMs)
        runCurrent()
        assertTrue(check.state.value.testing)

        check.down(KeyEvent.KEYCODE_BUTTON_A, InputButton.A)
        check.down(KeyEvent.KEYCODE_BUTTON_Y, InputButton.Y)
        assertNull(check.state.value.reading.holding)
        advanceTimeBy(holdMs * 2)
        runCurrent()
        assertTrue(check.state.value.testing)
        assertEquals(setOf(InputButton.A, InputButton.Y), check.state.value.reading.held)
    }

    @Test
    fun `stick deflection lights the drawing and never counts toward the hold`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.onMotion(PadAxes(leftX = 1f, rightY = -1f, leftTrigger = 1f))
        advanceTimeBy(holdMs * 2)
        runCurrent()

        assertTrue(check.state.value.testing)
        assertNull(check.state.value.reading.holding)
        assertEquals(1f, check.state.value.reading.axes.leftX)
    }

    @Test
    fun `a hat sample lights its d-pad direction without holding it`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.onMotion(PadAxes(hatY = -1f))
        assertTrue(check.state.value.reading.isLit(InputButton.DPAD_UP))
        assertFalse(check.state.value.reading.isLit(InputButton.DPAD_DOWN))
        assertNull(check.state.value.reading.holding)
    }

    @Test
    fun `keys without a pad button pass through in test mode and every key passes outside it`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        assertFalse(check.down(KeyEvent.KEYCODE_BUTTON_A, InputButton.A))
        check.startTest()
        assertFalse(check.down(KeyEvent.KEYCODE_BACK, null))
        assertTrue(check.down(KeyEvent.KEYCODE_DPAD_UP, InputButton.DPAD_UP))
    }

    @Test
    fun `back ends the test without leaving it`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.finishTest()
        assertFalse(check.state.value.testing)
        assertFalse(check.state.value.releasing)
        assertEquals(ButtonCheckFocus.TEST, check.state.value.focus)
    }

    @Test
    fun `touch toggles work while testing`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.startTest()
        check.focus(ButtonCheckFocus.SWAP_XY)
        check.setToggle(ButtonCheckFocus.SWAP_XY, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setSwapXY(true) }
        assertTrue(check.state.value.swapXY)
        assertEquals(setOf(InputButton.X, InputButton.Y), check.state.value.swappedButtons)
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
    fun `back and continue name their exits`() = runTest(StandardTestDispatcher()) {
        val check = controller()
        check.focus(ButtonCheckFocus.SWAP_START_SELECT)
        check.move(0, 1)
        assertEquals(ButtonCheckFocus.CONTINUE, check.state.value.focus)
        assertEquals(ButtonCheckExit.CONTINUE, check.confirm().second)
        check.move(-1, 0)
        assertEquals(ButtonCheckFocus.BACK, check.state.value.focus)
        assertEquals(ButtonCheckExit.BACK, check.confirm().second)
        check.move(0, -1)
        assertEquals(ButtonCheckFocus.SWAP_START_SELECT, check.state.value.focus)
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
    fun `back, enter and the d-pad centre have no pad button`() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_MODE)
            .forEach { assertNull(checkedPadButton(it) { code -> mapKeycodeToGamepadEvent(code) }) }
    }

    @Test
    fun `the d-pad, shoulders, triggers and stick clicks have fixed pad buttons`() {
        val swapped: (Int) -> GamepadEvent? = { mapKeycodeToGamepadEvent(it, swapAB = true) }
        assertEquals(InputButton.DPAD_UP, checkedPadButton(KeyEvent.KEYCODE_DPAD_UP, swapped))
        assertEquals(InputButton.DPAD_LEFT, checkedPadButton(KeyEvent.KEYCODE_DPAD_LEFT, swapped))
        assertEquals(InputButton.LB, checkedPadButton(KeyEvent.KEYCODE_BUTTON_L1, swapped))
        assertEquals(InputButton.RT, checkedPadButton(KeyEvent.KEYCODE_BUTTON_R2, swapped))
        assertEquals(InputButton.LEFT_STICK_CLICK, checkedPadButton(KeyEvent.KEYCODE_BUTTON_THUMBL, swapped))
        assertEquals(InputButton.RIGHT_STICK_CLICK, checkedPadButton(KeyEvent.KEYCODE_BUTTON_THUMBR, swapped))
    }

    @Test
    fun `face start and select keys are read through the swap mapping`() {
        val swapped: (Int) -> GamepadEvent? = { mapKeycodeToGamepadEvent(it, swapAB = true) }
        assertEquals(InputButton.B, checkedPadButton(KeyEvent.KEYCODE_BUTTON_A, swapped))
        assertEquals(InputButton.A, checkedPadButton(KeyEvent.KEYCODE_BUTTON_B, swapped))
        assertEquals(InputButton.X, checkedPadButton(KeyEvent.KEYCODE_BUTTON_X, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.Y, checkedPadButton(KeyEvent.KEYCODE_BUTTON_Y, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.START, checkedPadButton(KeyEvent.KEYCODE_BUTTON_START, ::mapKeycodeToGamepadEvent))
        assertEquals(InputButton.SELECT, checkedPadButton(KeyEvent.KEYCODE_BUTTON_SELECT, ::mapKeycodeToGamepadEvent))
    }

    @Test
    fun `the status line names menu actions and leaves x and y bare`() {
        assertEquals(R.string.setup_check_action_choose, InputButton.A.checkActionRes(null))
        assertEquals(R.string.setup_check_action_back, InputButton.B.checkActionRes(null))
        assertEquals(R.string.setup_check_action_menu, InputButton.START.checkActionRes(null))
        assertEquals(R.string.setup_check_action_hold_swap_screens, InputButton.SELECT.checkActionRes(SelectSwapMode.HOLD))
        assertEquals(R.string.setup_check_action_swap_screens, InputButton.SELECT.checkActionRes(SelectSwapMode.TAP))
        assertNull(InputButton.SELECT.checkActionRes(null))
        assertNull(InputButton.X.checkActionRes(SelectSwapMode.HOLD))
        assertNull(InputButton.Y.checkActionRes(SelectSwapMode.TAP))
    }
}
