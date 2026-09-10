package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.SaveFilePayload
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private const val GAME_ID = 6369L
private const val OWNER = 42L
private const val OTHER_OWNER = 43L
private const val CHANNEL = "A Plumber For All Seasons [2026-09-01_04-49-53]"

/**
 * Deleting a slot has to remove every record that can re-advertise it, not only the cache and the
 * registration. Each assertion here names one such record; on the build before this cleanup none
 * of them was touched.
 */
class DeleteSaveChannelUseCaseTest {

    private val getUnifiedSaves: GetUnifiedSavesUseCase = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
    private val stateCacheManager: StateCacheManager = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private val pendingConflictDao: PendingConflictDao = mockk(relaxed = true)
    private val saveOwnershipDao: SaveOwnershipDao = mockk(relaxed = true)
    private val payloadCodec = SyncPayloadCodec(Moshi.Builder().build())

    private val useCase = DeleteSaveChannelUseCase(
        getUnifiedSaves, saveCacheManager, saveSyncRepository, stateCacheManager, activeSaveRepository,
        saveSyncDao, pendingSyncQueueDao, pendingConflictDao, saveOwnershipDao, payloadCodec
    )

    private fun entry(cacheId: Long?, serverId: Long?, channel: String? = CHANNEL) = UnifiedSaveEntry(
        localCacheId = cacheId,
        serverSaveId = serverId,
        timestamp = Instant.EPOCH,
        size = 128L,
        channelName = channel,
        source = when {
            cacheId != null && serverId != null -> UnifiedSaveEntry.Source.BOTH
            cacheId != null -> UnifiedSaveEntry.Source.LOCAL
            else -> UnifiedSaveEntry.Source.SERVER
        }
    )

    private fun queueRow(id: Long, channel: String?, owner: Long? = OWNER, cacheId: Long? = null) =
        PendingSyncQueueEntity(
            id = id,
            gameId = GAME_ID,
            rommId = 6576L,
            syncType = SyncType.SAVE_FILE,
            priority = SyncPriority.SAVE_FILE,
            payloadJson = payloadCodec.encode(SaveFilePayload(emulatorId = "argosy", channelName = channel)),
            ownerUserId = owner,
            cacheId = cacheId
        )

    private fun arrange(entries: List<UnifiedSaveEntry>, serverDeleteSucceeds: Boolean = true) {
        coEvery { activeSaveRepository.activeOwnerId() } returns OWNER
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns "autosave"
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns entries
        coEvery { saveSyncRepository.deleteServerSaves(any()) } returns serverDeleteSucceeds
        coEvery { stateCacheManager.getStatesForChannel(GAME_ID, CHANNEL) } returns emptyList()
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns emptyList()
    }

    @Test
    fun `the sync tracking row for the channel is removed`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L)))

        useCase(GAME_ID, CHANNEL)

        coVerify(exactly = 1) { saveSyncDao.deleteByGameAndChannel(GAME_ID, CHANNEL, OWNER) }
    }

    @Test
    fun `a queued upload naming the channel is dropped and the autosave's is kept`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L)))
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns listOf(
            queueRow(id = 1L, channel = CHANNEL),
            queueRow(id = 2L, channel = "autosave"),
            queueRow(id = 3L, channel = null)
        )

        useCase(GAME_ID, CHANNEL)

        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(1L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(2L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(3L) }
    }

    @Test
    fun `a queued upload pinned to a deleted cache row is dropped whatever its payload says`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L), entry(cacheId = 96L, serverId = null)))
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns listOf(
            queueRow(id = 4L, channel = "autosave", cacheId = 96L),
            queueRow(id = 5L, channel = "autosave", cacheId = 500L)
        )

        useCase(GAME_ID, CHANNEL)

        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(4L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(5L) }
    }

    @Test
    fun `another account's queued upload for the same channel name is kept`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L)))
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns listOf(
            queueRow(id = 6L, channel = CHANNEL, owner = OTHER_OWNER)
        )

        useCase(GAME_ID, CHANNEL)

        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `parked conflicts and ownership attribution for the channel are removed`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L)))

        useCase(GAME_ID, CHANNEL)

        coVerify(exactly = 1) {
            pendingConflictDao.deleteByGameAndSlot(GAME_ID, CHANNEL, PendingConflictEntity.ownerScope(OWNER))
        }
        coVerify(exactly = 1) { saveOwnershipDao.detachChannel(GAME_ID, CHANNEL, OWNER) }
    }

    @Test
    fun `a failed server delete still clears local records and reports the failure`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = 88L)), serverDeleteSucceeds = false)

        val result = useCase(GAME_ID, CHANNEL)

        assertEquals(DeleteSaveChannelUseCase.Result.ServerDeleteFailed, result)
        coVerify(exactly = 1) { saveSyncRepository.deleteServerSaves(listOf(88L)) }
        coVerify(exactly = 1) { saveCacheManager.deleteSave(95L) }
        coVerify(exactly = 1) { saveSyncDao.deleteByGameAndChannel(GAME_ID, CHANNEL, OWNER) }
        coVerify(exactly = 1) { activeSaveRepository.forgetChannel(GAME_ID, CHANNEL) }
    }

    @Test
    fun `a slot with no server copies reports success without calling the server`() = runTest {
        arrange(listOf(entry(cacheId = 95L, serverId = null)))

        val result = useCase(GAME_ID, CHANNEL)

        assertEquals(DeleteSaveChannelUseCase.Result.Deleted, result)
        coVerify(exactly = 0) { saveSyncRepository.deleteServerSaves(any()) }
    }
}
