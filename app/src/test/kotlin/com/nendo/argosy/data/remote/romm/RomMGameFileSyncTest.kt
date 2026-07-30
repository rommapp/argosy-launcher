package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.MusicDirectoryManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * A game's soundtrack tracks only ever arrive on the single-ROM response; the list endpoint
 * reports an empty file array for every game. Recording what each response carries, and
 * pruning only from one that actually enumerated files, is what keeps a title-id platform
 * from ending up with no tracks and therefore no theme to play.
 */
class RomMGameFileSyncTest {

    private val gameFileDao = mockk<GameFileDao>(relaxed = true)
    private val musicDirectoryManager = mockk<MusicDirectoryManager>(relaxed = true)
    private lateinit var sync: RomMGameFileSync

    @Before
    fun setUp() {
        coEvery { gameFileDao.getByRommFileId(any()) } returns null
        coEvery { musicDirectoryManager.targetFileFor(any(), any(), any(), any(), any()) } returns
            File("/does/not/exist.mp3")
        sync = RomMGameFileSync(gameFileDao, musicDirectoryManager)
    }

    private fun file(id: Long, name: String, category: String, track: Int? = null) = RomMRomFile(
        id = id,
        romId = 11607L,
        fileName = name,
        filePath = "roms/switch",
        fileSizeBytes = 1024L,
        fullPath = "roms/switch/$name",
        category = category,
        trackMeta = track?.let { RomMTrackMeta(title = name.substringBeforeLast('.'), track = it, durationSeconds = 120.0) }
    )

    private fun rom(files: List<RomMRomFile>?) = RomMRom(
        id = 11607L,
        platformId = 20L,
        platformSlug = "switch",
        name = "Super Mario Odyssey",
        slug = "super-mario-odyssey",
        fileName = "Super Mario Odyssey",
        filePath = "roms/switch",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        files = files
    )

    @Test
    fun `soundtrack tracks are recorded on a title-id platform`() = runTest {
        val captured = slot<List<GameFileEntity>>()
        coEvery { gameFileDao.insertAll(capture(captured)) } returns Unit

        sync.sync(
            gameId = 1L,
            rom = rom(listOf(
                file(1, "Super Mario Odyssey.nsp", "game"),
                file(2, "1-01. Title Screen.mp3", "soundtrack", track = 1),
                file(3, "1-02. Bonneton.mp3", "soundtrack", track = 2)
            )),
            platformSlug = "switch",
            fileListIsAuthoritative = true
        )

        val categories = captured.captured.map { it.category }
        assertEquals(3, captured.captured.size)
        assertEquals(2, categories.count { it == VariantCategory.SOUNDTRACK.key })
        assertTrue(categories.contains(VariantCategory.GAME.key))
    }

    @Test
    fun `track titles and durations survive so a theme can be picked`() = runTest {
        val captured = slot<List<GameFileEntity>>()
        coEvery { gameFileDao.insertAll(capture(captured)) } returns Unit

        sync.sync(
            gameId = 1L,
            rom = rom(listOf(file(2, "1-01. Title Screen.mp3", "soundtrack", track = 1))),
            platformSlug = "switch",
            fileListIsAuthoritative = true
        )

        val row = captured.captured.single()
        assertEquals("1-01. Title Screen", row.trackTitle)
        assertEquals(1, row.trackNumber)
        assertEquals(120.0, row.durationSeconds!!, 0.01)
    }

    @Test
    fun `a response carrying no files leaves existing rows alone`() = runTest {
        sync.sync(1L, rom(emptyList()), "switch", fileListIsAuthoritative = false)

        coVerify(exactly = 0) { gameFileDao.deleteByGameId(any()) }
        coVerify(exactly = 0) { gameFileDao.insertAll(any()) }
    }

    @Test
    fun `an authoritative empty list does clear the rows`() = runTest {
        sync.sync(1L, rom(emptyList()), "switch", fileListIsAuthoritative = true)

        coVerify(exactly = 1) { gameFileDao.deleteByGameId(1L) }
    }

    @Test
    fun `a partial list never prunes rows it did not enumerate`() = runTest {
        sync.sync(
            gameId = 1L,
            rom = rom(listOf(file(1, "update.nsp", "update"))),
            platformSlug = "switch",
            fileListIsAuthoritative = false
        )

        coVerify(exactly = 0) { gameFileDao.deleteInvalidFiles(any(), any()) }
    }

    @Test
    fun `an absent file list is not an empty one`() = runTest {
        sync.sync(1L, rom(null), "switch", fileListIsAuthoritative = true)

        coVerify(exactly = 0) { gameFileDao.deleteByGameId(any()) }
        coVerify(exactly = 0) { gameFileDao.insertAll(any()) }
    }
}
