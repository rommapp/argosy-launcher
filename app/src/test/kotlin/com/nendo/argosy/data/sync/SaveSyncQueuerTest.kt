package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SaveSyncQueuerTest {

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true)

    private lateinit var queuer: SaveSyncQueuerImpl

    private val session = PersistedSession(
        gameId = 1L,
        emulatorPackage = "dev.eden.eden_emulator",
        startTime = Instant.now(),
        coreName = null,
        isHardcore = false,
        channelName = null,
    )

    private val rommGame = GameEntity(
        id = 1L,
        platformId = 10L,
        platformSlug = "switch",
        title = "BOTW",
        sortTitle = "botw",
        localPath = "/storage/emulated/0/Documents/romm/switch/botw.nsp",
        rommId = 100L,
        igdbId = null,
        source = GameSource.ROMM_SYNCED,
    )

    @Before
    fun setUp() {
        mockkObject(SaveSyncWorker.Companion)
        every { SaveSyncWorker.runNow(any()) } answers { }

        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences(saveSyncEnabled = true))
        coEvery { gameDao.getById(1L) } returns rommGame
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns EmulatorConfigEntity(
            platformId = 10L, gameId = null, packageName = "dev.eden.eden_emulator",
            displayName = "Eden", coreName = null, isDefault = true,
        )
        every { emulatorResolver.resolveEmulatorId("dev.eden.eden_emulator") } returns "eden"
        coEvery {
            saveSyncRepository.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any())
        } returns "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save/0/0/01007EF00011E000"

        queuer = SaveSyncQueuerImpl(
            context, gameDao, emulatorConfigDao, emulatorResolver,
            preferencesRepository, saveSyncRepository, pendingSyncQueueDao,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(SaveSyncWorker.Companion)
    }

    @Test
    fun `enqueues when no row exists`() = runTest {
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns emptyList()

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertTrue(ok)
        coVerify(exactly = 1) { saveSyncRepository.queueUpload(1L, "eden", any()) }
        coVerify(exactly = 1) { SaveSyncWorker.runNow(context) }
    }

    @Test
    fun `enqueues when only FAILED row exists`() = runTest {
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns listOf(
            row(status = SyncStatus.FAILED),
        )

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertTrue(ok)
        coVerify(exactly = 1) { saveSyncRepository.queueUpload(1L, "eden", any()) }
        coVerify(exactly = 1) { SaveSyncWorker.runNow(context) }
    }

    @Test
    fun `skips when PENDING row exists`() = runTest {
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns listOf(
            row(status = SyncStatus.PENDING),
        )

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertTrue(ok)
        coVerify(exactly = 0) { saveSyncRepository.queueUpload(any(), any(), any()) }
        coVerify(exactly = 0) { SaveSyncWorker.runNow(any()) }
    }

    @Test
    fun `skips when IN_PROGRESS row exists`() = runTest {
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns listOf(
            row(status = SyncStatus.IN_PROGRESS),
        )

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertTrue(ok)
        coVerify(exactly = 0) { saveSyncRepository.queueUpload(any(), any(), any()) }
    }

    @Test
    fun `bails when saveSyncEnabled is off`() = runTest {
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences(saveSyncEnabled = false))

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertFalse(ok)
        coVerify(exactly = 0) { saveSyncRepository.queueUpload(any(), any(), any()) }
    }

    @Test
    fun `bails when game has no rommId`() = runTest {
        coEvery { gameDao.getById(1L) } returns rommGame.copy(rommId = null)

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertFalse(ok)
        coVerify(exactly = 0) { saveSyncRepository.queueUpload(any(), any(), any()) }
    }

    @Test
    fun `bails when discoverSavePath returns null`() = runTest {
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns emptyList()
        coEvery {
            saveSyncRepository.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any())
        } returns null

        val ok = queuer.ensureQueuedForActiveSession(session)

        assertFalse(ok)
        coVerify(exactly = 0) { saveSyncRepository.queueUpload(any(), any(), any()) }
    }

    private fun row(status: SyncStatus, syncType: SyncType = SyncType.SAVE_FILE) =
        PendingSyncQueueEntity(
            id = 1L, gameId = 1L, rommId = 100L, syncType = syncType, priority = 0,
            payloadJson = "{}", status = status,
        )
}
