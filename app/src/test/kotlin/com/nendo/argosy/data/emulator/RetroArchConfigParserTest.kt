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
    fun `getRetroArchSaveDirName falls back to core id when library_name differs by no more than case`() {
        assertEquals("mesen", EmulatorRegistry.getRetroArchSaveDirName("mesen"))
        assertEquals("snes9x", EmulatorRegistry.getRetroArchSaveDirName("snes9x"))
        assertEquals("gambatte", EmulatorRegistry.getRetroArchSaveDirName("gambatte"))
    }

    @Test
    fun `getRetroArchSaveDirName maps cores whose libretro corename differs from id by more than case`() {
        assertEquals("Genesis Plus GX", EmulatorRegistry.getRetroArchSaveDirName("genesis_plus_gx"))
        assertEquals("PCSX-ReARMed", EmulatorRegistry.getRetroArchSaveDirName("pcsx_rearmed"))
        assertEquals("Beetle PSX HW", EmulatorRegistry.getRetroArchSaveDirName("mednafen_psx_hw"))
        assertEquals("ParaLLEl N64", EmulatorRegistry.getRetroArchSaveDirName("parallel_n64"))
        assertEquals("Mupen64Plus-Next", EmulatorRegistry.getRetroArchSaveDirName("mupen64plus_next_gles3"))
        assertEquals("Mupen64Plus-Next", EmulatorRegistry.getRetroArchSaveDirName("mupen64plus_next_gles2"))
        assertEquals("DOSBox-pure", EmulatorRegistry.getRetroArchSaveDirName("dosbox_pure"))
        assertEquals("MAME 2010 (0.139)", EmulatorRegistry.getRetroArchSaveDirName("mame2010"))
        assertEquals("VICE x64", EmulatorRegistry.getRetroArchSaveDirName("vice_x64"))
        assertEquals("VICE x64sc", EmulatorRegistry.getRetroArchSaveDirName("vice_x64sc"))
        assertEquals("melonDS DS", EmulatorRegistry.getRetroArchSaveDirName("melondsds"))
    }

    // --- Existing on-disk core folder wins over the guessed name ---
    // Resolver should reuse the folder's real name, not the slug guess (which
    // only matches on a case-insensitive FS).

    @Test
    fun `save path snaps sort-by-core folder to existing on-disk name despite case`() {
        val base = tempFolder.newFolder("saves")
        File(base, "Gambatte").mkdirs()
        val config = RetroArchSaveConfig(
            savefileDirectory = base.absolutePath,
            savefilesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = null,
            coreName = "gambatte"
        )
        assertTrue(
            "expected resolver to reuse RetroArch's real 'Gambatte' folder; got $paths",
            paths.contains("${base.absolutePath}/Gambatte")
        )
        assertTrue("must not invent a lowercase 'gambatte' folder", paths.none { it.endsWith("/gambatte") })
    }

    @Test
    fun `save path snaps underscored slug to on-disk folder with spaces`() {
        val base = tempFolder.newFolder("saves-slug")
        File(base, "Genesis Plus GX").mkdirs()
        val config = RetroArchSaveConfig(
            savefileDirectory = base.absolutePath,
            savefilesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = null,
            coreName = "genesis_plus_gx"
        )
        assertTrue(
            "unlisted-core slug should snap to the real 'Genesis Plus GX' folder; got $paths",
            paths.contains("${base.absolutePath}/Genesis Plus GX")
        )
    }

    @Test
    fun `save path leaves core name untouched when no folder exists yet`() {
        val base = tempFolder.newFolder("saves-fresh")
        val config = RetroArchSaveConfig(
            savefileDirectory = base.absolutePath,
            savefilesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveSavePathsWithConfig(
            config = config,
            contentDirName = null,
            coreName = "Gambatte"
        )
        assertTrue(paths.contains("${base.absolutePath}/Gambatte"))
    }

    @Test
    fun `null-config save path snaps to existing core folder despite case`() {
        val base = tempFolder.newFolder("saves-nullcfg")
        File(base, "Genesis Plus GX").mkdirs()
        val paths = parser.resolveSavePathsWithConfig(
            config = null,
            contentDirName = null,
            coreName = "genesis plus gx",
            basePathOverride = base.absolutePath
        )
        assertTrue(
            "config-missing fallback must reuse the real 'Genesis Plus GX' folder; got $paths",
            paths.contains("${base.absolutePath}/Genesis Plus GX")
        )
    }

    @Test
    fun `state path snaps sort-by-core folder to existing on-disk name despite case`() {
        val base = tempFolder.newFolder("states")
        File(base, "Snes9x").mkdirs()
        val config = RetroArchStateConfig(
            savestateDirectory = base.absolutePath,
            savestatesInContentDir = false,
            sortByContentDirectory = false,
            sortByCore = true
        )
        val paths = parser.resolveStatePathsWithConfig(
            config = config,
            contentDirName = null,
            coreName = "snes9x"
        )
        assertTrue(
            "expected resolver to reuse RetroArch's real 'Snes9x' folder; got $paths",
            paths.contains("${base.absolutePath}/Snes9x")
        )
        assertTrue(paths.none { it.endsWith("/snes9x") })
    }
}
