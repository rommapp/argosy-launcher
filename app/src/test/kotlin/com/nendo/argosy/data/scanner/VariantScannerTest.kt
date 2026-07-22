package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
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

class VariantScannerTest {

    private lateinit var tempDir: File
    private lateinit var dao: GameFileDao
    private lateinit var scanner: VariantScanner

    private val inserted = mutableListOf<GameFileEntity>()
    private val deleted = mutableListOf<Long>()

    @Before
    fun setup() {
        tempDir = createTempDirectory("variant_scanner_test").toFile()
        dao = mockk()
        inserted.clear()
        deleted.clear()
        coEvery { dao.getVariantsForGame(any()) } returns emptyList()
        coEvery { dao.getByGameIdAndFileName(any(), any()) } returns emptyList()
        coEvery { dao.getByLocalPath(any()) } returns null
        coEvery { dao.insert(capture(inserted)) } returns 1L
        coEvery { dao.deleteById(capture(deleted)) } just Runs
        scanner = VariantScanner(dao)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun gameFolder(name: String): File = File(tempDir, name).apply { mkdirs() }

    private fun write(dir: File, name: String, bytes: Int = 8): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(ByteArray(bytes)) }
    }

    private fun game(
        localPath: String?,
        platformSlug: String = "psx",
        id: Long = 1L
    ) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = platformSlug,
        title = "Test Game",
        sortTitle = "test game",
        localPath = localPath,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    @Test
    fun `multi-disc files in the game root are never variants`() = runTest {
        val folder = gameFolder("Final Fantasy VII")
        write(folder, "Final Fantasy VII (USA) (Disc 1).chd")
        write(folder, "Final Fantasy VII (USA) (Disc 2).chd")
        write(folder, "Final Fantasy VII (USA) (Disc 3).chd")
        val m3u = write(folder, "Final Fantasy VII.m3u")

        val added = scanner.scanForVariants(game(m3u.absolutePath))

        assertEquals(0, added)
        assertTrue("no variants should be created from root files", inserted.isEmpty())
    }

    @Test
    fun `sibling rom in the game root is not a variant`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        write(folder, "Some Game [Hack].chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `file in hack subdirectory becomes a hack variant`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        write(File(folder, "hack"), "Some Game - Cool Hack.chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(1, added)
        assertEquals("hack", inserted.single().category)
        assertEquals("Some Game - Cool Hack.chd", inserted.single().fileName)
        assertTrue(inserted.single().isLaunchTarget)
    }

    @Test
    fun `category is inferred from the subdirectory name`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        write(File(folder, "mod"), "A Mod.chd")
        write(File(folder, "translation"), "A Translation.chd")

        scanner.scanForVariants(game(base.absolutePath))

        val categories = inserted.map { it.category }.toSet()
        assertEquals(setOf("mod", "translation"), categories)
    }

    @Test
    fun `update and dlc subdirectories are not launch variants`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        write(File(folder, "update"), "An Update.chd")
        write(File(folder, "dlc"), "Some DLC.chd")
        write(File(folder, "extcontent"), "Extra.chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `unrecognized subdirectories are ignored`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        write(File(folder, "screenshots"), "shot.chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `patch and disc-component files inside a variant subdir are skipped`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        val hack = File(folder, "hack")
        write(hack, "patch.ips")
        write(hack, "data.bin")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `macOS resource forks in a variant subdir are skipped`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        val hack = File(folder, "hack")
        write(hack, "._Some Game - Cool Hack.chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `an m3u variant in a subdir is recorded as multi-disc`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        val hack = File(folder, "hack")
        val hackM3u = write(hack, "Cool Hack.m3u")

        scanner.scanForVariants(game(base.absolutePath))

        val variant = inserted.single()
        assertTrue(variant.isMultiDisc)
        assertEquals(hackM3u.absolutePath, variant.m3uPath)
    }

    @Test
    fun `stale local variant not in a subdir is deleted`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        val stalePath = File(folder, "Some Game (Disc 2).chd").absolutePath
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            GameFileEntity(id = 42L, gameId = 1L, rommFileId = null, fileName = "Some Game (Disc 2).chd", filePath = stalePath, category = "unknown", fileSize = 8L, localPath = stalePath)
        )

        scanner.scanForVariants(game(base.absolutePath))

        assertEquals(listOf(42L), deleted)
    }

    @Test
    fun `stale local variant with null localPath is deleted`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            GameFileEntity(id = 224L, gameId = 1L, rommFileId = null, fileName = "Disc 3.chd", filePath = "x", category = "unknown", fileSize = 8L, localPath = null)
        )

        scanner.scanForVariants(game(base.absolutePath))

        assertEquals(listOf(224L), deleted)
    }

    @Test
    fun `romm-synced variants are never deleted`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            GameFileEntity(id = 7L, gameId = 1L, rommFileId = 500L, fileName = "Official Hack.chd", filePath = "x", category = "hack", fileSize = 8L, localPath = null)
        )

        scanner.scanForVariants(game(base.absolutePath))

        assertTrue("romm-synced variant must survive", deleted.isEmpty())
    }

    @Test
    fun `excluded platforms are not scanned`() = runTest {
        val folder = gameFolder("Switch Game")
        val base = write(folder, "Switch Game.nsp")
        write(File(folder, "hack"), "Hacked.nsp")

        val added = scanner.scanForVariants(game(base.absolutePath, platformSlug = "switch"))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `games sitting directly in the platform root are not scanned`() = runTest {
        val platformRoot = gameFolder("psx")
        val base = write(platformRoot, "Some Game.chd")

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `already-known variant is not inserted again`() = runTest {
        val folder = gameFolder("Some Game")
        val base = write(folder, "Some Game.chd")
        val hackFile = write(File(folder, "hack"), "Cool Hack.chd")
        coEvery { dao.getByGameIdAndFileName(1L, "Cool Hack.chd") } returns listOf(
            GameFileEntity(
                id = 9L, gameId = 1L, fileName = "Cool Hack.chd", filePath = hackFile.absolutePath, category = "hack", fileSize = 8L, localPath = hackFile.absolutePath
            )
        )

        val added = scanner.scanForVariants(game(base.absolutePath))

        assertEquals(0, added)
        assertFalse(inserted.any { it.fileName == "Cool Hack.chd" })
    }
}
