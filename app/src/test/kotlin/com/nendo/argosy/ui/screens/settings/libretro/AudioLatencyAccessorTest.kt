package com.nendo.argosy.ui.screens.settings.libretro

import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLatencyAccessorTest {

    private val setting = LibretroSettingDef.AudioLatency

    private fun platformAccessor(
        overrideMs: Int?,
        globalToken: String = "Auto",
        onUpdate: (LibretroSettingDef, String?) -> Unit = { _, _ -> }
    ) = PlatformLibretroSettingsAccessor(
        platformSettings = PlatformLibretroSettingsEntity(platformId = 1, audioLatencyMs = overrideMs),
        globalState = BuiltinVideoState(audioLatency = globalToken),
        onUpdate = onUpdate
    )

    @Test
    fun `a per-platform override reads as its token and falls back to the global value`() {
        assertEquals("96ms", platformAccessor(overrideMs = 96).getValue(setting))
        assertTrue(platformAccessor(overrideMs = 96).hasOverride(setting))
        assertEquals("64ms", platformAccessor(overrideMs = null, globalToken = "64ms").getValue(setting))
        assertFalse(platformAccessor(overrideMs = null).hasOverride(setting))
    }

    @Test
    fun `per-platform stepping clamps at both ends`() {
        var written: String? = null
        platformAccessor(overrideMs = 256) { _, v -> written = v }.cycle(setting, 1)
        assertEquals("256ms", written)

        platformAccessor(overrideMs = 0) { _, v -> written = v }.cycle(setting, -1)
        assertEquals("Auto", written)

        platformAccessor(overrideMs = 0) { _, v -> written = v }.cycle(setting, 1)
        assertEquals("32ms", written)
    }

    @Test
    fun `the ends of the range report a boundary in the stepping direction only`() {
        assertTrue(platformAccessor(overrideMs = 256).isAtSegmentEnd(setting, 1))
        assertFalse(platformAccessor(overrideMs = 256).isAtSegmentEnd(setting, -1))
        assertTrue(platformAccessor(overrideMs = 0).isAtSegmentEnd(setting, -1))
        assertFalse(platformAccessor(overrideMs = 0).isAtSegmentEnd(setting, 1))
    }
}
