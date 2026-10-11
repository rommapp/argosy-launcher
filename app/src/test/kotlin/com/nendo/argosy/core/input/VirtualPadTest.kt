package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPadTest {

    private fun event(
        virtual: Boolean,
        source: Int,
        keyCode: Int = KeyEvent.KEYCODE_DPAD_UP
    ): KeyEvent {
        val device: InputDevice = mockk { every { isVirtual } returns virtual }
        return mockk {
            every { this@mockk.device } returns device
            every { this@mockk.source } returns source
            every { this@mockk.keyCode } returns keyCode
        }
    }

    @Test
    fun `only a virtual device sending pad input counts, and only once`() {
        val keyboard = InputDevice.SOURCE_KEYBOARD
        assertFalse(VirtualPad.observe(event(virtual = true, source = keyboard)))
        assertFalse(VirtualPad.observe(event(virtual = true, source = InputDevice.SOURCE_DPAD)))
        assertFalse(VirtualPad.observe(event(virtual = false, source = InputDevice.SOURCE_GAMEPAD)))
        assertFalse(
            VirtualPad.observe(event(virtual = false, source = keyboard, keyCode = KeyEvent.KEYCODE_BUTTON_A))
        )
        assertFalse(VirtualPad.seen)

        assertTrue(
            VirtualPad.observe(event(virtual = true, source = keyboard, keyCode = KeyEvent.KEYCODE_BUTTON_A))
        )
        assertTrue(VirtualPad.seen)
        assertFalse(VirtualPad.observe(event(virtual = true, source = 16778257)))
    }
}
