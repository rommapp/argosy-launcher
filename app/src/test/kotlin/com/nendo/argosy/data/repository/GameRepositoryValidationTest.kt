package com.nendo.argosy.data.repository

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.GameLocalPathInfo
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageVolumeHealth
import com.nendo.argosy.data.storage.VolumeProbe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameRepositoryValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var fal: FileAccessLayer
    private lateinit var repository: GameRepository
    private var storageState = Environment.MEDIA_MOUNTED

    private val goneP = "/storage/emulated/0/ROMs/nes/gone.nes"
    private val movedP = "/storage/emulated/0/ROMs/nes/moved.nes"

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageState() } answers { storageState }

        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        fal = mockk(relaxed = true)

        val probe = mockk<VolumeProbe>(relaxed = true)
        every { probe.isGenuinelyAbsent(any()) } returns true
        val volumeHealth = mockk<StorageVolumeHealth>(relaxed = true)
        every { volumeHealth.newProbe() } returns probe

        coEvery { gameFileDao.getAllWithLocalPath() } returns emptyList()
        every { fal.exists(any()) } returns false

        repository = GameRepository(
            context = mockk<Context>(relaxed = true),
            gameDao = gameDao,
            userRomsHiddenDao = mockk(relaxed = true),
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = gameFileDao,
            platformDao = mockk(relaxed = true),
            romMRepository = mockk(relaxed = true),
            overlayWriter = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            fileAccessLayer = fal,
            volumeHealth = volumeHealth,
            attributionRepository = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
    }

    @Test
    fun `a game whose file turns up elsewhere is not counted as a cleared path`() = runTest {
        val newHome = File(tempFolder.newFolder("nes"), "moved.nes").apply { writeText("rom") }
        every { fal.exists(newHome.absolutePath) } returns true
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(
            pathInfo(1L, goneP),
            pathInfo(2L, movedP)
        )
        coEvery { gameDao.getById(1L) } returns game(1L, localPath = null)
        coEvery { gameDao.getById(2L) } returns game(2L, localPath = newHome.absolutePath)

        assertEquals(1, repository.validateLocalFiles(force = true))
    }

    @Test
    fun `every walked row counts when nothing could be reclaimed`() = runTest {
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(
            pathInfo(1L, goneP),
            pathInfo(2L, movedP)
        )
        coEvery { gameDao.getById(any()) } returns null

        assertEquals(2, repository.validateLocalFiles(force = true))
    }

    @Test
    fun `a pass skipped for unready storage does not spend the coalescing window`() = runTest {
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(pathInfo(1L, goneP))
        coEvery { gameDao.getById(any()) } returns null

        storageState = "unmounted"
        assertNull(repository.validateLocalFiles())
        coVerify(exactly = 0) { gameDao.getGamesWithLocalPathInfo() }

        storageState = Environment.MEDIA_MOUNTED
        assertEquals(1, repository.validateLocalFiles())
        coVerify(exactly = 1) { gameDao.getGamesWithLocalPathInfo() }
    }

    @Test
    fun `a second request inside the window is answered by the pass that just ran`() = runTest {
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(pathInfo(1L, goneP))
        coEvery { gameDao.getById(any()) } returns null

        assertEquals(1, repository.validateLocalFiles())
        assertEquals(0, repository.validateLocalFiles())
        coVerify(exactly = 1) { gameDao.getGamesWithLocalPathInfo() }
    }

    private fun pathInfo(id: Long, path: String) = GameLocalPathInfo(
        id = id,
        platformId = 1L,
        platformSlug = "nes",
        source = GameSource.ROMM_SYNCED,
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        localPath = path
    )

    private fun game(id: Long, localPath: String?) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "nes",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = localPath,
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
