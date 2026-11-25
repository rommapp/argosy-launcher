package com.nendo.argosy.data.emulator.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigOptionDecoderTest {

    private fun option(spec: EmulatorConfigSpec, section: String, key: String): ConfigOption =
        spec.options.single { it.section == section && it.key == key }

    private fun decode(spec: EmulatorConfigSpec, section: String, key: String, entries: List<ConfigEntry>?) =
        ConfigOptionDecoder.decode(spec, option(spec, section, key), entries)

    private val eden = EdenConfigSpec.SPEC
    private val edenEntries = ConfigFileReaders.ini(ConfigFixtures.text("config.ini"))

    @Test
    fun `eden value with default flag false decodes and compares against the default`() {
        val filter = decode(eden, "Renderer", "scaling_filter", edenEntries)

        assertEquals("6", filter.raw)
        assertEquals("FSR", filter.valueName)
        assertFalse(filter.isDefault)
    }

    @Test
    fun `eden default flag true is the compiled default whatever the stored value`() {
        val nvdec = decode(eden, "Renderer", "nvdec_emulation", edenEntries)

        assertEquals("2", nvdec.raw)
        assertEquals("Gpu", nvdec.valueName)
        assertTrue(nvdec.isDefault)
    }

    @Test
    fun `eden missing default flag is the compiled default`() {
        val entries = ConfigFileReaders.ini("[Renderer]\nanti_aliasing=2\n")

        assertTrue(decode(eden, "Renderer", "anti_aliasing", entries).isDefault)
    }

    @Test
    fun `eden stored value equal to the default with the flag false is the default`() {
        val airplane = decode(eden, "Services", "airplane_mode", edenEntries)

        assertEquals("true", airplane.raw)
        assertTrue(airplane.isDefault)
    }

    @Test
    fun `eden stored value off the default with the flag false is not the default`() {
        val docked = decode(eden, "System", "use_docked_mode", edenEntries)

        assertEquals("Docked", docked.valueName)
        assertFalse(docked.isDefault)
    }

    @Test
    fun `an unknown enum token stays raw and is never the default`() {
        val entries = ConfigFileReaders.ini("[Renderer]\nresolution_setup\\default=true\nresolution_setup=10\n")

        val resolution = decode(eden, "Renderer", "resolution_setup", entries)

        assertEquals("10", resolution.raw)
        assertNull(resolution.valueName)
        assertFalse(resolution.isDefault)
    }

    @Test
    fun `duckstation old bool encoding of an enum is unknown`() {
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("settings.ini"))

        val depth = decode(DuckStationConfigSpec.SPEC, "GPU", "PGXPNormalizeDepth", entries)

        assertEquals("false", depth.raw)
        assertNull(depth.valueName)
        assertFalse(depth.isDefault)
    }

    @Test
    fun `duckstation enum tokens are case sensitive`() {
        val entries = ConfigFileReaders.ini("[GPU]\nRenderer = vulkan\n")

        val renderer = decode(DuckStationConfigSpec.SPEC, "GPU", "Renderer", entries)

        assertNull(renderer.valueName)
        assertFalse(renderer.isDefault)
    }

    @Test
    fun `an absent key is the default and names the default value`() {
        val renderer = decode(DuckStationConfigSpec.SPEC, "GPU", "Renderer", emptyList())

        assertNull(renderer.raw)
        assertEquals("Automatic", renderer.valueName)
        assertTrue(renderer.isDefault)
    }

    @Test
    fun `unknown file content is neither stored nor default`() {
        val renderer = decode(DuckStationConfigSpec.SPEC, "GPU", "Renderer", null)

        assertNull(renderer.raw)
        assertNull(renderer.valueName)
        assertFalse(renderer.isDefault)
        assertFalse(renderer.hidden)
    }

    @Test
    fun `dolphin matches sections and keys without case and bools across spellings`() {
        val dolphin = DolphinConfigSpec.SPEC

        val off = decode(dolphin, "Hardware", "VSync", ConfigFileReaders.ini("[hardware]\nvsync = 0\n"))
        val on = decode(dolphin, "Hardware", "VSync", ConfigFileReaders.ini("[HARDWARE]\nVSYNC = True\n"))

        assertEquals("0", off.raw)
        assertTrue(off.isDefault)
        assertEquals("True", on.raw)
        assertFalse(on.isDefault)
    }

    @Test
    fun `floats compare by value`() {
        val entries = requireNotNull(ConfigFileReaders.json(ConfigFixtures.text("armsx2-settings.json")))
            .filter { it.section == "global" }
            .map { it.copy(section = "config.global") }

        val upscale = decode(Armsx2ConfigSpec.SPEC, "config.global", "upscaleFloat", entries)

        assertEquals("1", upscale.raw)
        assertTrue(upscale.isDefault)
    }

    @Test
    fun `cemu upscale filter compares against the load default`() {
        val entries = requireNotNull(ConfigFileReaders.xml(ConfigFixtures.text("settings.xml")))

        val upscale = decode(CemuConfigSpec.SPEC, "Graphic", "UpscaleFilter", entries)

        assertEquals("bicubic", upscale.valueName)
        assertFalse(upscale.isDefault)
    }

    @Test
    fun `vita3k string enum decodes its own token`() {
        val entries = ConfigFileReaders.yaml(ConfigFixtures.text("config.yml"))

        val filter = decode(Vita3kConfigSpec.SPEC, "", "screen-filter", entries)
        val scale = decode(Vita3kConfigSpec.SPEC, "", "resolution-multiplier", entries)

        assertEquals("FXAA", filter.valueName)
        assertFalse(filter.isDefault)
        assertEquals("2", scale.raw)
        assertFalse(scale.isDefault)
    }

    @Test
    fun `a secret option is hidden`() {
        val spec = EmulatorConfigSpec(
            files = listOf(DuckStationConfigSpec.SETTINGS),
            options = listOf(ConfigOption(DuckStationConfigSpec.SETTINGS, "Cheevos", "Token", ConfigValueType.STRING, "")),
            secrets = DuckStationConfigSpec.SPEC.secrets
        )
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("settings.ini"))

        val token = ConfigOptionDecoder.decode(spec, spec.options.single(), entries)

        assertTrue(token.hidden)
        assertNull(token.raw)
        assertFalse(token.isDefault)
    }

    @Test
    fun `same compares each type by its own rule`() {
        assertTrue(ConfigOptionDecoder.same(ConfigValueType.BOOL, "TRUE", "1"))
        assertFalse(ConfigOptionDecoder.same(ConfigValueType.BOOL, "true", "false"))
        assertTrue(ConfigOptionDecoder.same(ConfigValueType.INT, "007", "7"))
        assertTrue(ConfigOptionDecoder.same(ConfigValueType.FLOAT, "1", "1.0"))
        assertTrue(ConfigOptionDecoder.same(ConfigValueType.ENUM, "-1", "-1"))
        assertFalse(ConfigOptionDecoder.same(ConfigValueType.ENUM, "Vulkan", "vulkan"))
        assertFalse(ConfigOptionDecoder.same(ConfigValueType.STRING, "a", "A"))
    }
}
