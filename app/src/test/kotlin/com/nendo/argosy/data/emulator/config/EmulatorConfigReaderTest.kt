package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EmulatorConfigReaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val fal: FileAccessLayer = realFsFal()
    private val reader = EmulatorConfigReader(mockk(relaxed = true), mockk(relaxed = true), fal)

    private val dataRoot: File by lazy {
        File(temp.root, "Android/data/pkg/files").apply { mkdirs() }
    }

    private fun place(relative: String, fixture: String): File =
        File(dataRoot, relative).apply {
            parentFile?.mkdirs()
            writeText(ConfigFixtures.text(fixture))
        }

    private fun capabilities(root: String? = dataRoot.absolutePath) = EmulatorDataCapabilities(
        dataRoot = root,
        readRoute = EmulatorDataRoute.FILE_ACCESS,
        writeAppDataRoute = null,
        editRoute = null
    )

    private fun EmulatorConfigSnapshot.file(spec: ConfigFileSpec) = files.single { it.file == spec }

    private fun EmulatorConfigSnapshot.option(section: String, key: String) =
        options.single { it.option.section == section && it.option.key == key }

    private fun EmulatorConfigSnapshot.other(section: String, key: String) =
        otherKeys.single { it.section == section && it.key == key }

    @Test
    fun `dolphin reads its files, decodes play time and treats absent files as defaults`() {
        place("Config/Dolphin.ini", "Dolphin.ini")
        place("Config/TimePlayed.ini", "TimePlayed.ini")

        val snapshot = reader.read(DolphinConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.READ, snapshot.file(DolphinConfigSpec.MAIN).state)
        assertTrue(snapshot.file(DolphinConfigSpec.MAIN).modifiedAt != null)
        assertEquals(ConfigFileState.ABSENT, snapshot.file(DolphinConfigSpec.GFX).state)
        assertNull(snapshot.file(DolphinConfigSpec.GFX).modifiedAt)
        assertEquals(mapOf("GFZE01" to 180_024L, "RMGE01" to 90_008L), snapshot.playTimes)
        assertTrue(snapshot.option("Hardware", "VSync").isDefault)
        assertTrue(snapshot.option("Core", "CPUThread").isDefault)
        assertTrue(snapshot.otherKeys.none { it.file == DolphinConfigSpec.TIME_PLAYED })
    }

    @Test
    fun `dolphin secrets are hidden among the other keys`() {
        place("Config/Dolphin.ini", "Dolphin.ini")

        val snapshot = reader.read(DolphinConfigSpec.SPEC, capabilities())

        val id = snapshot.other("Analytics", "ID")
        val mac = snapshot.other("General", "WirelessMac")
        assertTrue(id.hidden)
        assertNull(id.value)
        assertTrue(mac.hidden)
        assertNull(mac.value)
        assertEquals("True", snapshot.other("DSP", "DSPThread").value)
        assertFalse(snapshot.other("DSP", "DSPThread").hidden)
    }

    @Test
    fun `eden other keys drop default companions and hide web service fields`() {
        place("config/config.ini", "config.ini")

        val snapshot = reader.read(EdenConfigSpec.SPEC, capabilities())

        assertTrue(snapshot.otherKeys.none { it.key.endsWith("\\default") })
        assertTrue(snapshot.otherKeys.none { it.section == "Renderer" && it.key == "scaling_filter" })
        listOf("eden_token", "web_api_url", "eden_username").forEach { key ->
            val entry = snapshot.other("WebService", key)
            assertTrue(key, entry.hidden)
            assertNull(key, entry.value)
        }
        assertEquals("6543", snapshot.other("Debugging", "gdbstub_port").value)
        assertEquals("FSR", snapshot.option("Renderer", "scaling_filter").valueName)
    }

    @Test
    fun `cemu account entries are hidden`() {
        place("settings.xml", "settings.xml")

        val snapshot = reader.read(CemuConfigSpec.SPEC, capabilities())

        val account = snapshot.otherKeys.filter { it.section == "Account" }
        assertEquals(3, account.size)
        assertTrue(account.all { it.hidden && it.value == null })
        assertEquals("bicubic", snapshot.option("Graphic", "UpscaleFilter").valueName)
    }

    @Test
    fun `duckstation token is hidden`() {
        place("settings.ini", "settings.ini")

        val snapshot = reader.read(DuckStationConfigSpec.SPEC, capabilities())

        assertTrue(snapshot.other("Cheevos", "Token").hidden)
        assertNull(snapshot.other("Cheevos", "Token").value)
        assertEquals("Automatic", snapshot.option("GPU", "Renderer").valueName)
    }

    @Test
    fun `armsx2 without root reads its options from the json mirror`() {
        place("PCSX2-Android.ini", "PCSX2-Android.ini")
        place("armsx2-settings.json", "armsx2-settings.json")

        val snapshot = reader.read(Armsx2ConfigSpec.SPEC, capabilities(), "com.armsx2")

        val ini = snapshot.file(Armsx2ConfigSpec.INI)
        assertEquals(ConfigFileState.READ, ini.state)
        assertEquals(File(dataRoot, "PCSX2-Android.ini").absolutePath, ini.path)
        assertEquals("Auto", snapshot.option("EmuCore/GS", "Renderer").valueName)
        assertEquals(ConfigFileState.BLOCKED, snapshot.file(Armsx2ConfigSpec.PREFS).state)
        assertEquals("/data/data/com.armsx2/shared_prefs/ARMSX2.xml", snapshot.file(Armsx2ConfigSpec.PREFS).path)
        assertEquals("auto", snapshot.option("config.global", "renderer").raw)
        assertTrue(snapshot.option("config.global", "renderer").isDefault)
        assertTrue(snapshot.otherKeys.none { it.section == "global" && it.key == "renderer" })
    }

    @Test
    fun `armsx2 with root reads its options from shared preferences`() {
        place("armsx2-settings.json", "armsx2-settings.json")
        val prefs = ConfigFixtures.text("ARMSX2.xml").toByteArray()
        val paths = mutableListOf<String>()

        val snapshot = reader.read(Armsx2ConfigSpec.SPEC, capabilities(), "com.armsx2") { path ->
            paths += path
            ConfigRead.Found(prefs)
        }

        assertEquals(listOf("/data/data/com.armsx2/shared_prefs/ARMSX2.xml"), paths)
        assertEquals(ConfigFileState.READ, snapshot.file(Armsx2ConfigSpec.PREFS).state)
        assertEquals("vulkan", snapshot.option("config.global", "renderer").raw)
        assertFalse(snapshot.option("config.global", "renderer").isDefault)
    }

    @Test
    fun `vita3k lists unknown top level keys raw`() {
        place("config.yml", "config.yml")

        val snapshot = reader.read(Vita3kConfigSpec.SPEC, capabilities())

        assertEquals("[1, 0, 3]", snapshot.other("", "controller-binds").value)
        assertEquals("true", snapshot.other("", "psn-signed-in").value)
        assertEquals("FXAA", snapshot.option("", "screen-filter").valueName)
    }

    @Test
    fun `an unreadable file is blocked, not absent`() {
        val config = place("config/config.ini", "config.ini")
        every { fal.canRead(config.absolutePath) } returns false

        val snapshot = reader.read(EdenConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.BLOCKED, snapshot.file(EdenConfigSpec.CONFIG).state)
        val backend = snapshot.option("Renderer", "backend")
        assertNull(backend.raw)
        assertFalse(backend.isDefault)
        assertTrue(snapshot.otherKeys.isEmpty())
    }

    @Test
    fun `a file in a folder that refuses listing is blocked, not absent`() {
        val folder = File(dataRoot, "Config").apply { mkdirs() }
        every { fal.isListable(folder.absolutePath) } returns false

        val snapshot = reader.read(DolphinConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.BLOCKED, snapshot.file(DolphinConfigSpec.MAIN).state)
        assertFalse(snapshot.option("Hardware", "VSync").isDefault)
    }

    @Test
    fun `a missing folder under a listable data root is absent`() {
        val snapshot = reader.read(DolphinConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.ABSENT, snapshot.file(DolphinConfigSpec.MAIN).state)
        assertTrue(snapshot.option("Hardware", "VSync").isDefault)
    }

    @Test
    fun `a data root that refuses listing makes every file blocked`() {
        every { fal.isListable(any()) } returns false

        val snapshot = reader.read(Vita3kConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.BLOCKED, snapshot.file(Vita3kConfigSpec.CONFIG).state)
    }

    @Test
    fun `no data root makes every file blocked`() {
        val snapshot = reader.read(DolphinConfigSpec.SPEC, capabilities(root = null))

        assertTrue(snapshot.files.all { it.state == ConfigFileState.BLOCKED })
        assertTrue(snapshot.options.none { it.isDefault })
    }

    @Test
    fun `a malformed file reads but its options are unknown`() {
        File(dataRoot, "settings.xml").writeText("<content><Graphic>")

        val snapshot = reader.read(CemuConfigSpec.SPEC, capabilities())

        assertEquals(ConfigFileState.READ, snapshot.file(CemuConfigSpec.SETTINGS).state)
        assertTrue(snapshot.options.none { it.isDefault || it.raw != null })
    }
}
