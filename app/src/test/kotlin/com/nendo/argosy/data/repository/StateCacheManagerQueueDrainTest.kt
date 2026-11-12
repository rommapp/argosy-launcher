package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.sync.SaveStatePayload
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StateCacheManagerQueueDrainTest {

    private val gameDao: GameDao = mockk(relaxed = true)
    private val stateCacheDao: StateCacheDao = mockk(relaxed = true)
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private val saveSyncApiClient: SaveSyncApiClient = mockk(relaxed = true)
    private val api: RomMApi = mockk(relaxed = true)
    private val codec = SyncPayloadCodec(Moshi.Builder().build())
    private lateinit var manager: StateCacheManager

    private val game = GameEntity(
        id = GAME_ID,
        title = "Blue",
        sortTitle = "blue",
        platformId = 1L,
        platformSlug = "gb",
        rommId = CURRENT_ROM,
        igdbId = null,
        localPath = "/storage/roms/blue.gb",
        source = GameSource.ROMM_SYNCED
    )

    private val state = StateCacheEntity(
        id = STATE_ID,
        gameId = GAME_ID,
        platformSlug = "gb",
        emulatorId = "retroarch",
        slotNumber = 1,
        cachedAt = Instant.EPOCH,
        stateSize = 8L,
        cachePath = "states/1.state"
    )

    @Before
    fun setup() {
        every { saveSyncApiClient.getApi() } returns api
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { stateCacheDao.getById(STATE_ID) } returns state
        manager = spyk(
            StateCacheManager(
                context = mockk<Context>(relaxed = true),
                gameDao = gameDao,
                stateCacheDao = stateCacheDao,
                stateTombstoneDao = mockk(relaxed = true),
                saveCacheDao = mockk(relaxed = true),
                saveSyncDao = mockk(relaxed = true),
                pendingSyncQueueDao = pendingSyncQueueDao,
                emulatorSaveConfigDao = mockk(relaxed = true),
                preferencesRepository = mockk(relaxed = true),
                syncPreferencesRepository = mockk(relaxed = true) {
                    coEvery { getRommUserId() } returns null
                },
                coreVersionExtractor = mockk(relaxed = true),
                retroArchConfigParser = mockk(relaxed = true),
                retroArchPathResolver = mockk(relaxed = true),
                libretroStatePathResolver = mockk(relaxed = true),
                saveSyncApiClient = saveSyncApiClient,
                payloadCodec = codec,
                attributionRepository = mockk(relaxed = true),
                stateOwnershipTracker = mockk(relaxed = true)
            )
        )
        coEvery { manager.uploadStateToRomM(any(), any(), any(), any()) } returns
            StateCacheManager.StateCloudResult.Success
    }

    @Test
    fun `a queued state keyed to another rom stays local and is not uploaded`() = runTest {
        coEvery { pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_STATE) } returns listOf(row(STALE_ROM))

        val uploaded = manager.processPendingStateUploads()

        assertEquals(0, uploaded)
        coVerify(exactly = 0) { manager.uploadStateToRomM(any(), any(), any(), any()) }
        coVerify(exactly = 0) { stateCacheDao.deleteById(any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    @Test
    fun `a queued state keyed to the game's rom still uploads to that rom`() = runTest {
        coEvery { pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_STATE) } returns listOf(row(CURRENT_ROM))

        val uploaded = manager.processPendingStateUploads()

        assertEquals(1, uploaded)
        coVerify(exactly = 1) { manager.uploadStateToRomM(state, CURRENT_ROM, "blue", api) }
    }

    @Test
    fun `a queued state detached from its game stays local and is not uploaded`() = runTest {
        coEvery { stateCacheDao.getById(STATE_ID) } returns state.copy(gameId = -GAME_ID)
        coEvery { pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_STATE) } returns listOf(row(CURRENT_ROM))

        val uploaded = manager.processPendingStateUploads()

        assertEquals(0, uploaded)
        coVerify(exactly = 0) { manager.uploadStateToRomM(any(), any(), any(), any()) }
        coVerify(exactly = 0) { stateCacheDao.deleteById(any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    private fun row(rommId: Long) = PendingSyncQueueEntity(
        id = ROW_ID,
        gameId = GAME_ID,
        rommId = rommId,
        syncType = SyncType.SAVE_STATE,
        priority = SyncPriority.SAVE_STATE,
        payloadJson = codec.encode(SaveStatePayload(STATE_ID, "retroarch"))
    )

    private companion object {
        const val GAME_ID = 1L
        const val ROW_ID = 50L
        const val STATE_ID = 60L
        const val CURRENT_ROM = 100L
        const val STALE_ROM = 200L
    }
}
