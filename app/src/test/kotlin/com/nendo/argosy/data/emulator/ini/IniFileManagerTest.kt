package com.nendo.argosy.data.emulator.ini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IniFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val iniFileManager = IniFileManager()

    @Test
    fun `get reads key from target section with case-insensitivity`() {
        val content = """
            |[EmuCore/GS]
            |LoadTextureReplacements = true
            |VsyncEnable = 1
        """.trimMargin()

        assertEquals("true", iniFileManager.get(content, "EmuCore/GS", "LoadTextureReplacements"))
        assertEquals("true", iniFileManager.get(content, "emucore/gs", "loadtexturereplacements"))
        assertEquals("1", iniFileManager.get(content, "EmuCore/GS", "vsyncenable"))
        assertNull(iniFileManager.get(content, "EmuCore/GS", "NonExistentKey"))
        assertNull(iniFileManager.get(content, "MissingSection", "LoadTextureReplacements"))
    }

    @Test
    fun `get tolerates section header comments and whitespace`() {
        val content = """
            |[EmuCore] ; Core settings
            |EnableCheats = true
            |
            |  [EmuCore/GS]   # Graphics setup
            |LoadTextureReplacements = true
            |
            |[Sound] ; Audio config
            |Volume = 100
        """.trimMargin()

        assertEquals("true", iniFileManager.get(content, "EmuCore/GS", "LoadTextureReplacements"))
        assertEquals("true", iniFileManager.get(content, "EmuCore", "EnableCheats"))
        assertEquals("100", iniFileManager.get(content, "Sound", "Volume"))
    }

    @Test
    fun `get strips inline comments from value while preserving quoted strings`() {
        val content = """
            |[Settings]
            |LoadTextureReplacements = false ; Disabled to prevent stutter
            |PrecacheTextures = false # precache disabled
            |GameTitle = "Gran Turismo; Part 1 # Special Edition"
            |NormalKey = normal_val
        """.trimMargin()

        assertEquals("false", iniFileManager.get(content, "Settings", "LoadTextureReplacements"))
        assertEquals("false", iniFileManager.get(content, "Settings", "PrecacheTextures"))
        assertEquals("Gran Turismo; Part 1 # Special Edition", iniFileManager.get(content, "Settings", "GameTitle"))
        assertEquals("normal_val", iniFileManager.get(content, "Settings", "NormalKey"))
    }

    @Test
    fun `section boundary safety prevents bleed into adjacent sections`() {
        val content = """
            |[EmuCore/GS] ; Graphics settings
            |LoadTextureReplacements = true
            |
            |[Sound] ; Audio settings
            |Volume = 80
            |LoadTextureReplacements = false ; Sound section key with same name
            |
            |[Display] # Display settings
            |Fullscreen = 1
        """.trimMargin()

        val gsSection = iniFileManager.getSection(content, "EmuCore/GS")
        assertNotNull(gsSection)
        assertEquals(1, gsSection!!.size)
        assertEquals("true", gsSection["LoadTextureReplacements"])
        assertNull(gsSection["Volume"])

        val soundSection = iniFileManager.getSection(content, "Sound")
        assertNotNull(soundSection)
        assertEquals(2, soundSection!!.size)
        assertEquals("80", soundSection["Volume"])
        assertEquals("false", soundSection["LoadTextureReplacements"])

        val displaySection = iniFileManager.getSection(content, "Display")
        assertNotNull(displaySection)
        assertEquals("1", displaySection!!["Fullscreen"])
    }

    @Test
    fun `updateSetting updates existing value and preserves inline comments and indentation`() {
        val original = """
            |[EmuCore/GS]
            |    LoadTextureReplacements = false ; disabled for performance
            |    VsyncEnable = 1
        """.trimMargin()

        val updated = iniFileManager.updateSetting(
            content = original,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        assertTrue(updated.contains("    LoadTextureReplacements = true ; disabled for performance"))
        assertTrue(updated.contains("    VsyncEnable = 1"))
        assertFalse(updated.contains("= false"))
    }

    @Test
    fun `updateSetting preserves CRLF line endings`() {
        val original = "[EmuCore/GS]\r\nLoadTextureReplacements = false\r\n"

        val updated = iniFileManager.updateSetting(
            content = original,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        assertTrue("Must contain CRLF", updated.contains("\r\n"))
        assertFalse("Must not have bare LF without CR", updated.replace("\r\n", "").contains("\n"))
        assertEquals("[EmuCore/GS]\r\nLoadTextureReplacements = true\r\n", updated)
    }

    @Test
    fun `updateSetting deduplicates duplicate keys in same section`() {
        val original = """
            |[EmuCore/GS]
            |LoadTextureReplacements = false
            |VsyncEnable = 1
            |LoadTextureReplacements = false ; duplicate entry
        """.trimMargin()

        val updated = iniFileManager.updateSetting(
            content = original,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        val count = Regex("""LoadTextureReplacements\s*=""").findAll(updated).count()
        assertEquals("Duplicate keys must be consolidated to 1", 1, count)
        assertTrue(updated.contains("LoadTextureReplacements = true"))
    }

    @Test
    fun `updateSetting inserts key at end of section when key is missing`() {
        val original = """
            |[EmuCore/GS] ; Graphics settings
            |; Descriptive header comment
            |VsyncEnable = 1
            |
            |[Sound]
            |Volume = 100
        """.trimMargin()

        val updated = iniFileManager.updateSetting(
            content = original,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        // Ensure it did not insert right after section header before comments
        val lines = updated.lines()
        val gsHeaderIndex = lines.indexOfFirst { it.startsWith("[EmuCore/GS]") }
        val commentIndex = lines.indexOfFirst { it.contains("Descriptive header comment") }
        val keyIndex = lines.indexOfFirst { it.startsWith("LoadTextureReplacements") }
        val soundHeaderIndex = lines.indexOfFirst { it.startsWith("[Sound]") }

        assertTrue(commentIndex > gsHeaderIndex)
        assertTrue(keyIndex > commentIndex)
        assertTrue(keyIndex < soundHeaderIndex)
    }

    @Test
    fun `updateSetting appends new section when section is missing`() {
        val original = """
            |[EmuCore]
            |EnableCheats = true
        """.trimMargin()

        val updated = iniFileManager.updateSetting(
            content = original,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        assertTrue(updated.contains("[EmuCore]"))
        assertTrue(updated.contains("EnableCheats = true"))
        assertTrue(updated.contains("[EmuCore/GS]"))
        assertTrue(updated.contains("LoadTextureReplacements = true"))
    }

    @Test
    fun `updateSetting handles empty content`() {
        val updated = iniFileManager.updateSetting(
            content = "",
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )

        val expected = "[EmuCore/GS]\nLoadTextureReplacements = true\n"
        assertEquals(expected, updated)
    }

    @Test
    fun `writeSetting and writeAtomically safely persist UTF-8 content to disk`() {
        val file = File(tempFolder.newFolder("inis"), "test.ini")

        val initialSuccess = iniFileManager.writeSetting(
            file = file,
            section = "EmuCore/GS",
            key = "LoadTextureReplacements",
            value = "true"
        )
        assertTrue(initialSuccess)
        assertTrue(file.exists())

        assertEquals("true", iniFileManager.get(file, "EmuCore/GS", "LoadTextureReplacements"))

        // Update with non-ASCII UTF-8 characters
        val updateSuccess = iniFileManager.writeSetting(
            file = file,
            section = "GameMetadata",
            key = "Title",
            value = "グランツーリスモ"
        )
        assertTrue(updateSuccess)

        assertEquals("グランツーリスモ", iniFileManager.get(file, "GameMetadata", "Title"))
        assertEquals("true", iniFileManager.get(file, "EmuCore/GS", "LoadTextureReplacements"))
    }

    @Test
    fun `writeSettings updates multiple keys atomically`() {
        val file = File(tempFolder.root, "armsx2.ini")

        val success = iniFileManager.writeSettings(
            file = file,
            section = "EmuCore/GS",
            entries = mapOf(
                "LoadTextureReplacements" to "true",
                "PrecacheTextureReplacements" to "true",
                "VsyncEnable" to "1"
            )
        )
        assertTrue(success)

        val section = iniFileManager.getSection(file, "EmuCore/GS")
        assertNotNull(section)
        assertEquals("true", section!!["LoadTextureReplacements"])
        assertEquals("true", section["PrecacheTextureReplacements"])
        assertEquals("1", section["VsyncEnable"])
    }
}
