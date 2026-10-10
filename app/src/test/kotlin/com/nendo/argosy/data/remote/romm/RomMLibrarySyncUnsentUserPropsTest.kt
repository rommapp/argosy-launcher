package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameUserOverlayDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.local.entity.UnsentSyncTypeRow
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class RomMLibrarySyncUnsentUserPropsTest {

    private val romId = 42L
    private val gameId = 9L
    private val owner = 7L
    private val otherGameId = 99L
    private val since = Instant.parse("2026-09-20T12:00:00Z")

    private val serverRating = 8
    private val serverDifficulty = 6
    private val serverCompletion = 75
    private val serverStatus = "finished"

    private val propertyTypes = listOf(
        SyncType.RATING,
        SyncType.DIFFICULTY,
        SyncType.COMPLETION,
        SyncType.STATUS
    )

    private lateinit var api: RomMApi
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var overlayDao: GameUserOverlayDao
    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var platformDao: PlatformDao
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var service: RomMLibrarySyncService

    private fun rom(id: Long = romId, platformId: Long = 1L) = RomMRom(
        id = id,
        platformId = platformId,
        platformSlug = "snes",
        name = "Chrono Trigger",
        slug = "chrono-trigger",
        fileName = "ct-$id.sfc",
        filePath = "/roms/snes/ct-$id.sfc",
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
        sha1Hash = null,
        romUser = RomMRomUser(
            rating = serverRating,
            difficulty = serverDifficulty,
            completion = serverCompletion,
            status = serverStatus
        )
    )

    private val stored = GameEntity(
        id = gameId,
        platformId = 1L,
        platformSlug = "snes",
        title = "Chrono Trigger",
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = romId,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        overlayDao = mockk(relaxed = true)
        pendingSyncQueueDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns api
        every { preferences.boxArtCacheEnabled } returns false
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = false)
        every { preferencesRepository.preferences } returns flowOf(preferences)
        every {
            apiClient.buildRomsQueryParams(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { callOriginal() }
        coEvery { api.getPlatforms() } returns Response.success(
            listOf(RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 3))
        )
        coEvery { apiClient.getRom(romId) } returns RomMResult.Success(rom())
        every { apiClient.buildCoverUrls(any()) } returns emptyList()
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildBackgroundUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
            every { syncEnabled } returns true
        }
        coEvery { overlayWriter.activeOwnerId() } returns owner
        coEvery { gameDao.getByRommId(any()) } answers {
            val rommId = firstArg<Long>()
            if (rommId == romId) stored else stored.copy(id = rommId + 1000L, rommId = rommId)
        }
        coEvery { gameDao.insert(any()) } returns gameId

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
            overlayDao = overlayDao,
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = dagger.Lazy { mockk(relaxed = true) },
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = dagger.Lazy { mockk(relaxed = true) },
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = pendingSyncQueueDao,
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    private suspend fun syncWithUnsent(vararg unsent: SyncType) {
        coEvery {
            pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(owner, null)
        } returns unsent.map { UnsentSyncTypeRow(gameId, it) } + UnsentSyncTypeRow(otherGameId, SyncType.RATING)
        service.syncSingleRom(romId)
    }

    private fun verifyServerValueWritten(type: SyncType, times: Int) {
        when (type) {
            SyncType.RATING ->
                coVerify(exactly = times) { overlayDao.setUserRating(owner, gameId, serverRating) }
            SyncType.DIFFICULTY ->
                coVerify(exactly = times) { overlayDao.setUserDifficulty(owner, gameId, serverDifficulty) }
            SyncType.COMPLETION ->
                coVerify(exactly = times) { overlayDao.setCompletion(owner, gameId, serverCompletion) }
            SyncType.STATUS ->
                coVerify(exactly = times) { overlayDao.setStatus(owner, gameId, serverStatus) }
            else -> error("not a user property: $type")
        }
    }

    private suspend fun assertOnlyThisFieldAdopted(adopted: SyncType) {
        val others = propertyTypes - adopted
        syncWithUnsent(*others.toTypedArray())
        verifyServerValueWritten(adopted, times = 1)
        others.forEach { verifyServerValueWritten(it, times = 0) }
    }

    @Test
    fun `an unsent rating survives a library sync`() = runTest {
        syncWithUnsent(SyncType.RATING)
        verifyServerValueWritten(SyncType.RATING, times = 0)
    }

    @Test
    fun `an unsent difficulty survives a library sync`() = runTest {
        syncWithUnsent(SyncType.DIFFICULTY)
        verifyServerValueWritten(SyncType.DIFFICULTY, times = 0)
    }

    @Test
    fun `an unsent completion survives a library sync`() = runTest {
        syncWithUnsent(SyncType.COMPLETION)
        verifyServerValueWritten(SyncType.COMPLETION, times = 0)
    }

    @Test
    fun `an unsent status survives a library sync`() = runTest {
        syncWithUnsent(SyncType.STATUS)
        verifyServerValueWritten(SyncType.STATUS, times = 0)
    }

    @Test
    fun `the server rating is adopted when only other fields are unsent`() = runTest {
        assertOnlyThisFieldAdopted(SyncType.RATING)
    }

    @Test
    fun `the server difficulty is adopted when only other fields are unsent`() = runTest {
        assertOnlyThisFieldAdopted(SyncType.DIFFICULTY)
    }

    @Test
    fun `the server completion is adopted when only other fields are unsent`() = runTest {
        assertOnlyThisFieldAdopted(SyncType.COMPLETION)
    }

    @Test
    fun `the server status is adopted when only other fields are unsent`() = runTest {
        assertOnlyThisFieldAdopted(SyncType.STATUS)
    }

    @Test
    fun `with nothing unsent every server value is adopted`() = runTest {
        syncWithUnsent()
        propertyTypes.forEach { verifyServerValueWritten(it, times = 1) }
    }

    @Test
    fun `a pass over many roms reads the unsent queue once and never per game`() = runTest {
        val roms = listOf(rom(42L), rom(43L), rom(44L))
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = roms, total = roms.size))

        service.syncLibraryChanges(since)

        coVerify(exactly = roms.size) { overlayDao.setUserRating(owner, any(), serverRating) }
        coVerify(exactly = 1) { pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(owner, null) }
        coVerify(exactly = 1) { pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(any(), any()) }
    }

    @Test
    fun `a pass keeps each game's unsent edit from the one batched read`() = runTest {
        val roms = listOf(rom(42L), rom(43L))
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = roms, total = roms.size))
        coEvery { pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(owner, null) } returns
            listOf(UnsentSyncTypeRow(gameId, SyncType.COMPLETION))

        service.syncLibraryChanges(since)

        coVerify(exactly = 0) { overlayDao.setCompletion(owner, gameId, any()) }
        coVerify(exactly = 1) { overlayDao.setCompletion(owner, 43L + 1000L, serverCompletion) }
    }

    @Test
    fun `an edit queued between two platforms of one pass survives on the second platform`() = runTest {
        val firstPlatformGameId = gameId
        val secondPlatformRomId = 50L
        val secondPlatformGameId = secondPlatformRomId + 1000L
        coEvery { api.getPlatforms() } returns Response.success(
            listOf(
                RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 1),
                RomMPlatform(id = 2L, slug = "genesis", name = "Genesis", fsSlug = "genesis", romCount = 1)
            )
        )
        coEvery { platformDao.getById(2L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "genesis"
            every { syncEnabled } returns true
        }
        coEvery { api.getRoms(match { it["platform_ids"] == "1" }) } returns
            Response.success(RomMRomPage(items = listOf(rom(romId, platformId = 1L)), total = 1))
        coEvery { api.getRoms(match { it["platform_ids"] == "2" }) } returns
            Response.success(RomMRomPage(items = listOf(rom(secondPlatformRomId, platformId = 2L)), total = 1))
        coEvery { preferencesRepository.getSyncResumeGeneration() } returns null

        val queue = mutableListOf<UnsentSyncTypeRow>()
        coEvery { pendingSyncQueueDao.getUnsentSyncTypesForOwnerOrUnowned(owner, null) } answers { queue.toList() }
        coEvery { overlayDao.setNowPlaying(owner, firstPlatformGameId, any()) } answers {
            queue += UnsentSyncTypeRow(secondPlatformGameId, SyncType.COMPLETION)
        }

        service.syncLibrary()

        coVerify(exactly = 1) { overlayDao.setCompletion(owner, firstPlatformGameId, serverCompletion) }
        coVerify(exactly = 0) { overlayDao.setCompletion(owner, secondPlatformGameId, any()) }
        coVerify(exactly = 1) { overlayDao.setUserRating(owner, secondPlatformGameId, serverRating) }
    }

    @Test
    fun `unsent rows of other kinds do not hold back user properties`() = runTest {
        syncWithUnsent(SyncType.SAVE_FILE, SyncType.FAVORITE, SyncType.HIDDEN)
        propertyTypes.forEach { verifyServerValueWritten(it, times = 1) }
    }
}
