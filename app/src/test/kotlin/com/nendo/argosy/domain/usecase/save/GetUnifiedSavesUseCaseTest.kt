package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry.Source
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class GetUnifiedSavesUseCaseTest {

    private lateinit var saveCacheManager: SaveCacheManager
    private lateinit var saveSyncRepository: SaveSyncRepository
    private lateinit var gameDao: GameDao
    private lateinit var useCase: GetUnifiedSavesUseCase

    private val gameId = 1L
    private val rommId = 100L
    private val romBaseName = "Super Mario World (USA)"
    private val localPath = "/storage/roms/snes/$romBaseName.sfc"

    private val testGame = GameEntity(
        id = gameId,
        platformId = 1L,
        title = "Super Mario World",
        sortTitle = "super mario world",
        localPath = localPath,
        rommId = rommId,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        saveCacheManager = mockk(relaxed = true)
        saveSyncRepository = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        useCase = GetUnifiedSavesUseCase(saveCacheManager, saveSyncRepository, gameDao)
    }

    @Test
    fun `local-only entries without channel remain unlocked in timeline`() = runTest {
        val localCache = createLocalCache(id = 1, note = null, isLocked = false)
        setupMocks(localCaches = listOf(localCache), serverSaves = emptyList())

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(Source.LOCAL, entry.source)
        assertFalse(entry.isLocked)
        assertNull(entry.channelName)
    }

    @Test
    fun `local locked channel entries appear in save slots`() = runTest {
        val localCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        setupMocks(localCaches = listOf(localCache), serverSaves = emptyList())

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, slots.size)
        assertEquals("checkpoint", slots[0].channelName)
        assertEquals(Source.LOCAL, slots[0].source)
    }

    @Test
    fun `server-only named save gets isLocked true`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "checkpoint.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, entries.size)
        assertEquals(1, slots.size)
        assertEquals("checkpoint", slots[0].channelName)
        assertEquals(Source.SERVER, slots[0].source)
        assertTrue(slots[0].isLocked)
    }

    @Test
    fun `server-only latest save gets isLocked false`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "argosy-latest.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, entries.size)
        assertEquals(0, slots.size)
        assertNull(entries[0].channelName)
        assertEquals(Source.SERVER, entries[0].source)
        assertFalse(entries[0].isLocked)
    }

    @Test
    fun `server save with rom base name gets isLocked false`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "$romBaseName.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertFalse(entries[0].isLocked)
        assertNull(entries[0].channelName)
        assertTrue(entries[0].isLatest)
    }

    @Test
    fun `matching local and server save merged to BOTH source`() = runTest {
        val localCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm")
        setupMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(Source.BOTH, entry.source)
        assertEquals(1L, entry.localCacheId)
        assertEquals(10L, entry.serverSaveId)
        assertEquals("checkpoint", entry.channelName)
        assertTrue(entry.isLocked)
    }

    @Test
    fun `only most recent local cache matches server save`() = runTest {
        val now = Instant.now()
        val oldCache = createLocalCache(
            id = 1,
            note = "checkpoint",
            isLocked = true,
            cachedAt = now.minusSeconds(3600)
        )
        val newCache = createLocalCache(
            id = 2,
            note = "checkpoint",
            isLocked = true,
            cachedAt = now
        )
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm")
        setupMocks(
            localCaches = listOf(oldCache, newCache),
            serverSaves = listOf(serverSave)
        )

        val entries = useCase(gameId)

        val bothEntries = entries.filter { it.source == Source.BOTH }
        val localOnlyEntries = entries.filter { it.source == Source.LOCAL }
        assertEquals(1, bothEntries.size)
        assertEquals(2L, bothEntries[0].localCacheId)
        assertEquals(1, localOnlyEntries.size)
        assertEquals(1L, localOnlyEntries[0].localCacheId)
    }

    @Test
    fun `timeline contains all entries regardless of lock status`() = runTest {
        val lockedCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val unlockedCache = createLocalCache(id = 2, note = null, isLocked = false)
        val serverOnly = createServerSave(id = 10, fileName = "quicksave.srm")
        setupMocks(
            localCaches = listOf(lockedCache, unlockedCache),
            serverSaves = listOf(serverOnly)
        )

        val entries = useCase(gameId)

        assertEquals(3, entries.size)
        val slots = entries.filter { it.isLocked }
        assertEquals(2, slots.size)
        assertTrue(slots.any { it.channelName == "checkpoint" })
        assertTrue(slots.any { it.channelName == "quicksave" })
    }

    @Test
    fun `multiple distinct channels each appear in slots`() = runTest {
        val checkpoint = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val quicksave = createLocalCache(id = 2, note = "quicksave", isLocked = true)
        val backup = createServerSave(id = 10, fileName = "backup.srm")
        setupMocks(
            localCaches = listOf(checkpoint, quicksave),
            serverSaves = listOf(backup)
        )

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(3, slots.size)
        val channelNames = slots.mapNotNull { it.channelName }.toSet()
        assertEquals(setOf("checkpoint", "quicksave", "backup"), channelNames)
    }

    @Test
    fun `single channel cache is locked as most recent for channel`() = runTest {
        val cache = createLocalCache(id = 1, note = "crono", isLocked = false)
        setupMocks(localCaches = listOf(cache), serverSaves = emptyList())

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, entries.size)
        assertEquals(1, slots.size)
        assertEquals("crono", slots[0].channelName)
        assertTrue(slots[0].isLocked)
    }

    @Test
    fun `most recent channel entry is locked older entries unlocked`() = runTest {
        val olderEntry = createLocalCache(id = 1, note = "crono", isLocked = true)
        val newerEntry = createLocalCache(
            id = 2,
            note = "crono",
            isLocked = false,
            cachedAt = Instant.now().plusSeconds(60)
        )
        setupMocks(
            localCaches = listOf(olderEntry, newerEntry),
            serverSaves = emptyList()
        )

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(2, entries.size)
        assertEquals(1, slots.size)
        assertEquals(2L, slots[0].localCacheId)
    }

    @Test
    fun `no duplicate locked entries after repeated game sessions`() = runTest {
        val originalSlot = createLocalCache(
            id = 1,
            note = "crono",
            isLocked = true,
            cachedAt = Instant.parse("2024-01-01T10:00:00Z")
        )
        val session1 = createLocalCache(
            id = 2,
            note = "crono",
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T11:00:00Z")
        )
        val session2 = createLocalCache(
            id = 3,
            note = "crono",
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T12:00:00Z")
        )
        setupMocks(
            localCaches = listOf(originalSlot, session1, session2),
            serverSaves = emptyList()
        )

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, slots.size)
        assertEquals(3L, slots[0].localCacheId)
        assertEquals("crono", slots[0].channelName)
    }

    @Test
    fun `server sync creates locked slot that persists through sessions`() = runTest {
        val serverSyncedSlot = createLocalCache(
            id = 1,
            note = "checkpoint",
            isLocked = true,
            cachedAt = Instant.parse("2024-01-01T10:00:00Z")
        )
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm")
        val sessionSave1 = createLocalCache(
            id = 2,
            note = "checkpoint",
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T11:00:00Z")
        )
        val sessionSave2 = createLocalCache(
            id = 3,
            note = "checkpoint",
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T12:00:00Z")
        )
        setupMocks(
            localCaches = listOf(serverSyncedSlot, sessionSave1, sessionSave2),
            serverSaves = listOf(serverSave)
        )

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, slots.size)
        assertEquals("checkpoint", slots[0].channelName)
    }

    @Test
    fun `entries sorted with latest first then dated then channels`() = runTest {
        val latestCache = createLocalCache(
            id = 1,
            note = null,
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T14:00:00Z")
        )
        val latestServer = createServerSave(id = 10, fileName = "argosy-latest.srm")
        val channelCache = createLocalCache(
            id = 2,
            note = "beta",
            isLocked = true,
            cachedAt = Instant.parse("2024-01-01T12:00:00Z")
        )
        val datedCache = createLocalCache(
            id = 3,
            note = null,
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T11:00:00Z")
        )
        setupMocks(
            localCaches = listOf(latestCache, channelCache, datedCache),
            serverSaves = listOf(latestServer)
        )

        val entries = useCase(gameId)

        assertEquals(3, entries.size)
        assertTrue(entries[0].isLatest)
        assertEquals(1L, entries[0].localCacheId)
        assertEquals(3L, entries[1].localCacheId)
        assertEquals("beta", entries[2].channelName)
    }

    @Test
    fun `timestamp-only server saves are not treated as channels`() = runTest {
        val timestampSave = createServerSave(id = 1, fileName = "2024-01-15_14-30-00.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(timestampSave))

        val entries = useCase(gameId)
        val slots = entries.filter { it.isLocked }

        assertEquals(1, entries.size)
        assertEquals(0, slots.size)
        assertNull(entries[0].channelName)
        assertFalse(entries[0].isLocked)
    }

    @Test
    fun `case insensitive channel matching between local and server`() = runTest {
        val localCache = createLocalCache(id = 1, note = "Checkpoint", isLocked = true)
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm")
        setupMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertEquals(Source.BOTH, entries[0].source)
    }

    @Test
    fun `returns empty list when no saves exist`() = runTest {
        setupMocks(localCaches = emptyList(), serverSaves = emptyList())

        val entries = useCase(gameId)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `handles game without rommId by skipping server check`() = runTest {
        val gameWithoutRomm = testGame.copy(rommId = null)
        val localCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        coEvery { gameDao.getById(gameId) } returns gameWithoutRomm
        coEvery { saveCacheManager.getCachesForGameOnce(gameId) } returns listOf(localCache)

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertEquals(Source.LOCAL, entries[0].source)
    }

    private fun setupMocks(
        localCaches: List<SaveCacheEntity>,
        serverSaves: List<RomMSave>
    ) {
        coEvery { gameDao.getById(gameId) } returns testGame
        coEvery { saveCacheManager.getCachesForGameOnce(gameId) } returns localCaches
        coEvery { saveSyncRepository.checkSavesForGame(gameId, rommId) } returns serverSaves
    }

    private fun createLocalCache(
        id: Long,
        note: String?,
        isLocked: Boolean,
        cachedAt: Instant = Instant.now()
    ) = SaveCacheEntity(
        id = id,
        gameId = gameId,
        emulatorId = "snes9x",
        cachedAt = cachedAt,
        saveSize = 8192,
        cachePath = "$gameId/${cachedAt.epochSecond}/save.srm",
        note = note,
        channelName = note,
        isLocked = isLocked
    )

    private fun createServerSave(
        id: Long,
        fileName: String,
        updatedAt: String = "2024-01-15T12:00:00Z"
    ) = RomMSave(
        id = id,
        romId = rommId,
        userId = 1,
        emulator = "snes9x",
        fileName = fileName,
        fileSizeBytes = 8192,
        updatedAt = updatedAt
    )
}
