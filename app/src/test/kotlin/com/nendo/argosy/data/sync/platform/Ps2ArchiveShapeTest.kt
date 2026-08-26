package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * A PS2 folder memory card holds one directory per game, so an upload is rooted at the card
 * and the card's name identifies nothing. Older builds rooted the archive at the game's own
 * folder instead, and both shapes exist on servers, so a restore has to recognise each and
 * place it without emptying a game folder loose into the card.
 *
 * Fixtures mirror a real card read off an Odin 3: a directory named `test` carrying a
 * `_pcsx2_superblock` and `BASLUS-20152AC04` for Ace Combat 04.
 */
class Ps2ArchiveShapeTest {

    private lateinit var tempDir: File
    private lateinit var handler: FolderSaveHandler
    private lateinit var archiver: SaveArchiver
    private lateinit var card: File

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val saveId = "BASLUS-20152"
    private val gameFolder = "BASLUS-20152AC04"

    private val config = SavePathConfig(
        emulatorId = "armsx2",
        defaultPaths = emptyList(),
        saveExtensions = listOf("*"),
        usesFolderBasedSaves = true
    )

    @Before
    fun setUp() {
        tempDir = createTempDirectory("ps2_shape").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        archiver = SaveArchiver(androidDataAccessor, fal)
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = archiver,
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("ps2") ?: error("PS2 handler not registered")

        card = File(tempDir, "memcards/test").apply { mkdirs() }
        File(card, "_pcsx2_superblock").writeText("superblock")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun saveContext() = SaveContext(
        config = config,
        romPath = null,
        saveId = saveId,
        emulatorPackage = "com.armsx2",
        gameId = 1L,
        gameTitle = "Ace Combat 04",
        platformSlug = "ps2",
        emulatorId = "armsx2",
        localSavePath = card.absolutePath
    )

    private fun cardRootedArchive(): File {
        val staging = File(tempDir, "staging/test").apply { mkdirs() }
        File(staging, "_pcsx2_superblock").writeText("superblock")
        File(staging, gameFolder).mkdirs()
        File(staging, "$gameFolder/icon.sys").writeText("icon")
        File(staging, "$gameFolder/ace.bin").writeText("server-save")
        val zip = File(tempDir, "card-rooted.zip")
        assertTrue(archiver.zipFolder(staging, zip))
        return zip
    }

    private fun gameFolderRootedArchive(): File {
        val staging = File(tempDir, "legacy/$gameFolder").apply { mkdirs() }
        File(staging, "icon.sys").writeText("icon")
        File(staging, "ace.bin").writeText("server-save")
        val zip = File(tempDir, "folder-rooted.zip")
        assertTrue(archiver.zipFolder(staging, zip))
        return zip
    }

    @Test
    fun `an archive rooted at the card is accepted`() = runTest {
        val result = handler.extractDownload(cardRootedArchive(), saveContext())

        assertTrue(result.error ?: "", result.success)
        assertEquals("server-save", File(card, "$gameFolder/ace.bin").readText())
    }

    @Test
    fun `an archive rooted at the game folder keeps that folder`() = runTest {
        val result = handler.extractDownload(gameFolderRootedArchive(), saveContext())

        assertTrue(result.error ?: "", result.success)
        assertTrue("the game folder must survive", File(card, gameFolder).isDirectory)
        assertEquals("server-save", File(card, "$gameFolder/ace.bin").readText())
        assertFalse("contents must not land loose in the card", File(card, "ace.bin").exists())
    }

    @Test
    fun `a card left flattened by an older restore is repaired`() = runTest {
        File(card, "icon.sys").writeText("stray")
        File(card, "ace.bin").writeText("stray")

        val result = handler.extractDownload(gameFolderRootedArchive(), saveContext())

        assertTrue(result.error ?: "", result.success)
        assertFalse("strays must not remain at the card root", File(card, "ace.bin").exists())
        assertFalse(File(card, "icon.sys").exists())
        assertEquals("server-save", File(card, "$gameFolder/ace.bin").readText())
        assertTrue("the superblock is not a stray", File(card, "_pcsx2_superblock").exists())
    }

    @Test
    fun `an archive for a different game is still refused`() = runTest {
        val staging = File(tempDir, "other/BASLUS-21693XX").apply { mkdirs() }
        File(staging, "other.bin").writeText("not ours")
        val zip = File(tempDir, "other.zip")
        assertTrue(archiver.zipFolder(staging, zip))

        val result = handler.extractDownload(zip, saveContext())

        assertFalse(result.success)
        assertFalse(File(card, "BASLUS-21693XX").exists())
    }

    @Test
    fun `folder names are visible at any depth`() {
        val folders = archiver.peekFolderNames(cardRootedArchive())

        assertTrue(folders.contains("test"))
        assertTrue(folders.contains(gameFolder))
    }
}
