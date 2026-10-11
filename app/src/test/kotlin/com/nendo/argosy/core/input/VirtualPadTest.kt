package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPadTest {

    private fun event(virtual: Boolean, source: Int): KeyEvent {
        val device: InputDevice = mockk { every { isVirtual } returns virtual }
        return mockk {
            every { this@mockk.device } returns device
            every { this@mockk.source } returns source
        }
    }

    @Test
    fun `only a virtual device sending pad input counts, and only once`() {
        assertFalse(VirtualPad.observe(event(virtual = true, source = InputDevice.SOURCE_KEYBOARD)))
        assertFalse(VirtualPad.observe(event(virtual = true, source = InputDevice.SOURCE_DPAD)))
        assertFalse(VirtualPad.observe(event(virtual = false, source = InputDevice.SOURCE_GAMEPAD)))
        assertFalse(VirtualPad.seen)

        val firmwarePad = 16778257
        assertTrue(VirtualPad.observe(event(virtual = true, source = firmwarePad)))
        assertTrue(VirtualPad.seen)
        assertFalse(VirtualPad.observe(event(virtual = true, source = firmwarePad)))
    }
}
