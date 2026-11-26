package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.ActiveSaveRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class NegotiateInventoryTest {
    private val gameId = 5L
    private val transferStamp = Instant.parse("2026-09-01T00:00:00Z")
    private val cachedStamp = Instant.parse("2026-09-20T00:00:00Z")

    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)

    private val inventory = NegotiateInventory(
        saveSyncDao = saveSyncDao,
        saveCacheDao = saveCacheDao,
        gameDao = gameDao,
        activeSaveRepository = activeSaveRepository,
        syncPreferencesRepository = mockk(relaxed = true),
        savePathResolver = mockk(relaxed = true),
        fal = mockk(relaxed = true) { every { exists(any()) } returns true },
        liveSavePath = com.nendo.argosy.data.sync.fixtures.cachedPathAsStored()
    )

    private val row = SaveSyncEntity(
        id = 1L, gameId = gameId, rommId = 100L, emulatorId = "retroarch", channelName = "autosave",
        localSavePath = "/saves/game.srm", rommSaveId = 9L, lastUploadedHash = "server-hash",
        localContentHash = "local-hash", serverUpdatedAt = transferStamp,
        syncStatus = SaveSyncEntity.STATUS_SYNCED
    )

    private fun active(hash: String) = SaveCacheEntity(
        id = 40L, gameId = gameId, emulatorId = "retroarch", cachedAt = cachedStamp, saveSize = 8192,
        cachePath = "x/game.srm", contentHash = hash, identityHash = hash, channelName = "autosave"
    )

    @Before
    fun setup() {
        coEvery { saveSyncDao.getAllWithLocalPath(any()) } returns listOf(row)
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId, platformId = 1L, title = "Game", sortTitle = "game", localPath = "/roms/game.sfc",
            rommId = 100L, igdbId = null, source = GameSource.ROMM_SYNCED, platformSlug = "snes"
        )
    }

    @Test
    fun `an active version unchanged since its last transfer reports the server hash and stamp`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active("local-hash")

        val state = inventory.build(secureSaves = true).single()

        assertEquals("server-hash", state.contentHash)
        assertEquals(transferStamp.toString(), state.updatedAt)
        assertEquals(8192L, state.fileSizeBytes)
    }

    @Test
    fun `a new active version reports its own hash and the time Argosy cached it`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active("new-progress")

        val state = inventory.build(secureSaves = true).single()

        assertEquals("new-progress", state.contentHash)
        assertEquals(cachedStamp.toString(), state.updatedAt)
    }

    @Test
    fun `the server save this device last transferred is unchanged whatever form its hash is stored in`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active("archive-roots-form").copy(rommSaveId = 9L)

        val state = inventory.build(secureSaves = true).single()

        assertEquals("server-hash", state.contentHash)
        assertEquals(transferStamp.toString(), state.updatedAt)
    }

    @Test
    fun `a slot a background check moved to a newer server save reports the local version as it is, not the server head`() = runTest {
        val observed = row.copy(
            rommSaveId = 12L,
            lastUploadedHash = "head-hash",
            serverUpdatedAt = java.time.Instant.parse("2026-10-02T09:00:00Z"),
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
        coEvery { saveSyncDao.getAllWithLocalPath(any()) } returns listOf(observed)
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active("local-hash").copy(rommSaveId = 9L)

        val state = inventory.build(secureSaves = true).single()

        assertEquals("local-hash", state.contentHash)
        assertEquals(cachedStamp.toString(), state.updatedAt)
    }

    @Test
    fun `a named slot with no cached version is not reported from the disk save that belongs to the slot in play`() = runTest {
        coEvery { saveSyncDao.getAllWithLocalPath(any()) } returns listOf(row.copy(channelName = "main-save"))
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns null
        coEvery { activeSaveRepository.getActiveChannel(gameId) } returns "autosave"
        coEvery { saveCacheDao.getMostRecentInChannel(gameId, any(), "main-save") } returns null

        assertEquals(emptyList<Any>(), inventory.build(secureSaves = true))
    }

    @Test
    fun `a disk save cached under Eden stable is reported from the nightly folder the game launches with`() = runTest {
        val stablePath = "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save"
        val nightlyPath = "/storage/emulated/0/Android/data/dev.eden.eden_emulator.nightly/files/nand/user/save"
        val nightlyModified = Instant.parse("2026-10-05T08:00:00Z")
        coEvery { saveSyncDao.getAllWithLocalPath(any()) } returns listOf(row.copy(localSavePath = stablePath))
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns null
        coEvery { activeSaveRepository.getActiveChannel(gameId) } returns "autosave"
        coEvery { saveCacheDao.getMostRecentInChannel(gameId, any(), "autosave") } returns null
        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { exists(stablePath) } returns false
            every { exists(nightlyPath) } returns true
            every { lastModified(nightlyPath) } returns nightlyModified.toEpochMilli()
            every { length(nightlyPath) } returns 4096L
        }
        val nightlyInventory = NegotiateInventory(
            saveSyncDao = saveSyncDao,
            saveCacheDao = saveCacheDao,
            gameDao = gameDao,
            activeSaveRepository = activeSaveRepository,
            syncPreferencesRepository = mockk(relaxed = true),
            savePathResolver = mockk(relaxed = true),
            fal = fal,
            liveSavePath = com.nendo.argosy.data.sync.fixtures.launchingPackage("dev.eden.eden_emulator.nightly")
        )

        val state = nightlyInventory.build(secureSaves = true).single()

        assertEquals(nightlyModified.toString(), state.updatedAt)
        assertEquals(4096L, state.fileSizeBytes)
    }

    @Test
    fun `a rollback snapshot is never reported as the version`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active("snapshot").copy(isRollback = true)
        coEvery { saveCacheDao.getMostRecentInChannel(gameId, any(), "autosave") } returns active("new-progress")

        val state = inventory.build(secureSaves = true).single()

        assertEquals("new-progress", state.contentHash)
    }
}
