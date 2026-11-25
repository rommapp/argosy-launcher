package com.nendo.argosy.data.emulator.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFileReadersTest {

    private fun List<ConfigEntry>.value(section: String, key: String): String? =
        lastOrNull { it.section == section && it.key == key }?.value

    @Test
    fun `eden ini keeps backslash keys and their default companions`() {
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("config.ini"))

        assertEquals("6", entries.value("Renderer", "scaling_filter"))
        assertEquals("false", entries.value("Renderer", "scaling_filter\\default"))
        assertEquals("min_x:100,min_y:50,max_x:1800,max_y:850", entries.value("Controls", "touch_device"))
        assertEquals("/storage/546B-6466/Games/switch", entries.value("UI", "Paths\\external_content_dirs\\1\\path"))
        assertEquals("", entries.value("Data%20Storage", "save_directory"))
    }

    @Test
    fun `dolphin ini drops the space around the equals sign`() {
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("Dolphin.ini"))

        assertEquals("False", entries.value("Analytics", "Enabled"))
        assertEquals("True", entries.value("DSP", "DSPThread"))
    }

    @Test
    fun `duckstation ini reads an empty value and a value with spaces`() {
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("settings.ini"))

        assertEquals("", entries.value("GPU", "Adapter"))
        assertEquals("Auto (Game Native)", entries.value("Display", "AspectRatio"))
    }

    @Test
    fun `ini keeps every copy of a repeated key in file order`() {
        val entries = ConfigFileReaders.ini("[GameList]\nRecursivePaths = /a\nRecursivePaths = /b\n")

        assertEquals(listOf("/a", "/b"), entries.filter { it.key == "RecursivePaths" }.map { it.value })
    }

    @Test
    fun `pcsx2 ini reads slash sections`() {
        val entries = ConfigFileReaders.ini(ConfigFixtures.text("PCSX2-Android.ini"))

        assertEquals("-1", entries.value("EmuCore/GS", "Renderer"))
        assertEquals("1", entries.value("UI", "SettingsVersion"))
    }

    @Test
    fun `cemu xml keys leaves by their parent path`() {
        val entries = requireNotNull(ConfigFileReaders.xml(ConfigFixtures.text("settings.xml")))

        assertEquals("1", entries.value("", "console_language"))
        assertEquals("", entries.value("", "mlc_path"))
        assertEquals("1", entries.value("Graphic", "UpscaleFilter"))
        assertEquals("true", entries.value("Graphic/Overlay", "FPS"))
        assertEquals("12345678", entries.value("Account", "PersistentId"))
        assertEquals("127.0.0.1", entries.value("Input/DSUC", "@host"))
        assertEquals("", entries.value("", "GamePaths"))
    }

    @Test
    fun `malformed xml reads as nothing`() {
        assertNull(ConfigFileReaders.xml("<content><Graphic>"))
    }

    @Test
    fun `vita3k yaml reads top level scalars and flattens block lists`() {
        val entries = ConfigFileReaders.yaml(ConfigFixtures.text("config.yml"))

        assertEquals("FXAA", entries.value("", "screen-filter"))
        assertEquals("2", entries.value("", "resolution-multiplier"))
        assertEquals("", entries.value("", "custom-driver-name"))
        assertEquals("[1, 0, 3]", entries.value("", "controller-binds"))
        assertEquals("[]", entries.value("", "controller-led-color"))
        assertEquals("[4]", entries.value("", "ime-langs"))
        assertTrue(entries.none { it.key == "---" || it.key == "..." || it.key.startsWith("-") })
    }

    @Test
    fun `armsx2 json reads the global object`() {
        val entries = requireNotNull(ConfigFileReaders.json(ConfigFixtures.text("armsx2-settings.json")))

        assertEquals("auto", entries.value("global", "renderer"))
        assertEquals("1", entries.value("global", "upscaleFloat"))
        assertEquals("true", entries.value("global", "mtvu"))
        assertEquals("", entries.value("global", "customDriverId"))
        assertEquals("[]", entries.value("global", "dev9EthHosts"))
    }

    @Test
    fun `malformed json reads as nothing`() {
        assertNull(ConfigFileReaders.json("{\"global\":"))
    }

    @Test
    fun `dolphin time played decodes hex milliseconds per game`() {
        val times = DolphinTimePlayed.decode(ConfigFixtures.text("TimePlayed.ini"))

        assertEquals(mapOf("GFZE01" to 180_024L, "RMGE01" to 90_008L), times)
    }

    @Test
    fun `time played leaves out a value that is not hex`() {
        val times = DolphinTimePlayed.decode("[TimePlayed]\nGOOD01 = 0x10\nBAD001 = soon\n")

        assertEquals(mapOf("GOOD01" to 16L), times)
    }
}
