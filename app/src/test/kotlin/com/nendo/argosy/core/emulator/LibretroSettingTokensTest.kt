package com.nendo.argosy.core.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the stored values of every cycle setting.
 *
 * These strings are not copy. They are written to DataStore and to the
 * `platform_libretro_settings` table, they are parsed back by the video pipeline, and the
 * shader entries are upstream libretrodroid `ShaderConfig` names. Renaming one, translating
 * one, or tidying the casing silently invalidates every row already on disk, and the read
 * path coerces a miss to the first option rather than failing, so nothing would report it.
 *
 * If a change here is deliberate it needs a migration for both stores and for settings
 * backups already exported. Updating this test alone is not that.
 */
class LibretroSettingTokensTest {

    private fun optionsOf(def: LibretroSettingDef): List<String> =
        (def.type as LibretroSettingDef.SettingType.Cycle).options

    @Test
    fun `shader tokens match the upstream names`() {
        assertEquals(
            listOf("None", "Sharp", "CUT", "CUT2", "CUT3", "CRT", "LCD", "Custom"),
            optionsOf(LibretroSettingDef.Shader)
        )
    }

    @Test
    fun `video tokens are unchanged`() {
        assertEquals(listOf("Auto", "Nearest", "Bilinear"), optionsOf(LibretroSettingDef.Filter))
        assertEquals(
            listOf("Core Provided", "4:3", "3:2", "16:9", "Integer", "Stretch"),
            optionsOf(LibretroSettingDef.AspectRatio)
        )
        assertEquals(
            listOf("Auto", "Top", "Center", "Bottom"),
            optionsOf(LibretroSettingDef.PortraitPosition)
        )
        assertEquals(
            listOf("Auto", "0°", "90°", "180°", "270°"),
            optionsOf(LibretroSettingDef.Rotation)
        )
        assertEquals(
            listOf("Off", "4px", "8px", "12px", "16px"),
            optionsOf(LibretroSettingDef.OverscanCrop)
        )
    }

    @Test
    fun `performance tokens keep the suffixes the pipeline parses`() {
        assertEquals(listOf("2x", "4x", "8x"), optionsOf(LibretroSettingDef.FastForwardSpeed))
        assertEquals(listOf("1x", "2x", "4x"), optionsOf(LibretroSettingDef.RewindSpeed))
        assertEquals(
            listOf("5s", "15s", "30s", "60s"),
            optionsOf(LibretroSettingDef.RewindBufferDuration)
        )
        assertTrue(optionsOf(LibretroSettingDef.AudioVolume).all { it.endsWith("%") })
        assertEquals("100%", optionsOf(LibretroSettingDef.AudioVolume)[10])
        assertEquals(
            listOf(
                "Auto", "32ms", "48ms", "64ms", "80ms", "96ms", "112ms", "128ms", "144ms",
                "160ms", "176ms", "192ms", "208ms", "224ms", "240ms", "256ms"
            ),
            optionsOf(LibretroSettingDef.AudioLatency)
        )
    }

    @Test
    fun `audio latency stepping clamps at Auto and 256 ms instead of wrapping`() {
        val cycle = LibretroSettingDef.AudioLatency.type as LibretroSettingDef.SettingType.Cycle
        assertEquals("Auto", cycle.next("Auto", -1))
        assertEquals("32ms", cycle.next("Auto", 1))
        assertEquals("Auto", cycle.next("32ms", -1))
        assertEquals("256ms", cycle.next("240ms", 1))
        assertEquals("256ms", cycle.next("256ms", 1))
        assertEquals("256ms", cycle.next("Auto", 100))
        assertEquals("Auto", cycle.next("256ms", -100))
    }

    @Test
    fun `wrapping cycles still wrap at both ends`() {
        val cycle = LibretroSettingDef.AudioVolume.type as LibretroSettingDef.SettingType.Cycle
        assertEquals("200%", cycle.next("0%", -1))
        assertEquals("0%", cycle.next("200%", 1))
    }

    @Test
    fun `audio latency tokens round-trip through milliseconds`() {
        assertEquals(AudioLatencySteps.AUTO_MS, AudioLatencySteps.latencyMsFor("Auto"))
        assertEquals(128, AudioLatencySteps.latencyMsFor("128ms"))
        assertEquals(null, AudioLatencySteps.latencyMsFor("fast"))
        AudioLatencySteps.VALUES_MS.forEach { ms ->
            assertEquals(ms, AudioLatencySteps.latencyMsFor(AudioLatencySteps.tokenFor(ms)))
        }
    }

    @Test
    fun `audio latency outside the range normalizes to Auto or the nearest lower step`() {
        assertEquals(AudioLatencySteps.AUTO_MS, AudioLatencySteps.normalize(-5))
        assertEquals(AudioLatencySteps.MIN_MS, AudioLatencySteps.normalize(1))
        assertEquals(AudioLatencySteps.MAX_MS, AudioLatencySteps.normalize(1000))
        assertEquals(48, AudioLatencySteps.normalize(50))
        assertEquals("256ms", AudioLatencySteps.tokenFor(9999))
    }

    @Test
    fun `every cycle setting has one label resource per option, in the same order`() {
        LibretroSettingDef.ALL
            .mapNotNull { it.type as? LibretroSettingDef.SettingType.Cycle }
            .forEach { cycle ->
                assertEquals(cycle.options.size, cycle.labels.size)
                cycle.labels.forEach { resId -> assertTrue(resId != 0) }
                cycle.options.forEachIndexed { index, token ->
                    assertEquals(cycle.labels[index], cycle.labelResFor(token))
                }
            }
    }

    @Test
    fun `an unknown stored value resolves to no label resource rather than the first option`() {
        val cycle = LibretroSettingDef.Shader.type as LibretroSettingDef.SettingType.Cycle
        assertEquals(null, cycle.labelResFor("Nuage"))
    }
}
