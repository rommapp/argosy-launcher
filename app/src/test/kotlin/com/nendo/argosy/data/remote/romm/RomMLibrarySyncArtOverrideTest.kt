package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
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

class RomMLibrarySyncArtOverrideTest {

    private val romId = 42L
    private val romName = "Chrono Trigger"
    private val coverUrl = "https://romm/covers/42.png"
    private val backgroundUrl = "https://romm/bg/42.jpg"
    private val logoUrl = "https://romm/logo/42.png"

    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var gameArtDao: GameArtDao
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var platformDao: PlatformDao
    private lateinit var overlayWriter: GameUserOverlayWriter
    private lateinit var service: RomMLibrarySyncService

    private fun rom() = RomMRom(
        id = romId,
        platformId = 1L,
        platformSlug = "snes",
        name = romName,
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

    private fun existing() = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "snes",
        title = romName,
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = romId,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    @Before
    fun setup() {
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameArtDao = mockk(relaxed = true)
        imageCacheManager = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        overlayWriter = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns mockk(relaxed = true)
        every { preferences.boxArtCacheEnabled } returns false
        every { preferencesRepository.preferences } returns flowOf(preferences)
        coEvery { apiClient.getRom(romId) } returns RomMResult.Success(rom())
        every { apiClient.buildCoverUrls(any()) } returns listOf(coverUrl)
        every { apiClient.buildLogoUrls(any()) } returns listOf(logoUrl)
        every { apiClient.buildBackgroundUrls(any()) } returns listOf(backgroundUrl)
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
        }
        coEvery { overlayWriter.activeOwnerId() } returns null
        coEvery { gameDao.getByRommId(romId) } returns existing()

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
            imageCacheManager = imageCacheManager,
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true),
            gameRepository = mockk(relaxed = true),
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = mockk(relaxed = true),
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = gameArtDao,
            gameScreenshotDao = mockk(relaxed = true)
        )
    }

    @Test
    fun `a sync writes the first server url of each slot as its source`() = runTest {
        service.syncSingleRom(romId)

        coVerify(exactly = 1) { gameArtDao.setSourceUrl(9L, ArtSlot.COVER, coverUrl) }
        coVerify(exactly = 1) { gameArtDao.setSourceUrl(9L, ArtSlot.BACKGROUND, backgroundUrl) }
        coVerify(exactly = 1) { gameArtDao.setSourceUrl(9L, ArtSlot.LOGO, logoUrl) }
    }

    @Test
    fun `a sync hands each slot to the cache to decide whether it is stale`() = runTest {
        service.syncSingleRom(romId)

        coVerify(exactly = 1) {
            imageCacheManager.queueArtIfStale(9L, ArtSlot.COVER, listOf(coverUrl), romId, null, romName)
        }
        coVerify(exactly = 1) {
            imageCacheManager.queueArtIfStale(9L, ArtSlot.BACKGROUND, listOf(backgroundUrl), romId, null, romName)
        }
    }

    @Test
    fun `a sync never writes an override or a cached path`() = runTest {
        service.syncSingleRom(romId)

        coVerify(exactly = 0) { gameArtDao.setOverride(any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.setCached(any(), any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateCached(any(), any(), any(), any()) }
    }
}
