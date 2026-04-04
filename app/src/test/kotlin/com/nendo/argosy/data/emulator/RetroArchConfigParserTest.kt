package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the sort-flag matrix for [RetroArchConfigParser.resolveSavePathsWithConfig] and
 * [RetroArchConfigParser.resolveStatePathsWithConfig], plus the content-directory semantics
 * that RetroArch uses for `sort_savefiles_by_content_enable` and the analogous state flag.
 *
 * The `sort_by_content_enable` flags sort into the ROM's parent directory basename, NOT the
 * internal platform slug. These tests guard against regressions where the platform slug
 * leaks into the parser.
 */
class RetroArchConfigParserTest {

    private lateinit var parser: RetroArchConfigParser

    @Before
    fun setup() {
        parser = RetroArchConfigParser()
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
        // This flag was parsed into RetroArchStateConfig but never applied before the fix.
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
        // Override forces non-content-dir branch; core subdir still applies.
        assertTrue(paths.contains("/sdcard/custom/saves/Snes9x"))
        // And the content directory must NOT appear.
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
}
