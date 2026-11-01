package com.nendo.argosy.data.download

import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerPlatformFolderTest {

    @get:Rule
    val storage = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val downloadQueueDao = mockk<DownloadQueueDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private val inserted = slot<DownloadQueueEntity>()
    private lateinit var storageRoot: File
    private lateinit var packagesFolder: File
    private lateinit var manager: DownloadManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storageRoot = storage.newFolder("roms")
        packagesFolder = storage.newFolder("ps3pkg")

        val discs = platform(DISCS_ID, customRomPath = null)
        val packages = platform(PACKAGES_ID, customRomPath = packagesFolder.absolutePath)
        coEvery { platformDao.getById(any()) } returns null
        coEvery { platformDao.getById(DISCS_ID) } returns discs
        coEvery { platformDao.getById(PACKAGES_ID) } returns packages
        coEvery { gameDao.getById(any()) } returns null
        coEvery { gameDao.getById(PACKAGE_GAME) } returns game(PACKAGE_GAME, PACKAGES_ID)
        coEvery { gameDao.getById(DISC_GAME) } returns game(DISC_GAME, DISCS_ID)
        coEvery { downloadQueueDao.getByGameId(any()) } returns null
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns emptyList()
        coEvery { downloadQueueDao.insert(capture(inserted)) } returns 1L

        val romMRepository = mockk<RomMRepository>(relaxed = true)
        every { romMRepository.isConnected() } returns false
        every { romMRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)
        every { preferences.userPreferences } returns flowOf(
            UserPreferences(autoStartDownloads = false, romStoragePath = storageRoot.absolutePath)
        )

        manager = DownloadManager(
            gameDao = gameDao,
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            downloadQueueDao = downloadQueueDao,
            platformDao = platformDao,
            romMRepository = romMRepository,
            preferencesRepository = preferences,
            soundManager = mockk(relaxed = true),
            m3uManager = mockk(relaxed = true),
            thermalManager = dagger.Lazy { mockk(relaxed = true) },
            mediaDownloadManager = dagger.Lazy { mockk(relaxed = true) },
            musicDirectoryManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            homeTileRepository = mockk(relaxed = true),
            homeTilePromptQueue = mockk(relaxed = true),
            customGridShapeStore = mockk(relaxed = true),
            extContentOrganizer = mockk(relaxed = true),
            romStagingManager = mockk(relaxed = true),
            platformFolderResolver = PlatformFolderResolver(mockk(relaxed = true), platformDao, gameDao, preferences),
            scope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a game on the overridden platform downloads into the override`() = runTest {
        enqueue(PACKAGE_GAME)

        assertEquals(File(packagesFolder, "game.pkg.tmp").absolutePath, inserted.captured.tempFilePath)
    }

    @Test
    fun `a game on the same-slug platform without an override downloads into the slug folder`() = runTest {
        enqueue(DISC_GAME)

        assertEquals(File(File(storageRoot, "ps3"), "game.pkg.tmp").absolutePath, inserted.captured.tempFilePath)
    }

    @Test
    fun `a game with no platform row downloads into the slug folder`() = runTest {
        enqueue(ORPHAN_GAME)

        assertEquals(File(File(storageRoot, "ps3"), "game.pkg.tmp").absolutePath, inserted.captured.tempFilePath)
    }

    private suspend fun enqueue(gameId: Long) = manager.enqueueDownload(
        gameId = gameId,
        rommId = gameId + 1000L,
        fileName = "game.pkg",
        gameTitle = "Game",
        platformSlug = "ps3",
        coverPath = null
    )

    private fun platform(id: Long, customRomPath: String?) = PlatformEntity(
        id = id,
        slug = "ps3",
        fsSlug = null,
        name = "ps3",
        shortName = "ps3",
        romExtensions = "",
        customRomPath = customRomPath
    )

    private fun game(id: Long, platformId: Long) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = "ps3",
        title = "Game",
        sortTitle = "game",
        localPath = null,
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private companion object {
        const val DISCS_ID = 1L
        const val PACKAGES_ID = 2L
        const val PACKAGE_GAME = 10L
        const val DISC_GAME = 11L
        const val ORPHAN_GAME = 12L
    }
}
