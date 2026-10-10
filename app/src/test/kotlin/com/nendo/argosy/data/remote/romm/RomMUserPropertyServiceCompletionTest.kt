package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.local.entity.UnsentSyncTypeRow
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.sync.SyncCoordinator
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class RomMUserPropertyServiceCompletionTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
    private val pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true)
    private val syncCoordinator = mockk<SyncCoordinator>(relaxed = true)

    private lateinit var service: RomMUserPropertyService

    private val rommGame = GameEntity(
        id = 1L, platformId = 10L, platformSlug = "snes",
        title = "Game", sortTitle = "game",
        localPath = null, rommId = 100L,
        igdbId = null, source = GameSource.ROMM_SYNCED,
    )

    @Before
    fun setUp() {
        service = buildService(connectionManager = mockk(relaxed = true))
    }

    private fun buildService(connectionManager: RomMConnectionManager): RomMUserPropertyService =
        RomMUserPropertyService(
            apiClient = mockk(relaxed = true),
            connectionManager = connectionManager,
            gameDao = gameDao,
            overlayWriter = overlayWriter,
            pendingSyncQueueDao = pendingSyncQueueDao,
            imageCacheManager = mockk(relaxed = true),
            syncCoordinator = Lazy { syncCoordinator },
            userPreferencesRepository = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )

    @Test
    fun `completion writes the overlay before queueing the RomM push`() = runTest {
        coEvery { gameDao.getById(1L) } returns rommGame

        val result = service.updateCompletion(1L, 45)

        assertTrue(result is RomMResult.Success)
        coVerifyOrder {
            overlayWriter.updateCompletion(1L, 45)
            syncCoordinator.queuePropertyChange(1L, 100L, SyncType.COMPLETION, intValue = 45)
        }
    }

    @Test
    fun `completion of zero is written and queued so the server value clears`() = runTest {
        coEvery { gameDao.getById(1L) } returns rommGame

        service.updateCompletion(1L, 0)

        coVerify(exactly = 1) { overlayWriter.updateCompletion(1L, 0) }
        coVerify(exactly = 1) {
            syncCoordinator.queuePropertyChange(1L, 100L, SyncType.COMPLETION, intValue = 0)
        }
    }

    @Test
    fun `completion outside 0 to 100 is clamped before it is stored or queued`() = runTest {
        coEvery { gameDao.getById(1L) } returns rommGame

        service.updateCompletion(1L, 140)
        service.updateCompletion(1L, -5)

        coVerify(exactly = 1) { overlayWriter.updateCompletion(1L, 100) }
        coVerify(exactly = 1) {
            syncCoordinator.queuePropertyChange(1L, 100L, SyncType.COMPLETION, intValue = 100)
        }
        coVerify(exactly = 1) { overlayWriter.updateCompletion(1L, 0) }
        coVerify(exactly = 1) {
            syncCoordinator.queuePropertyChange(1L, 100L, SyncType.COMPLETION, intValue = 0)
        }
    }

    @Test
    fun `a local-only game stores completion without queueing a push`() = runTest {
        coEvery { gameDao.getById(1L) } returns rommGame.copy(rommId = null)

        val result = service.updateCompletion(1L, 30)

        assertTrue(result is RomMResult.Success)
        coVerify(exactly = 1) { overlayWriter.updateCompletion(1L, 30) }
        coVerify(exactly = 0) { syncCoordinator.queuePropertyChange(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unknown game writes nothing`() = runTest {
        coEvery { gameDao.getById(1L) } returns null

        val result = service.updateCompletion(1L, 30)

        assertTrue(result is RomMResult.Error)
        coVerify(exactly = 0) { overlayWriter.updateCompletion(any(), any()) }
        coVerify(exactly = 0) { syncCoordinator.queuePropertyChange(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refresh keeps a pending local completion instead of the server value`() = runTest {
        refreshWithServerCompletion(serverCompletion = 80, pendingCompletion = true)

        coVerify(exactly = 0) { overlayWriter.updateCompletion(any(), any()) }
    }

    @Test
    fun `refresh adopts the server completion when nothing is pending`() = runTest {
        refreshWithServerCompletion(serverCompletion = 80, pendingCompletion = false)

        coVerify(exactly = 1) { overlayWriter.updateCompletion(1L, 80) }
    }

    private suspend fun refreshWithServerCompletion(serverCompletion: Int, pendingCompletion: Boolean) {
        val api = mockk<RomMApi>(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        every { connectionManager.getApi() } returns api
        val rom = mockk<RomMRom>(relaxed = true)
        every { rom.romUser } returns RomMRomUser(completion = serverCompletion)
        coEvery { api.getRom(100L) } returns Response.success(rom)
        coEvery { gameDao.getById(1L) } returns rommGame
        coEvery { pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(any(), 1L) } returns
            if (pendingCompletion) listOf(UnsentSyncTypeRow(1L, SyncType.COMPLETION)) else emptyList()

        buildService(connectionManager).refreshUserProps(1L)
    }
}
