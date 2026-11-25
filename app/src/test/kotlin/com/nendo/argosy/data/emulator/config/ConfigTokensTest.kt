package com.nendo.argosy.data.emulator.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTokensTest {

    private fun EmulatorConfigSpec.option(section: String, key: String): ConfigOption =
        options.single { it.section == section && it.key == key }

    private val dataRoot = "/storage/emulated/0/Android/data/info.cemu.cemu/files"

    @Test
    fun `enum tokens come only from the registered values`() {
        val option = EdenConfigSpec.SPEC.option("Renderer", "resolution_setup")

        assertEquals("12", ConfigTokens.resolve(option, "12", "3", null))
        assertNull(ConfigTokens.resolve(option, "9", "3", null))
        assertNull(ConfigTokens.resolve(option, "x", "3", null))
    }

    @Test
    fun `booleans follow the spelling the file already uses`() {
        val dolphin = DolphinConfigSpec.SPEC.option("Core", "CPUThread")
        val duck = DuckStationConfigSpec.SPEC.option("Display", "VSync")

        assertEquals("False", ConfigTokens.resolve(dolphin, "false", "True", null))
        assertEquals("False", ConfigTokens.resolve(dolphin, "false", null, null))
        assertEquals("0", ConfigTokens.resolve(dolphin, "false", "1", null))
        assertEquals("true", ConfigTokens.resolve(duck, "True", "false", null))
        assertNull(ConfigTokens.resolve(duck, "maybe", "false", null))
    }

    @Test
    fun `numbers must sit inside the range and floats keep their decimal style`() {
        val volume = EdenConfigSpec.SPEC.option("Audio", "volume")
        val multiplier = Vita3kConfigSpec.SPEC.option("", "resolution-multiplier")
        val speed = DolphinConfigSpec.SPEC.option("Core", "EmulationSpeed")

        assertEquals("150", ConfigTokens.resolve(volume, "150", "100", null))
        assertNull(ConfigTokens.resolve(volume, "201", "100", null))
        assertNull(ConfigTokens.resolve(volume, "1.5", "100", null))
        assertEquals("3", ConfigTokens.resolve(multiplier, "3.0", "2", null))
        assertEquals("2.25", ConfigTokens.resolve(multiplier, "2.25", "2", null))
        assertEquals("1.0", ConfigTokens.resolve(speed, "1", "1.5", null))
        assertNull(ConfigTokens.resolve(multiplier, "8.5", "2", null))
    }

    @Test
    fun `a number without a range and a non-editable option are refused`() {
        val noRange = EdenConfigSpec.SPEC.option("Core", "speed_limit").copy(range = null)
        val notEditable = EdenConfigSpec.SPEC.option("Renderer", "backend")

        assertFalse(ConfigTokens.isAdjustable(noRange))
        assertNull(ConfigTokens.resolve(noRange, "100", "100", null))
        assertNull(ConfigTokens.resolve(notEditable, "0", "1", null))
    }

    @Test
    fun `driver paths must point inside the driver folder`() {
        val option = CemuConfigSpec.CUSTOM_DRIVER_PATH

        assertEquals("", ConfigTokens.resolve(option, "", null, dataRoot))
        assertEquals("$dataRoot/customDrivers/abc", ConfigTokens.resolve(option, "$dataRoot/customDrivers/abc", null, dataRoot))
        assertNull(ConfigTokens.resolve(option, "$dataRoot/customDrivers/../settings.xml", null, dataRoot))
        assertNull(ConfigTokens.resolve(option, "$dataRoot/mlc01", null, dataRoot))
        assertNull(ConfigTokens.resolve(option, "$dataRoot/customDrivers/abc", null, null))
    }

    @Test
    fun `cycling and stepping stop at either end`() {
        val aa = EdenConfigSpec.SPEC.option("Renderer", "anti_aliasing")
        val multiplier = Vita3kConfigSpec.SPEC.option("", "resolution-multiplier")

        assertEquals("1", ConfigTokens.cycled(aa, "0", 1))
        assertNull(ConfigTokens.cycled(aa, "2", 1))
        assertNull(ConfigTokens.cycled(aa, "0", -1))
        assertEquals("0", ConfigTokens.cycled(aa, "7", 1))
        assertEquals("2.25", ConfigTokens.stepped(multiplier, "2", 1))
        assertEquals("1.75", ConfigTokens.stepped(multiplier, "2", -1))
        assertNull(ConfigTokens.stepped(multiplier, "8", 1))
        assertNull(ConfigTokens.stepped(multiplier, "0.5", -1))
    }

    @Test
    fun `toggling flips any boolean spelling`() {
        assertEquals("false", ConfigTokens.toggled("True"))
        assertEquals("true", ConfigTokens.toggled("0"))
        assertEquals("true", ConfigTokens.toggled(null))
        assertTrue(ConfigTokens.isOn("1"))
    }
}
