package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RetroArchConfigParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var parser: RetroArchConfigParser

    @Before
    fun setup() {
        // Tests reach the parser only via the File-based parseFile / parseLines
        // path, so a relaxed FAL mock is enough -- it never gets called.
        parser = RetroArchConfigParser(mockk<FileAccessLayer>(relaxed = true))
    }

    private fun writeCfg(contents: String): File {
        val file = tempFolder.newFile("retroarch.cfg")
        file.writeText(contents)
        return file
    }

    // --- Save paths: non-content-dir mode ---

    @Test
    fun `save path uses savefile_directory when no sort flags set`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = false
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/saves"))
    }

    @Test
    fun `save path appends core name when sort_savefiles_enable is true`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/saves/Snes9x"))
    }

    @Test
    fun `save path appends content dir name when sort_savefiles_by_content_enable is true`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = false
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/saves/Super Nintendo"))
    }

    @Test
    fun `save path appends content dir then core when both sort flags set`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/saves/Super Nintendo/Snes9x"))
    }

    // --- Save paths: content-dir mode (#170) ---

    @Test
    fun `save path uses content directory when savefiles_in_content_dir is true`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = true,
            sortByContentDirectory = false,
            sortByCore = false
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(paths.contains("/sdcard/ROMs/SNES"))
    }

    @Test
    fun `save path content-dir mode with sort_savefiles_enable adds core subdir`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = true,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(
            "expected content-dir + core suffix; got $paths",
            paths.contains("/sdcard/ROMs/SNES/Snes9x")
        )
    }

    @Test
    fun `save path content-dir mode with sort_savefiles_by_content_enable adds content subdir`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = true,
            sortByContentDirectory = true,
            sortByCore = false
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(paths.contains("/sdcard/ROMs/SNES/SNES"))
    }

    @Test
    fun `save path content-dir mode with both sort flags adds both subdirs`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = true,
            sortByContentDirectory = true,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(paths.contains("/sdcard/ROMs/SNES/SNES/Snes9x"))
    }

    // --- Content dir name semantics (guards against slug leak) ---

    @Test
    fun `save path uses ROM parent dir basename not platform slug`() {
        // Regression guard for the bug where callers were passing the internal
        // platform slug ("snes") instead of the ROM's actual parent directory
        // basename ("Super Nintendo"). RetroArch cares about the on-disk name.
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = false
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = null
        )
        assertTrue(paths.any { it.endsWith("/Super Nintendo") })
    }

    @Test
    fun `save path skips content-dir suffix when contentDirName is null`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = null,
            coreName = "Snes9x"
        )
        // sort-by-content falls through silently; sort-by-core still applies.
        assertTrue(paths.contains("/sdcard/RetroArch/saves/Snes9x"))
    }

    // --- State paths: non-content-dir mode ---

    @Test
    fun `state path uses savestate_directory when no sort flags set`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = false
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/states"))
    }

    @Test
    fun `state path appends core name when sort_savestates_enable is true`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/states/Snes9x"))
    }

    @Test
    fun `state path appends content dir when sort_savestates_by_content_enable is true`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = false
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(
            "sortByContentDirectory must apply to state paths; got $paths",
            paths.contains("/sdcard/RetroArch/states/Super Nintendo")
        )
    }

    @Test
    fun `state path appends content dir then core when both sort flags set`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = false,
            sortByContentDirectory = true,
            sortByCore = true
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "Super Nintendo",
            coreName = "Snes9x"
        )
        assertTrue(paths.contains("/sdcard/RetroArch/states/Super Nintendo/Snes9x"))
    }

    // --- State paths: content-dir mode ---

    @Test
    fun `state path uses content directory when savestates_in_content_dir is true`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = true,
            sortByContentDirectory = false,
            sortByCore = false
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(paths.contains("/sdcard/ROMs/SNES"))
    }

    @Test
    fun `state path content-dir mode honors both sort flags`() {
        val config = RetroArchStateConfig(
            savestateDirectory = "/sdcard/RetroArch/states",
            savestatesInContentDir = true,
            sortByContentDirectory = true,
            sortByCore = true
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES"
        )
        assertTrue(paths.contains("/sdcard/ROMs/SNES/SNES/Snes9x"))
    }

    // --- basePathOverride short-circuits content-dir mode ---

    @Test
    fun `save path basePathOverride skips content-dir even when flag is set`() {
        val config = RetroArchSaveConfig(
            savefileDirectory = "/sdcard/RetroArch/saves",
            savefilesInContentDir = true,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = "SNES",
            coreName = "Snes9x",
            contentDirectory = "/sdcard/ROMs/SNES",
            basePathOverride = "/sdcard/custom/saves"
        )
        assertTrue(paths.contains("/sdcard/custom/saves/Snes9x"))
        assertTrue(paths.none { it.startsWith("/sdcard/ROMs/SNES") })
    }

    @Test
    fun `resolveSavePathsWithConfig returns fallback when config is null`() {
        val paths = parser.resolveSavePathsWithConfig(
            config = null,
            contentDirName = null,
            coreName = null
        )
        assertTrue(paths.isNotEmpty())
        assertTrue(paths.any { it.contains("RetroArch/saves") })
    }

    @Test
    fun `parseFile defaults sortByCore to false when sort_savefiles_enable is absent`() {
        val cfg = writeCfg("""savefile_directory = "/sdcard/RetroArch/saves"""")
        val parsed = parser.parseFile(cfg)
        assertFalse("absent sort_savefiles_enable should default to false, matching RetroArch (savefiles flat unless opted in)", parsed.sortByCore)
        assertFalse(parsed.sortByContentDirectory)
    }

    @Test
    fun `parseFile honors sort_savefiles_enable false`() {
        val cfg = writeCfg(
            """
            savefile_directory = "/sdcard/RetroArch/saves"
            sort_savefiles_enable = "false"
            """.trimIndent()
        )
        assertFalse(parser.parseFile(cfg).sortByCore)
    }

    @Test
    fun `parseFile honors sort_savefiles_enable true explicit`() {
        val cfg = writeCfg(
            """
            savefile_directory = "/sdcard/RetroArch/saves"
            sort_savefiles_enable = "true"
            """.trimIndent()
        )
        assertTrue(parser.parseFile(cfg).sortByCore)
    }

    @Test
    fun `parseStateConfigFromFile defaults sortByCore to true when sort_savestates_enable is absent`() {
        val cfg = writeCfg("""savestate_directory = "/sdcard/RetroArch/states"""")
        val parsed = parser.parseStateConfigFromFile(cfg)
        assertTrue("absent sort_savestates_enable should default to true", parsed.sortByCore)
        assertFalse(parsed.sortByContentDirectory)
    }

    @Test
    fun `parseStateConfigFromFile honors sort_savestates_enable false`() {
        val cfg = writeCfg(
            """
            savestate_directory = "/sdcard/RetroArch/states"
            sort_savestates_enable = "false"
            """.trimIndent()
        )
        assertFalse(parser.parseStateConfigFromFile(cfg).sortByCore)
    }

    @Test
    fun `getRetroArchSaveDirName falls back to core id when no override is registered`() {
        assertEquals("mesen", EmulatorRegistry.getRetroArchSaveDirName("mesen"))
        assertEquals("snes9x", EmulatorRegistry.getRetroArchSaveDirName("snes9x"))
        assertEquals("pcsx_rearmed", EmulatorRegistry.getRetroArchSaveDirName("pcsx_rearmed"))
    }

    @Test
    fun `getRetroArchSaveDirName overrides cores whose library_name differs from id by more than case`() {
        assertEquals("Mupen64Plus-Next", EmulatorRegistry.getRetroArchSaveDirName("mupen64plus_next_gles3"))
        assertEquals("Mupen64Plus-Next", EmulatorRegistry.getRetroArchSaveDirName("mupen64plus_next_gles2"))
        assertEquals("VICE x64", EmulatorRegistry.getRetroArchSaveDirName("vice_x64"))
        assertEquals("VICE x64sc", EmulatorRegistry.getRetroArchSaveDirName("vice_x64sc"))
        assertEquals("melonDS DS", EmulatorRegistry.getRetroArchSaveDirName("melondsds"))
    }
}
