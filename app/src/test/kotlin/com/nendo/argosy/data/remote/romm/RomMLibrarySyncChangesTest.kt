package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.SiblingGroupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Duration
import java.time.Instant

class RomMLibrarySyncChangesTest {

    private val since = Instant.parse("2026-09-20T12:00:00Z")

    private lateinit var api: RomMApi
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var siblingGroupRepository: SiblingGroupRepository
    private lateinit var overlayWriter: GameUserOverlayWriter
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private lateinit var service: RomMLibrarySyncService

    private val platform = RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 1)

    private fun rom(platformId: Long = 1L) = RomMRom(
        id = 42L,
        platformId = platformId,
        platformSlug = "snes",
        name = "Chrono Trigger",
        slug = "chrono-trigger",
        fileName = "ct.sfc",
        filePath = "/roms/snes/ct.sfc",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null
    )

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        siblingGroupRepository = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        overlayWriter = mockk(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns api
        every { preferences.boxArtCacheEnabled } returns false
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = false)
        every { preferencesRepository.preferences } returns flowOf(preferences)
        every {
            apiClient.buildRomsQueryParams(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { callOriginal() }
        every { apiClient.buildCoverUrls(any()) } returns emptyList()
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildBackgroundUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform))
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
            every { syncEnabled } returns true
        }
        coEvery { overlayWriter.activeOwnerId() } returns null

        service = RomMLibrarySyncService(
            apiClient = apiClient,
            connectionManager = connectionManager,
            userPreferencesRepository = preferencesRepository,
            database = mockk(relaxed = true),
            gameDao = gameDao,
            homeTileDao = mockk(relaxed = true),
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            stateCacheDao = mockk(relaxed = true),
            platformDao = platformDao,
            emulatorConfigDao = mockk(relaxed = true),
            platformLibretroSettingsDao = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            firmwareDao = mockk(relaxed = true),
            controllerMappingDao = mockk(relaxed = true),
            collectionDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true),
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true),
            gameRepository = dagger.Lazy { mockk(relaxed = true) },
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = dagger.Lazy { mockk(relaxed = true) },
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = dagger.Lazy { mockk(relaxed = true) },
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = pendingSyncQueueDao,
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = siblingGroupRepository,
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    @Test
    fun `the changes pass asks for roms changed since the last sync, with an overlap`() = runTest {
        val params = slot<Map<String, String>>()
        coEvery { api.getRoms(capture(params)) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))

        service.syncLibraryChanges(since)

        assertEquals(since.minus(Duration.ofHours(1)).toString(), params.captured["updated_after"])
        assertTrue("platform_ids" !in params.captured)
    }

    @Test
    fun `the changes pass syncs what came back and never marks rows dirty`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        val result = service.syncLibraryChanges(since)

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1, result.gamesAdded + result.gamesUpdated)
        coVerify(exactly = 0) { gameDao.markSyncDirtyForOwner(any(), any(), any()) }
        coVerify(exactly = 0) { gameDao.getSyncDirtyGames(any(), any()) }
    }

    @Test
    fun `the changes pass recomputes sibling visibility after storing the roms`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        service.syncLibraryChanges(since)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            gameDao.insert(any())
            siblingGroupRepository.recomputeAll()
        }
    }

    @Test
    fun `the changes pass writes platform counts after sibling visibility is recomputed`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))
        coEvery { gameDao.countByPlatform(1L, null) } returns 7

        service.syncLibraryChanges(since)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            siblingGroupRepository.recomputeAll()
            platformDao.updateGameCount(1L, 7)
        }
    }

    @Test
    fun `the changes pass recounts every enabled platform, not only those with changed roms`() = runTest {
        val quiet = RomMPlatform(id = 2L, slug = "nes", name = "NES", fsSlug = "nes", romCount = 4)
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform, quiet))
        coEvery { platformDao.getById(2L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "nes"
            every { syncEnabled } returns true
        }
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))
        coEvery { gameDao.countByPlatform(1L, null) } returns 7
        coEvery { gameDao.countByPlatform(2L, null) } returns 3

        service.syncLibraryChanges(since)

        coVerify(exactly = 1) { platformDao.updateGameCount(1L, 7) }
        coVerify(exactly = 1) { platformDao.updateGameCount(2L, 3) }
    }

    @Test
    fun `the changes pass leaves a sync-disabled platform's count alone`() = runTest {
        val disabled = RomMPlatform(id = 2L, slug = "nes", name = "NES", fsSlug = "nes", romCount = 4)
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform, disabled))
        coEvery { platformDao.getById(2L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "nes"
            every { syncEnabled } returns false
        }
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))

        service.syncLibraryChanges(since)

        coVerify(exactly = 0) { platformDao.updateGameCount(2L, any()) }
    }

    @Test
    fun `a complete library pass records the sibling full pass and then writes counts`() = runTest {
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        coEvery { gameDao.countByPlatform(1L, null) } returns 3

        val result = service.syncLibrary()

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        coVerify(exactly = 0) { siblingGroupRepository.recomputeAll() }
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            siblingGroupRepository.completeFullPass()
            platformDao.updateGameCount(1L, 3)
        }
    }

    @Test
    fun `a game realigned to a re-uploaded rom carries its queued uploads to the new rom id`() = runTest {
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        val dirty = GameEntity(
            id = 1L, platformId = 1L, platformSlug = "snes", title = "Chrono Trigger", sortTitle = "chrono trigger",
            localPath = "/roms/snes/ct.sfc", rommId = 42L, rommFileName = "ct.sfc", igdbId = null,
            source = GameSource.ROMM_SYNCED, syncDirty = true
        )
        val successor = dirty.copy(id = 2L, rommId = 77L, localPath = null, syncDirty = false)
        coEvery { gameDao.getSyncDirtyGames(1L, any()) } returns listOf(dirty)
        coEvery { gameDao.getCleanSyncedByFileNameAndPlatformForOwner("ct.sfc", 1L, null) } returns listOf(successor)

        service.syncLibrary()

        coVerify(exactly = 1) { pendingSyncQueueDao.realignRommId(1L, 42L, 77L) }
    }

    @Test
    fun `a library pass with a failed platform leaves the sibling full pass unrecorded`() = runTest {
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        coEvery { api.getRoms(any()) } returns Response.error(500, "".toResponseBody())

        val result = service.syncLibrary()

        assertTrue(result.errors.isNotEmpty())
        coVerify(exactly = 0) { siblingGroupRepository.completeFullPass() }
        coVerify { siblingGroupRepository.recomputeAll() }
    }

    @Test
    fun `a synced row carries its group key and hack flag, and the main sibling goes to the syncing account`() = runTest {
        val hackRom = rom().copy(
            igdbId = 1234L,
            tags = listOf("patched-kaizo"),
            romUser = RomMRomUser(isMainSibling = true)
        )
        coEvery { overlayWriter.activeOwnerId() } returns OWNER
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(hackRom), total = 1))
        val stored = slot<GameEntity>()
        coEvery { gameDao.insert(capture(stored)) } returns 1L
        coEvery { gameDao.getByRommId(42L) } answers { if (stored.isCaptured) stored.captured.copy(id = 9L) else null }

        service.syncLibraryChanges(since)

        assertEquals("igdb-1-1234", stored.captured.siblingGroupKey)
        assertTrue(stored.captured.isHackVariant)
        coVerify(exactly = 1) { overlayWriter.setRommMainSibling(OWNER, 9L, true) }
    }

    @Test
    fun `a payload without rom_user leaves the stored main sibling alone`() = runTest {
        coEvery { overlayWriter.activeOwnerId() } returns OWNER
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        service.syncLibraryChanges(since)

        coVerify(exactly = 0) { overlayWriter.setRommMainSibling(any(), any(), any()) }
    }

    @Test
    fun `a rom on a platform with sync turned off is skipped`() = runTest {
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { syncEnabled } returns false
        }
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        val result = service.syncLibraryChanges(since)

        assertEquals(0, result.gamesAdded + result.gamesUpdated)
    }

    @Test
    fun `a failed fetch reports an error and keeps the last sync time`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.error(500, "".toResponseBody())

        val result = service.syncLibraryChanges(since)

        assertTrue(result.errors.isNotEmpty())
        coVerify(exactly = 0) { preferencesRepository.setLastRommSyncTime(any()) }
    }

    @Test
    fun `a complete library pass records when it started as the last full pass`() = runTest {
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        val before = Instant.now()

        service.syncLibrary()

        val recorded = slot<Instant>()
        coVerify(exactly = 1) { preferencesRepository.setLastRommFullSyncTime(capture(recorded)) }
        assertTrue(!recorded.captured.isBefore(before))
    }

    @Test
    fun `a resumed library pass records the start of its first attempt as the last full pass`() = runTest {
        val generation = Instant.now().minus(Duration.ofMinutes(30))
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns generation
        coEvery { preferencesRepository.getSyncResumeCompletedPlatformIds() } returns setOf(1L)

        service.syncLibrary()

        coVerify(exactly = 1) { preferencesRepository.setLastRommFullSyncTime(generation) }
    }

    @Test
    fun `a library pass with a failed platform leaves the last full pass time alone`() = runTest {
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null
        coEvery { api.getRoms(any()) } returns Response.error(500, "".toResponseBody())

        service.syncLibrary()

        coVerify(exactly = 0) { preferencesRepository.setLastRommFullSyncTime(any()) }
    }

    @Test
    fun `the changes pass never records a full pass time`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        service.syncLibraryChanges(since)

        coVerify(exactly = 0) { preferencesRepository.setLastRommFullSyncTime(any()) }
    }

    @Test
    fun `a clean pass records the time it started`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        val before = Instant.now()

        service.syncLibraryChanges(since)

        val recorded = slot<Instant>()
        coVerify { preferencesRepository.setLastRommSyncTime(capture(recorded)) }
        assertTrue(!recorded.captured.isBefore(before))
    }

    private companion object {
        const val OWNER = 5L
    }
}
