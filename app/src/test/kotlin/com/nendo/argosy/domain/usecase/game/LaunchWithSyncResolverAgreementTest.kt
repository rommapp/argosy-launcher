package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.SavePathRegistry
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LaunchWithSyncResolverAgreementTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val emulatorDetector = mockk<EmulatorDetector>(relaxed = true) {
        every { getByPackage(any()) } answers { EmulatorRegistry.getByPackage(firstArg()) }
    }
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)

    private val duckstation = EmulatorRegistry.getById("duckstation")!!
    private val retroarch = EmulatorRegistry.getById("retroarch")!!

    private val game = GameEntity(
        id = GAME_ID,
        title = "Ape Escape",
        sortTitle = "ape escape",
        platformId = PLATFORM_ID,
        platformSlug = "psx",
        rommId = ROMM_ID,
        igdbId = null,
        localPath = "/roms/psx/ape.chd",
        source = GameSource.ROMM_SYNCED
    )

    private lateinit var resolver: EmulatorResolver
    private lateinit var useCase: LaunchWithSyncUseCase

    @Before
    fun setUp() {
        mockkObject(SavePathRegistry)
        every { SavePathRegistry.canSyncWithSettings(any(), any()) } returns true

        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { romMRepository.isConnected() } returns true
        coEvery { romMRepository.isReachable() } returns true
        coEvery { romMRepository.awaitReachable(any()) } returns true
        coEvery { emulatorConfigDao.getByGameId(GAME_ID) } returns EmulatorConfigEntity(
            platformId = PLATFORM_ID,
            gameId = GAME_ID,
            packageName = duckstation.packageName,
            displayName = duckstation.displayName,
            coreName = null
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(PLATFORM_ID) } returns null
        val installed = listOf(InstalledEmulator(def = retroarch, versionName = "1.0", versionCode = 1L))
        every { emulatorDetector.installedEmulators } returns MutableStateFlow(installed)
        every { emulatorDetector.getPreferredEmulator("psx", any()) } returns installed.first()
        coEvery { saveSyncRepository.preLaunchSyncForGame(any(), any(), any(), any(), any()) } returns
            PreLaunchSyncResult.NoServerSave

        resolver = EmulatorResolver(
            emulatorDetector = emulatorDetector,
            emulatorConfigDao = emulatorConfigDao,
            userPreferencesRepository = preferencesRepository,
            libretroCoreMgr = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true) { every { isAppInstalled(any()) } returns false }
        )
        useCase = LaunchWithSyncUseCase(
            gameDao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            resolver,
            preferencesRepository,
            romMRepository,
            saveSyncRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkObject(SavePathRegistry)
    }

    @Test
    fun `pre-launch sync targets the emulator the launch resolves when the configured one is not installed`() =
        runTest {
            val launchId = resolver.getEmulatorIdForGame(GAME_ID, PLATFORM_ID, "psx")

            useCase.invokeWithProgress(GAME_ID).toList()

            assertEquals("retroarch", launchId)
            coVerify(exactly = 1) {
                saveSyncRepository.preLaunchSyncForGame(GAME_ID, ROMM_ID, "retroarch", null, true)
            }
            coVerify(exactly = 0) {
                saveSyncRepository.preLaunchSyncForGame(any(), any(), "duckstation", any(), any())
            }
        }

    private companion object {
        const val GAME_ID = 1L
        const val PLATFORM_ID = 10L
        const val ROMM_ID = 100L
    }
}
