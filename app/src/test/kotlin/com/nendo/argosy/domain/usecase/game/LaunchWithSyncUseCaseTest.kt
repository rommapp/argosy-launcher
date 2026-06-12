package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.SyncProgress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LaunchWithSyncUseCaseTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val titleIdDownloadObserver = mockk<TitleIdDownloadObserver>(relaxed = true)

    private lateinit var useCase: LaunchWithSyncUseCase

    private val gameId = 1L
    private val rommId = 100L
    private val emulatorId = "retroarch"
    private val emulatorPackage = "com.retroarch"

    private val game = GameEntity(
        id = gameId,
        title = "Test",
        sortTitle = "test",
        platformId = 7L,
        platformSlug = "gba",
        rommId = rommId,
        igdbId = null,
        localPath = "/roms/test.gba",
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setUp() {
        mockkObject(SavePathRegistry)
        every { SavePathRegistry.canSyncWithSettings(emulatorId, any()) } returns true

        useCase = LaunchWithSyncUseCase(
            gameDao, emulatorConfigDao, emulatorResolver,
            preferencesRepository, romMRepository, saveSyncRepository,
            titleIdDownloadObserver
        )

        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        coEvery { gameDao.getById(gameId) } returns game
        coEvery { emulatorConfigDao.getByGameId(gameId) } returns EmulatorConfigEntity(
            id = 1L,
            platformId = game.platformId,
            gameId = gameId,
            packageName = emulatorPackage,
            displayName = "RetroArch",
            coreName = null,
            isDefault = true
        )
        every { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns emulatorId
        coEvery { romMRepository.isConnected() } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(SavePathRegistry)
    }

    @Test
    fun `NoConnection result emits Skipped`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)
        } returns PreLaunchSyncResult.NoConnection

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected to emit Skipped, got $progress", progress.any { it is SyncProgress.Skipped })
    }

    @Test
    fun `NoServerSave result emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)
        } returns PreLaunchSyncResult.NoServerSave

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
    }

    @Test
    fun `LocalIsNewer result emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)
        } returns PreLaunchSyncResult.LocalIsNewer

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
    }

    @Test
    fun `ServerIsNewer result downloads and emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)
        } returns PreLaunchSyncResult.ServerIsNewer(
            serverTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
            channelName = "autosave",
            serverSaveId = 42L
        )
        coEvery {
            saveSyncRepository.downloadSave(gameId, emulatorId, "autosave", knownServerSaveId = 42L)
        } returns SaveSyncResult.Success()

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Downloading present, got $progress",
            progress.any { it is SyncProgress.PreLaunch.Downloading })
        assertTrue("Expected Launching at the end, got $progress",
            progress.any { it is SyncProgress.PreLaunch.Launching })
    }

    @Test
    fun `LocalModified result emits LocalModified for UI prompt`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)
        } returns PreLaunchSyncResult.LocalModified(
            localSavePath = "/saves/test.srm",
            serverTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
            channelName = "autosave",
            serverSaveId = 99L
        )

        val progress = useCase.invokeWithProgress(gameId).toList()

        val modified = progress.firstOrNull { it is SyncProgress.LocalModified } as? SyncProgress.LocalModified
        assertTrue("Expected LocalModified emission, got $progress", modified != null)
        assertEquals(99L, modified?.serverSaveId)
    }

    @Test
    fun `channelName is forwarded to preLaunchSyncForGame`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1")
        } returns PreLaunchSyncResult.LocalIsNewer

        useCase.invokeWithProgress(gameId, channelName = "slot1").toList()

        io.mockk.coVerify {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1")
        }
    }

    @Test
    fun `skipPreLaunchSync=true emits Skipped before any sync work`() = runTest {
        val progress = useCase.invokeWithProgress(gameId, skipPreLaunchSync = true).toList()

        assertEquals(listOf(SyncProgress.Skipped), progress)
        io.mockk.coVerify(exactly = 0) {
            saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any())
        }
    }

    @Test
    fun `saveSync disabled in prefs emits Skipped before any API call`() = runTest {
        every { preferencesRepository.userPreferences } returns MutableStateFlow(
            UserPreferences(saveSyncEnabled = false)
        )

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertEquals(listOf(SyncProgress.Skipped), progress)
        io.mockk.coVerify(exactly = 0) {
            saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any())
        }
    }
}
