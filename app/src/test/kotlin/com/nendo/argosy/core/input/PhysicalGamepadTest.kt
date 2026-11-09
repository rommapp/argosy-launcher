package com.nendo.argosy.core.input

import android.view.InputDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalGamepadTest {

    private fun device(sources: Int, name: String = "Pad", virtual: Boolean = false): InputDevice =
        mockk {
            every { this@mockk.sources } returns sources
            every { isVirtual } returns virtual
            every { this@mockk.name } returns name
        }

    @Test
    fun `a controller with gamepad buttons is a pad`() {
        val pad = device(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_KEYBOARD)

        assertTrue(pad.isPhysicalGamepad())
    }

    @Test
    fun `a remote reporting only a joystick source is not a pad`() {
        val remote = device(
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_MOUSE or
                InputDevice.SOURCE_JOYSTICK,
            name = "Bluetooth HID Consumer Control"
        )

        assertFalse(remote.isPhysicalGamepad())
    }

    @Test
    fun `a virtual device with a gamepad source is not a pad`() {
        assertFalse(device(InputDevice.SOURCE_GAMEPAD, virtual = true).isPhysicalGamepad())
    }

    @Test
    fun `an OEM uinput device with a gamepad source is not a pad`() {
        assertFalse(device(InputDevice.SOURCE_GAMEPAD, name = "uinput-xiaomi").isPhysicalGamepad())
    }
}
