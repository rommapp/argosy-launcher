package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DatabaseAdminRepositoryDeleteFilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameDao: GameDao
    private lateinit var repository: DatabaseAdminRepository
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        gameDao = mockk(relaxed = true)
        val database = mockk<ALauncherDatabase>(relaxed = true)
        every { database.gameDao() } returns gameDao
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns filesDir
        repository = DatabaseAdminRepository(
            context = context,
            database = database,
            imageCacheManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true),
            emulatorDownloadManager = mockk(relaxed = true),
            steamContentManager = mockk(relaxed = true),
            mediaDownloadManager = mockk(relaxed = true),
            socialRepository = mockk(relaxed = true),
            soundFeedbackManager = mockk(relaxed = true),
            hardResetRecorder = HardResetRecorder(context, Moshi.Builder().build())
        )
    }

    @Test
    fun `deletes downloaded files and keeps adopted files`() = runTest {
        val downloaded = romFile("downloaded.nes")
        val adopted = romFile("adopted.nes")
        coEvery { gameDao.getDownloadedBySources(any()) } returns listOf(
            game(id = 1L, path = downloaded.absolutePath, origin = FileOrigin.ROMM_DOWNLOAD),
            game(id = 2L, path = adopted.absolutePath, origin = FileOrigin.ADOPTED)
        )

        val summary = repository.deleteDownloadedFiles(GameSource.entries)

        assertFalse(downloaded.exists())
        assertTrue(adopted.exists())
        assertEquals(1, summary.deleted)
        assertEquals(1, summary.kept)
        assertEquals(0, summary.failed)
    }

    @Test
    fun `steam downloads are deleted like romm downloads`() = runTest {
        val installDir = tempFolder.newFolder("steam", "123")
        File(installDir, "game.exe").writeText("bin")
        coEvery { gameDao.getDownloadedBySources(any()) } returns listOf(
            game(id = 3L, path = installDir.absolutePath, origin = FileOrigin.STEAM_DOWNLOAD)
        )

        val summary = repository.deleteDownloadedFiles(GameSource.entries)

        assertFalse(installDir.exists())
        assertEquals(1, summary.deleted)
        assertEquals(0, summary.kept)
    }

    @Test
    fun `writes a record listing every path with its outcome`() = runTest {
        val downloaded = romFile("downloaded.nes")
        val adopted = romFile("adopted.nes")
        coEvery { gameDao.getDownloadedBySources(any()) } returns listOf(
            game(id = 1L, path = downloaded.absolutePath, origin = FileOrigin.ROMM_DOWNLOAD),
            game(id = 2L, path = adopted.absolutePath, origin = FileOrigin.ADOPTED)
        )

        val summary = repository.deleteDownloadedFiles(GameSource.entries)

        val recordFile = summary.recordFile
        assertTrue(recordFile != null && recordFile.exists())
        assertEquals(File(filesDir, "reset_records"), recordFile!!.parentFile)
        val record = Moshi.Builder().build().adapter(HardResetRecord::class.java).fromJson(recordFile.readText())!!
        assertTrue(record.completedAt != null)
        assertEquals(2, record.entries.size)
        val byPath = record.entries.associateBy { it.path }
        assertEquals(HardResetRecorder.RESULT_DELETED, byPath.getValue(downloaded.absolutePath).result)
        assertEquals(HardResetRecorder.ACTION_DELETE, byPath.getValue(downloaded.absolutePath).action)
        assertEquals(HardResetRecorder.RESULT_KEPT, byPath.getValue(adopted.absolutePath).result)
        assertEquals(HardResetRecorder.ACTION_KEEP, byPath.getValue(adopted.absolutePath).action)
    }

    @Test
    fun `preview counts and sizes deletable and kept files separately`() = runTest {
        val downloaded = romFile("downloaded.nes", size = 10)
        val adopted = romFile("adopted.nes", size = 25)
        coEvery { gameDao.getDownloadedBySources(any()) } returns listOf(
            game(id = 1L, path = downloaded.absolutePath, origin = FileOrigin.ROMM_DOWNLOAD),
            game(id = 2L, path = adopted.absolutePath, origin = FileOrigin.ADOPTED)
        )

        val preview = repository.previewHardReset()

        assertEquals(HardResetPreview(deleteCount = 1, deleteBytes = 10L, keepCount = 1, keepBytes = 25L), preview)
    }

    private fun romFile(name: String, size: Int = 4): File =
        File(tempFolder.root, name).apply { writeText("x".repeat(size)) }

    private fun game(id: Long, path: String, origin: FileOrigin) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "nes",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = path,
        fileOrigin = origin,
        rommId = id,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
