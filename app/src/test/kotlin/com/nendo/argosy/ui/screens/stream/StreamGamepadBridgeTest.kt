package com.nendo.argosy.ui.screens.stream

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamGamepadBridgeTest {

    private val bridge = StreamGamepadBridge()

    @Test
    fun `face buttons map to standard gamepad indices and release`() {
        assertTrue(bridge.onKey(KeyEvent.KEYCODE_BUTTON_A, pressed = true))
        assertTrue(bridge.onKey(KeyEvent.KEYCODE_BUTTON_Y, pressed = true))
        assertEquals(1f, bridge.buttonValue(0))
        assertEquals(1f, bridge.buttonValue(3))

        bridge.onKey(KeyEvent.KEYCODE_BUTTON_A, pressed = false)
        assertEquals(0f, bridge.buttonValue(0))
    }

    @Test
    fun `start, select and the d-pad keys map to their standard slots`() {
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_START, pressed = true)
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_SELECT, pressed = true)
        bridge.onKey(KeyEvent.KEYCODE_DPAD_LEFT, pressed = true)
        assertEquals(1f, bridge.buttonValue(9))
        assertEquals(1f, bridge.buttonValue(8))
        assertEquals(1f, bridge.buttonValue(14))
    }

    @Test
    fun `a key with no gamepad meaning is not taken`() {
        assertFalse(bridge.onKey(KeyEvent.KEYCODE_A, pressed = true))
    }

    @Test
    fun `sticks fill the axes, triggers fill their buttons and the hat drives the d-pad`() {
        val values = mapOf(
            MotionEvent.AXIS_X to -0.5f,
            MotionEvent.AXIS_Y to 0.25f,
            MotionEvent.AXIS_Z to 1f,
            MotionEvent.AXIS_RZ to -1f,
            MotionEvent.AXIS_LTRIGGER to 0.75f,
            MotionEvent.AXIS_GAS to 1f,
            MotionEvent.AXIS_HAT_X to 1f,
            MotionEvent.AXIS_HAT_Y to -1f
        )
        bridge.onMotion { values[it] ?: 0f }

        assertEquals(-0.5f, bridge.axisValue(0))
        assertEquals(0.25f, bridge.axisValue(1))
        assertEquals(1f, bridge.axisValue(2))
        assertEquals(-1f, bridge.axisValue(3))
        assertEquals(0.75f, bridge.buttonValue(6))
        assertEquals(1f, bridge.buttonValue(7))
        assertEquals(1f, bridge.buttonValue(15))
        assertEquals(1f, bridge.buttonValue(12))
        assertEquals(0f, bridge.buttonValue(14))
    }

    @Test
    fun `a trigger held as a key stays held through stick motion`() {
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_L2, pressed = true)
        bridge.onMotion { if (it == MotionEvent.AXIS_X) 0.5f else 0f }

        assertEquals(1f, bridge.buttonValue(6))

        bridge.onKey(KeyEvent.KEYCODE_BUTTON_L2, pressed = false)
        assertEquals(0f, bridge.buttonValue(6))
    }

    @Test
    fun `an analog trigger stays pressed when its digital key releases`() {
        bridge.onMotion { if (it == MotionEvent.AXIS_RTRIGGER) 0.6f else 0f }
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_R2, pressed = true)
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_R2, pressed = false)

        assertEquals(0.6f, bridge.buttonValue(7))
    }

    @Test
    fun `releasing the hat clears the d-pad`() {
        bridge.onMotion { if (it == MotionEvent.AXIS_HAT_Y) 1f else 0f }
        assertEquals(1f, bridge.buttonValue(13))
        bridge.onMotion { 0f }
        assertEquals(0f, bridge.buttonValue(13))
    }

    @Test
    fun `the push script carries every button and axis`() {
        bridge.onKey(KeyEvent.KEYCODE_BUTTON_B, pressed = true)
        val script = bridge.script()
        assertTrue(script.startsWith("window.__argosyPadSet&&window.__argosyPadSet([0.0,1.0,"))
        val values = script.substringAfter("set(").substringBeforeLast(")")
            .replace("[", "").replace("]", "").split(",")
        assertEquals(StreamGamepadBridge.BUTTON_COUNT + StreamGamepadBridge.AXIS_COUNT, values.size)
    }
}
