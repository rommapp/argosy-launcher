package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeysTest {

    private fun device(keyboardType: Int): InputDevice = mockk {
        every { this@mockk.keyboardType } returns keyboardType
    }

    @Test
    fun `the system Home key is Home from any device`() {
        assertTrue(HomeKeys.isSystemHome(KeyEvent.KEYCODE_HOME, null))
        assertTrue(HomeKeys.isSystemHome(KeyEvent.KEYCODE_HOME, device(InputDevice.KEYBOARD_TYPE_ALPHABETIC)))
    }

    @Test
    fun `a handheld Home button sending MOVE_HOME is Home`() {
        assertTrue(HomeKeys.isSystemHome(KeyEvent.KEYCODE_MOVE_HOME, device(InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)))
        assertTrue(HomeKeys.isSystemHome(KeyEvent.KEYCODE_MOVE_HOME, null))
    }

    @Test
    fun `a keyboard's Home key keeps moving the cursor`() {
        assertFalse(HomeKeys.isSystemHome(KeyEvent.KEYCODE_MOVE_HOME, device(InputDevice.KEYBOARD_TYPE_ALPHABETIC)))
    }

    @Test
    fun `the guide button is Home but not the system Home key`() {
        assertTrue(HomeKeys.isGuide(KeyEvent.KEYCODE_BUTTON_MODE))
        assertFalse(HomeKeys.isSystemHome(KeyEvent.KEYCODE_BUTTON_MODE, null))
        assertFalse(HomeKeys.isGuide(KeyEvent.KEYCODE_HOME))
    }

    @Test
    fun `every Home keycode is kept from bindings`() {
        assertTrue(HomeKeys.SYSTEM.containsAll(setOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MOVE_HOME)))
        assertTrue(KeyEvent.KEYCODE_BUTTON_MODE in HomeKeys.RESERVED)
        assertFalse(com.nendo.argosy.hardware.SystemHotkeyCombos.isRecordable(KeyEvent.KEYCODE_MOVE_HOME))
        assertFalse(com.nendo.argosy.libretro.HotkeyManager.isHotkeyKey(KeyEvent.KEYCODE_MOVE_HOME))
    }
}
