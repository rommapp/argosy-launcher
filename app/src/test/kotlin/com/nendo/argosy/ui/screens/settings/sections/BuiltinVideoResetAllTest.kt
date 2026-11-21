package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.PlatformContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinVideoResetAllTest {

    private val wii = PlatformContext(9, "Wii", "wii")
    private val onWii = BuiltinVideoState(availablePlatforms = listOf(wii), platformContextIndex = 1)

    @Test
    fun `a platform with only saving overrides offers no video reset`() {
        val settings = mapOf(9L to PlatformLibretroSettingsEntity(platformId = 9, autoSaveState = true))

        assertFalse(builtinVideoShowsResetAll(onWii, settings))
        assertEquals(builtinVideoMaxFocusIndex(onWii, emptyMap()), builtinVideoMaxFocusIndex(onWii, settings))
    }

    @Test
    fun `a platform with a video override offers the reset as one extra row`() {
        val settings = mapOf(9L to PlatformLibretroSettingsEntity(platformId = 9, shader = "crt"))

        assertTrue(builtinVideoShowsResetAll(onWii, settings))
        assertEquals(builtinVideoMaxFocusIndex(onWii, emptyMap()) + 1, builtinVideoMaxFocusIndex(onWii, settings))
    }

    @Test
    fun `an audio latency override alone offers the video reset`() {
        val settings = mapOf(9L to PlatformLibretroSettingsEntity(platformId = 9, audioLatencyMs = 64))

        assertTrue(builtinVideoShowsResetAll(onWii, settings))
    }

    @Test
    fun `the global context never offers the reset`() {
        val global = onWii.copy(platformContextIndex = 0)
        val settings = mapOf(9L to PlatformLibretroSettingsEntity(platformId = 9, shader = "crt"))

        assertFalse(builtinVideoShowsResetAll(global, settings))
    }
}
