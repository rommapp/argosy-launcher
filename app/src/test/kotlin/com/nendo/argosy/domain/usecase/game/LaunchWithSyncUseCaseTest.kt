package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.SiblingGroupRepository
import com.nendo.argosy.data.sync.CrossEmulatorSaveMigrator
import com.nendo.argosy.domain.model.LaunchFailure
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.failure
import com.nendo.argosy.domain.usecase.state.PreLaunchStateSyncUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.awaitCancellation
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
    private val siblingGroupRepository = mockk<SiblingGroupRepository>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val titleIdDownloadObserver = mockk<TitleIdDownloadObserver>(relaxed = true)
    private val preLaunchStateSyncUseCase = mockk<PreLaunchStateSyncUseCase>(relaxed = true)
    private val stateCacheManager = mockk<com.nendo.argosy.data.repository.StateCacheManager>(relaxed = true)
    private val effectiveLibretroSettingsResolver =
        mockk<com.nendo.argosy.data.preferences.EffectiveLibretroSettingsResolver>(relaxed = true)
    private val migrator = mockk<CrossEmulatorSaveMigrator>(relaxed = true)

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
            gameDao,
            mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            mockk<com.nendo.argosy.domain.usecase.savechannel.ActivateSaveChannelUseCase>(relaxed = true),
            emulatorResolver,
            preferencesRepository, romMRepository, saveSyncRepository,
            titleIdDownloadObserver, preLaunchStateSyncUseCase,
            mockk<com.nendo.argosy.data.sync.N3dsSaveCaseRepair>(relaxed = true),
            mockk<com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase>(relaxed = true),
            siblingGroupRepository,
            stateCacheManager,
            effectiveLibretroSettingsResolver,
            migrator
        )

        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        coEvery { gameDao.getById(gameId) } returns game
        coEvery {
            emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        } returns emulatorPackage
        every { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns emulatorId
        coEvery { romMRepository.isConnected() } returns true
        coEvery { romMRepository.isReachable() } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(SavePathRegistry)
    }

    @Test
    fun `NoConnection result emits Skipped`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.NoConnection

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected to emit Skipped, got $progress", progress.any { it is SyncProgress.Skipped })
    }

    @Test
    fun `a pre-launch sync past its budget launches with local data without reporting a failed connection`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } coAnswers {
            kotlinx.coroutines.delay(60_000)
            PreLaunchSyncResult.NoServerSave
        }

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Skipped, got $progress", progress.last() is SyncProgress.Skipped)
        assertTrue(
            "A timeout is not a connection failure: $progress",
            progress.none { it is SyncProgress.PreLaunch.Connecting && it.success == false }
        )
    }

    @Test
    fun `a sync that gets no answer in time tells the launch the server was slow`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } coAnswers {
            kotlinx.coroutines.delay(60_000)
            PreLaunchSyncResult.NoServerSave
        }

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertEquals(
            listOf(SyncProgress.PreLaunch.TimedOut(null), SyncProgress.Skipped),
            progress.takeLast(2)
        )
        assertEquals(
            LaunchFailure.SERVER_TIMED_OUT,
            progress.mapNotNull { LaunchStep.Save(it).failure }.lastOrNull()
        )
    }

    @Test
    fun `a pre-launch sync past its budget is awaited while the server is answering`() = runTest {
        every { romMRepository.answeredSince(any()) } returns true
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } coAnswers {
            kotlinx.coroutines.delay(60_000)
            PreLaunchSyncResult.NoServerSave
        }

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("A slow transfer finishes before launch: $progress", progress.last() is SyncProgress.PreLaunch.Launching)
        assertTrue(progress.none { it is SyncProgress.Skipped })
    }

    @Test
    fun `NoServerSave result emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.NoServerSave

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
    }

    @Test
    fun `LocalIsNewer result emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.LocalIsNewer

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
    }

    private val serverSaveTime = Instant.parse("2025-01-15T12:00:00Z")

    private fun builtinDownloads(result: SaveSyncResult) {
        every { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns builtinId
        every { SavePathRegistry.canSyncWithSettings(builtinId, any()) } returns true
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, builtinId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.ServerIsNewer(serverSaveTime, "autosave", 42L)
        coEvery { saveSyncRepository.downloadSave(gameId, builtinId, "autosave", knownServerSaveId = 42L) } returns result
    }

    private fun builtinSettings(autoRestore: Boolean, preferServer: Boolean) {
        coEvery { effectiveLibretroSettingsResolver.getEffectiveSettings(game.platformId, game.platformSlug) } returns
            com.nendo.argosy.data.preferences.BuiltinEmulatorSettings(
                autoRestoreState = autoRestore,
                preferNewerServerSave = preferServer
            )
    }

    private val builtinId = com.nendo.argosy.data.emulator.EmulatorRegistry.BUILTIN_ID

    private fun verifyDrop(times: Int) = io.mockk.coVerify(exactly = times) {
        stateCacheManager.deleteAutoResumeStatesOlderThan(
            builtinId, game.localPath!!, game.platformSlug, null, emptySet(), gameId, serverSaveTime
        )
    }

    @Test
    fun `a newer server save drops the built-in auto states written before it`() = runTest {
        builtinDownloads(SaveSyncResult.Success(rommSaveId = 42L, serverTimestamp = serverSaveTime))
        builtinSettings(autoRestore = true, preferServer = true)

        useCase.invokeWithProgress(gameId).toList()

        verifyDrop(times = 1)
    }

    @Test
    fun `turning the setting off keeps the auto state`() = runTest {
        builtinDownloads(SaveSyncResult.Success(rommSaveId = 42L, serverTimestamp = serverSaveTime))
        builtinSettings(autoRestore = true, preferServer = false)

        useCase.invokeWithProgress(gameId).toList()

        verifyDrop(times = 0)
    }

    @Test
    fun `with restore on launch off there is no auto state to drop`() = runTest {
        builtinDownloads(SaveSyncResult.Success(rommSaveId = 42L, serverTimestamp = serverSaveTime))
        builtinSettings(autoRestore = false, preferServer = true)

        useCase.invokeWithProgress(gameId).toList()

        verifyDrop(times = 0)
    }

    @Test
    fun `a download that changed nothing keeps the auto state`() = runTest {
        builtinDownloads(SaveSyncResult.Success(rommSaveId = 42L, serverTimestamp = serverSaveTime, noOp = true))
        builtinSettings(autoRestore = true, preferServer = true)

        useCase.invokeWithProgress(gameId).toList()

        verifyDrop(times = 0)
    }

    @Test
    fun `an external emulator's states are never dropped`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.ServerIsNewer(serverSaveTime, "autosave", 42L)
        coEvery { saveSyncRepository.downloadSave(gameId, emulatorId, "autosave", knownServerSaveId = 42L) } returns
            SaveSyncResult.Success(rommSaveId = 42L, serverTimestamp = serverSaveTime)
        builtinSettings(autoRestore = true, preferServer = true)

        useCase.invokeWithProgress(gameId).toList()

        io.mockk.coVerify(exactly = 0) { stateCacheManager.deleteAutoResumeStatesOlderThan(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ServerIsNewer result downloads and emits Launching`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
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
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
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
        assertTrue("a legacy conflict keeps the two-choice prompt", modified?.snapshotConflict == false)
    }

    @Test
    fun `a snapshot conflict reaches the launch prompt flagged`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.LocalModified(
            localSavePath = "/saves/test.srm",
            serverTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
            channelName = null,
            snapshotConflict = true
        )

        val progress = useCase.invokeWithProgress(gameId).toList()

        val modified = progress.firstOrNull { it is SyncProgress.LocalModified } as? SyncProgress.LocalModified
        assertTrue("Expected a flagged snapshot conflict, got $progress", modified?.snapshotConflict == true)
    }

    @Test
    fun `channelName is forwarded to preLaunchSyncForGame`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1", secureSaves = true)
        } returns PreLaunchSyncResult.LocalIsNewer

        useCase.invokeWithProgress(gameId, channelName = "slot1").toList()

        io.mockk.coVerify {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1", secureSaves = true)
        }
    }

    @Test
    fun `a failing main-sibling refresh leaves the launch result unchanged`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.NoServerSave
        val baseline = useCase.invokeWithProgress(gameId).toList()
        coEvery { siblingGroupRepository.refreshRommMainSibling(gameId) } throws IllegalStateException("offline")

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertEquals(baseline, progress)
        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
        io.mockk.coVerify(timeout = 2_000L) { siblingGroupRepository.refreshRommMainSibling(gameId) }
    }

    @Test
    fun `a main-sibling refresh that never returns does not hold the launch`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.LocalIsNewer
        coEvery { siblingGroupRepository.refreshRommMainSibling(gameId) } coAnswers { awaitCancellation() }

        val progress = useCase.invokeWithProgress(gameId).toList()

        assertTrue("Expected Launching, got $progress", progress.any { it is SyncProgress.PreLaunch.Launching })
        io.mockk.coVerify(timeout = 2_000L) { siblingGroupRepository.refreshRommMainSibling(gameId) }
    }

    @Test
    fun `no main-sibling refresh runs when RomM is not reachable`() = runTest {
        coEvery { romMRepository.isConnected() } returns true
        coEvery { romMRepository.isReachable() } returns false

        useCase.invokeWithProgress(gameId).toList()

        io.mockk.coVerify(exactly = 0) { siblingGroupRepository.refreshRommMainSibling(any()) }
    }

    @Test
    fun `skipPreLaunchSync=true emits Skipped before any sync work`() = runTest {
        val progress = useCase.invokeWithProgress(gameId, skipPreLaunchSync = true).toList()

        assertEquals(listOf(SyncProgress.Skipped), progress)
        io.mockk.coVerify(exactly = 0) {
            saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any(), any())
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
            saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `the cross-emulator migration runs before the pre-launch sync`() = runTest {
        coEvery {
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)
        } returns PreLaunchSyncResult.LocalIsNewer

        useCase.invokeWithProgress(gameId).toList()

        io.mockk.coVerifyOrder {
            migrator.migrateBeforeLaunch(gameId)
            saveSyncRepository.preLaunchSyncForGame(gameId, rommId, emulatorId, null, true)
        }
    }

    @Test
    fun `the cross-emulator migration runs without RomM, a rommId, sync enabled or the pre-launch sync`() = runTest {
        coEvery { romMRepository.isReachable() } returns false
        coEvery { gameDao.getById(gameId) } returns game.copy(rommId = null)

        useCase.invokeWithProgress(gameId).toList()
        coEvery { gameDao.getById(gameId) } returns game
        useCase.invokeWithProgress(gameId).toList()
        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = false))
        useCase.invokeWithProgress(gameId).toList()
        useCase.invokeWithProgress(gameId, skipPreLaunchSync = true).toList()

        io.mockk.coVerify(exactly = 4) { migrator.migrateBeforeLaunch(gameId) }
        io.mockk.coVerify(exactly = 0) { saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any(), any()) }
    }
}
