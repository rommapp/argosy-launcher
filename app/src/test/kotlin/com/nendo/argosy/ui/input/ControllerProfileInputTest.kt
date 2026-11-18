package com.nendo.argosy.ui.input

import android.view.KeyEvent
import com.nendo.argosy.core.input.ActivePad
import com.nendo.argosy.core.input.ControllerProfile
import com.nendo.argosy.core.input.ControllerProfiles
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerProfileInputTest {

    private val nintendoPadId = "11720:12290:nintendo-pad"
    private val xboxPadId = "11720:24577:xbox-pad"

    private val prefs = UserPreferences(
        controllerProfiles = ControllerProfiles(
            defaults = ControllerProfile(controllerLayout = "auto"),
            saved = mapOf(xboxPadId to ControllerProfile(controllerLayout = "xbox", swapAB = true))
        )
    )

    @Test
    fun `glyphs follow the lettering of the pad that last sent input`() {
        val nintendoGlyphs = prefs.buttonGlyphSwaps(ActivePad(nintendoPadId, DetectedLayout.NINTENDO))
        val xboxGlyphs = prefs.buttonGlyphSwaps(ActivePad(xboxPadId, DetectedLayout.NINTENDO))

        assertEquals(ButtonGlyphSwaps(ab = true, xy = true, startSelect = false), nintendoGlyphs)
        assertEquals(ButtonGlyphSwaps(ab = true, xy = false, startSelect = false), xboxGlyphs)
    }

    @Test
    fun `before any pad sends input glyphs use the defaults`() {
        val glyphs = prefs.buttonGlyphSwaps(ActivePad(controllerId = null, layout = DetectedLayout.NINTENDO))

        assertEquals(ButtonGlyphSwaps(ab = true, xy = true, startSelect = false), glyphs)
    }

    @Test
    fun `each press reads through the swaps of the pad it came from`() {
        val profiles = prefs.controllerProfiles

        assertEquals(GamepadEvent.Confirm, profiles.forController(nintendoPadId).gamepadEventFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GamepadEvent.Back, profiles.forController(nintendoPadId).gamepadEventFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(GamepadEvent.Back, profiles.forController(xboxPadId).gamepadEventFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GamepadEvent.Confirm, profiles.forController(xboxPadId).gamepadEventFor(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun `the controller layout alone never changes dispatch`() {
        val lettering = ControllerProfile(controllerLayout = "nintendo")

        assertEquals(GamepadEvent.Confirm, lettering.gamepadEventFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GamepadEvent.ContextMenu, lettering.gamepadEventFor(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(GamepadEvent.Menu, lettering.gamepadEventFor(KeyEvent.KEYCODE_BUTTON_START))
    }
}
