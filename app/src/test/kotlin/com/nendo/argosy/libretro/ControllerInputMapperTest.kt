package com.nendo.argosy.libretro

import android.view.KeyEvent
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.RetroButton
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerInputMapperTest {

    private val gbaWithoutTurboR = mapOf<InputSource, Int>(
        InputSource.Button(KeyEvent.KEYCODE_BUTTON_A) to RetroButton.A,
        InputSource.Button(KeyEvent.KEYCODE_BUTTON_B) to RetroButton.B,
        InputSource.Button(KeyEvent.KEYCODE_BUTTON_L1) to RetroButton.L,
        InputSource.Button(KeyEvent.KEYCODE_BUTTON_R1) to RetroButton.R
    )

    @Test
    fun `mapped button resolves to its retro button`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R1,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_R1)
        )
    }

    @Test
    fun `cleared button is silenced instead of falling through to the core`() {
        assertEquals(
            KeyEvent.KEYCODE_UNKNOWN,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_R2)
        )
    }

    @Test
    fun `remapped button resolves to its target, not its physical keycode`() {
        val swapped = mapOf<InputSource, Int>(
            InputSource.Button(KeyEvent.KEYCODE_BUTTON_A) to RetroButton.B,
            InputSource.Button(KeyEvent.KEYCODE_BUTTON_B) to RetroButton.A
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_B,
            ControllerInputMapper.resolveMappedKey(swapped, KeyEvent.KEYCODE_BUTTON_A)
        )
    }

    @Test
    fun `a bindable button the editor allows resolves rather than being dropped`() {
        val withC = gbaWithoutTurboR + (InputSource.Button(KeyEvent.KEYCODE_BUTTON_C) to RetroButton.R)
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R1,
            ControllerInputMapper.resolveMappedKey(withC, KeyEvent.KEYCODE_BUTTON_C)
        )
    }

    @Test
    fun `an unbound C and Z reach Genesis C and Z`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_C, "genesis")
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R1,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_Z, "genesis")
        )
    }

    @Test
    fun `an unbound C and Z reach Saturn C and Z`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R1,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_C, "saturn")
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_L1,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_Z, "saturn")
        )
    }

    @Test
    fun `C and Z reach their target with no mapping resolved at all`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            ControllerInputMapper.resolveMappedKey(null, KeyEvent.KEYCODE_BUTTON_C, "genesis")
        )
    }

    @Test
    fun `a user's own C binding wins over the platform target`() {
        val withC = gbaWithoutTurboR + (InputSource.Button(KeyEvent.KEYCODE_BUTTON_C) to RetroButton.B)
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_B,
            ControllerInputMapper.resolveMappedKey(withC, KeyEvent.KEYCODE_BUTTON_C, "genesis")
        )
    }

    @Test
    fun `C and Z stay silent on a platform with no C or Z button`() {
        assertEquals(
            KeyEvent.KEYCODE_UNKNOWN,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_BUTTON_Z, "snes")
        )
    }

    @Test
    fun `an unbindable keycode keeps its default route when the mapping omits it`() {
        assertEquals(
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            ControllerInputMapper.resolveMappedKey(gbaWithoutTurboR, KeyEvent.KEYCODE_DPAD_UP_LEFT)
        )
    }

    @Test
    fun `absent mapping falls back to the raw keycode`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R2,
            ControllerInputMapper.resolveMappedKey(null, KeyEvent.KEYCODE_BUTTON_R2)
        )
    }

    @Test
    fun `empty mapping falls back to the raw keycode`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_R2,
            ControllerInputMapper.resolveMappedKey(emptyMap(), KeyEvent.KEYCODE_BUTTON_R2)
        )
    }
}
