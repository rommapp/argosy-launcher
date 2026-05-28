package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry.Source
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `server-only slotted save gets isLocked true`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "checkpoint.srm", slot = "checkpoint")
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
    fun `server-only argosy-latest slot gets isLocked false`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "argosy-latest.srm", slot = "argosy-latest")
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
    fun `server save with rom base name slot is latest`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "$romBaseName.srm", slot = "argosy-latest")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertFalse(entries[0].isLocked)
        assertNull(entries[0].channelName)
        assertTrue(entries[0].isLatest)
    }

    @Test
    fun `null-slot named server save lands in Archived bucket`() = runTest {
        val serverSave = createServerSave(id = 1, fileName = "checkpoint.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val collapsed = useCase(gameId, expandHistory = false)
        val expanded = useCase(gameId, expandHistory = true)

        assertTrue(collapsed.isEmpty())
        assertEquals(1, expanded.size)
        assertTrue(expanded[0].isArchival)
        assertFalse(expanded[0].isLatest)
        assertNull(expanded[0].channelName)
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
        val serverOnly = createServerSave(id = 10, fileName = "quicksave.srm", slot = "quicksave")
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
        val backup = createServerSave(id = 10, fileName = "backup.srm", slot = "backup")
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
            note = "autosave",
            isLocked = false,
            cachedAt = Instant.parse("2024-01-01T14:00:00Z")
        )
        val latestServer = createServerSave(
            id = 10,
            fileName = "argosy-latest.srm",
            slot = "autosave"
        )
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
    fun `timestamp-only server save becomes archival`() = runTest {
        val timestampSave = createServerSave(id = 1, fileName = "2024-01-15_14-30-00.srm")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(timestampSave))

        val entries = useCase(gameId, expandHistory = true)

        assertEquals(1, entries.size)
        assertTrue(entries[0].isArchival)
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

    // --- Unicode merging tests ---

    private val accentGameId = 2L
    private val accentRommId = 200L
    private val accentRomBaseName = "Pokemon Violet"
    private val accentLocalPath = "/storage/roms/switch/$accentRomBaseName.nsp"
    private val accentGame = GameEntity(
        id = accentGameId,
        platformId = 2L,
        title = "Pokemon Violet",
        sortTitle = "pokemon violet",
        localPath = accentLocalPath,
        rommId = accentRommId,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun setupAccentMocks(
        localCaches: List<SaveCacheEntity>,
        serverSaves: List<RomMSave>
    ) {
        coEvery { gameDao.getById(accentGameId) } returns accentGame
        coEvery { saveCacheManager.getCachesForGameOnce(accentGameId) } returns localCaches
        coEvery { saveSyncRepository.checkSavesForGame(accentGameId, accentRommId) } returns serverSaves
    }

    @Test
    fun `server save with accented filename and autosave slot is latest`() = runTest {
        val serverSave = createAccentServerSave(
            id = 1,
            fileName = "Pok\u00e9mon Violet.srm",
            slot = "argosy-latest"
        )
        setupAccentMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(accentGameId)

        assertEquals(1, entries.size)
        assertTrue(entries[0].isLatest)
        assertNull(entries[0].channelName)
        assertFalse(entries[0].isLocked)
    }

    @Test
    fun `accented server save merges with local latest cache as BOTH`() = runTest {
        val localCache = createAccentLocalCache(id = 1, note = null, isLocked = false)
        val serverSave = createAccentServerSave(id = 10, fileName = "Pok\u00e9mon Violet.srm")
        setupAccentMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(accentGameId)

        assertEquals(1, entries.size)
        assertEquals(Source.BOTH, entries[0].source)
        assertNull(entries[0].channelName)
    }

    @Test
    fun `accented server slot matches local channel name`() = runTest {
        val localCache = createAccentLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val serverSave = createAccentServerSave(
            id = 10,
            fileName = "Ch\u00e9ckpoint.srm",
            slot = "Ch\u00e9ckpoint"
        )
        setupAccentMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(accentGameId)

        assertEquals(1, entries.size)
        assertEquals(Source.BOTH, entries[0].source)
    }

    @Test
    fun `mixed accented latest and plain channel saves correctly categorized`() = runTest {
        val latestSave = createAccentServerSave(
            id = 1,
            fileName = "Pok\u00e9mon Violet.srm",
            slot = "argosy-latest"
        )
        val channelSave = createAccentServerSave(
            id = 2,
            fileName = "checkpoint.srm",
            slot = "checkpoint"
        )
        setupAccentMocks(localCaches = emptyList(), serverSaves = listOf(latestSave, channelSave))

        val entries = useCase(accentGameId)

        assertEquals(2, entries.size)
        val latest = entries.find { it.isLatest }
        val channel = entries.find { it.channelName == "checkpoint" }
        assertNotNull(latest)
        assertNull(latest!!.channelName)
        assertNotNull(channel)
        assertTrue(channel!!.isLocked)
    }

    @Test
    fun `accented filename with RomM timestamp tag and no slot is archival`() = runTest {
        val serverSave = createAccentServerSave(
            id = 1,
            fileName = "Pok\u00e9mon Violet [2024-01-15 12-00-00].srm"
        )
        setupAccentMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(accentGameId, expandHistory = true)

        assertEquals(1, entries.size)
        assertTrue(entries[0].isArchival)
        assertFalse(entries[0].isLatest)
        assertNull(entries[0].channelName)
    }

    // --- Device sync display tests ---

    @Test
    fun `server-only save with device is_current true shows isCurrent`() = runTest {
        coEvery { saveSyncRepository.getDeviceId() } returns "device-1"
        val serverSave = createServerSave(id = 1, fileName = "checkpoint.srm", slot = "checkpoint").copy(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        )
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertTrue(entries[0].isCurrent)
    }

    @Test
    fun `server-only save with device is_current false shows not current`() = runTest {
        coEvery { saveSyncRepository.getDeviceId() } returns "device-1"
        val serverSave = createServerSave(id = 1, fileName = "checkpoint.srm", slot = "checkpoint").copy(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        setupMocks(localCaches = emptyList(), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertFalse(entries[0].isCurrent)
    }

    @Test
    fun `merged BOTH entry preserves device sync isCurrent`() = runTest {
        coEvery { saveSyncRepository.getDeviceId() } returns "device-1"
        val localCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm").copy(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        )
        setupMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        assertEquals(1, entries.size)
        assertEquals(Source.BOTH, entries[0].source)
        assertTrue(entries[0].isCurrent)
    }

    // --- rommSaveId matching priority tests ---

    @Test
    fun `cache with rommSaveId matches server by ID not name`() = runTest {
        val localCache = createLocalCache(id = 1, note = "old-name", isLocked = true).copy(
            rommSaveId = 10L
        )
        val serverSave = createServerSave(id = 10, fileName = "new-name.srm")
        setupMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        val bothEntries = entries.filter { it.source == Source.BOTH }
        assertEquals(1, bothEntries.size)
        assertEquals(1L, bothEntries[0].localCacheId)
        assertEquals(10L, bothEntries[0].serverSaveId)
    }

    @Test
    fun `cache without rommSaveId falls back to name matching`() = runTest {
        val localCache = createLocalCache(id = 1, note = "checkpoint", isLocked = true)
        val serverSave = createServerSave(id = 10, fileName = "checkpoint.srm")
        setupMocks(localCaches = listOf(localCache), serverSaves = listOf(serverSave))

        val entries = useCase(gameId)

        val bothEntries = entries.filter { it.source == Source.BOTH }
        assertEquals(1, bothEntries.size)
        assertEquals(1L, bothEntries[0].localCacheId)
        assertEquals(10L, bothEntries[0].serverSaveId)
    }

    // --- expandHistory tests ---

    @Test
    fun `expandHistory true surfaces every null-slot save as archival`() = runTest {
        val archivalSaves = (0 until 7).map { i ->
            createServerSave(
                id = (100 + i).toLong(),
                fileName = "$romBaseName [2026-05-${10 + i} 12-00-00].srm",
                updatedAt = "2026-05-${10 + i}T12:00:00Z"
            )
        }
        setupMocks(localCaches = emptyList(), serverSaves = archivalSaves)

        val entries = useCase(gameId, expandHistory = true)

        assertEquals(7, entries.size)
        assertTrue(entries.all { it.isArchival })
        assertTrue(entries.none { it.isLatest })
        assertTrue(entries.all { it.channelName == null })
        assertEquals(archivalSaves.map { it.id }.toSet(), entries.mapNotNull { it.serverSaveId }.toSet())
    }

    @Test
    fun `expandHistory false drops null-slot archival saves entirely`() = runTest {
        val archivalSaves = (0 until 3).map { i ->
            createServerSave(
                id = (100 + i).toLong(),
                fileName = "$romBaseName [2026-05-${10 + i} 12-00-00].srm",
                updatedAt = "2026-05-${10 + i}T12:00:00Z"
            )
        }
        setupMocks(localCaches = emptyList(), serverSaves = archivalSaves)

        val entries = useCase(gameId, expandHistory = false)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `expandHistory true preserves null-slot save shadowed by slotted version`() = runTest {
        val legacy = createServerSave(id = 1, fileName = "checkpoint.srm")
        val slotted = createServerSave(id = 2, fileName = "checkpoint.srm").copy(slot = "checkpoint")
        setupMocks(localCaches = emptyList(), serverSaves = listOf(legacy, slotted))

        val expanded = useCase(gameId, expandHistory = true)

        assertEquals(2, expanded.size)
        val archival = expanded.single { it.isArchival }
        val slot = expanded.single { !it.isArchival }
        assertEquals(1L, archival.serverSaveId)
        assertEquals(2L, slot.serverSaveId)
        assertEquals("checkpoint", slot.channelName)
    }

    // --- helpers ---

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
        updatedAt: String = "2024-01-15T12:00:00Z",
        slot: String? = null
    ) = RomMSave(
        id = id,
        romId = rommId,
        userId = 1,
        emulator = "snes9x",
        fileName = fileName,
        fileSizeBytes = 8192,
        updatedAt = updatedAt,
        slot = slot
    )

    private fun createAccentLocalCache(
        id: Long,
        note: String?,
        isLocked: Boolean,
        cachedAt: Instant = Instant.now()
    ) = SaveCacheEntity(
        id = id,
        gameId = accentGameId,
        emulatorId = "yuzu",
        cachedAt = cachedAt,
        saveSize = 65536,
        cachePath = "$accentGameId/${cachedAt.epochSecond}/save.srm",
        note = note,
        channelName = note,
        isLocked = isLocked
    )

    private fun createAccentServerSave(
        id: Long,
        fileName: String,
        updatedAt: String = "2024-01-15T12:00:00Z",
        slot: String? = null
    ) = RomMSave(
        id = id,
        romId = accentRommId,
        userId = 1,
        emulator = "yuzu",
        fileName = fileName,
        fileSizeBytes = 65536,
        updatedAt = updatedAt,
        slot = slot
    )
}
