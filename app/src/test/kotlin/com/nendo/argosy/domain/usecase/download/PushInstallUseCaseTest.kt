package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PushInstallUseCaseTest {

    private lateinit var romMRepository: RomMRepository
    private lateinit var gameFileDao: GameFileDao
    private lateinit var platformRepository: PlatformRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var builtinPreferences: BuiltinEmulatorPreferencesRepository
    private lateinit var downloadGameUseCase: DownloadGameUseCase
    private lateinit var useCase: PushInstallUseCase
    private val emulator: InstalledEmulator = mockk(relaxed = true)

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        platformRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        emulatorDetector = mockk(relaxed = true)
        builtinPreferences = mockk(relaxed = true)
        downloadGameUseCase = mockk(relaxed = true)
        useCase = PushInstallUseCase(
            romMRepository,
            gameFileDao,
            platformRepository,
            gameRepository,
            emulatorDetector,
            builtinPreferences,
            downloadGameUseCase,
            mockk(relaxed = true)
        )

        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Success(game())
        coEvery { platformRepository.getAllPlatformIds() } returns setOf(PLATFORM_ID)
        coEvery { gameRepository.validateAndDiscoverGame(GAME_ID) } returns false
        every { builtinPreferences.isBuiltinLibretroEnabled() } returns flowOf(true)
        coEvery { emulatorDetector.detectEmulators() } returns emptyList()
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns emulator
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns emptyList()
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.Queued
    }

    @Test
    fun `a game already on disk is already installed and nothing is queued`() = runTest {
        coEvery { gameRepository.validateAndDiscoverGame(GAME_ID) } returns true

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a game not on disk is queued with the default selection`() = runTest {
        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 1) { downloadGameUseCase(GAME_ID, null) }
    }

    @Test
    fun `a download that reports already downloaded is already installed`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.AlreadyDownloaded

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `every disc already downloaded is already installed`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns
            DownloadResult.Error(DownloadGameFailureReason.AllDiscsAlreadyDownloaded)

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `a platform with no emulator after a fresh detection fails without queueing`() = runTest {
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns null

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR),
            useCase(ROM_ID, emptyList())
        )
        coVerify(exactly = 1) { emulatorDetector.detectEmulators() }
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a cached emulator skips detection`() = runTest {
        useCase(ROM_ID, emptyList())

        coVerify(exactly = 0) { emulatorDetector.detectEmulators() }
    }

    @Test
    fun `an emulator installed after the cache was filled is found by re-detecting`() = runTest {
        every { emulatorDetector.getPreferredEmulator("nes", true) } returnsMany listOf(null, emulator)

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 1) { emulatorDetector.detectEmulators() }
    }

    @Test
    fun `the emulator check honours a disabled built-in emulator`() = runTest {
        every { builtinPreferences.isBuiltinLibretroEnabled() } returns flowOf(false)
        every { emulatorDetector.getPreferredEmulator("nes", false) } returns null

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR),
            useCase(ROM_ID, emptyList())
        )
        verify(exactly = 0) { emulatorDetector.getPreferredEmulator(any(), true) }
    }

    @Test
    fun `an android game skips the emulator check`() = runTest {
        val android = game(platformId = LocalPlatformIds.ANDROID, platformSlug = "android")
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Success(android)

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        verify(exactly = 0) { emulatorDetector.getPreferredEmulator(any(), any()) }
    }

    @Test
    fun `media and cheats are dropped while documents ride along`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME),
            file(rommFileId = 11L, category = VariantCategory.SOUNDTRACK),
            file(rommFileId = 12L, category = VariantCategory.SCREENSHOT),
            file(rommFileId = 13L, category = VariantCategory.CHEAT),
            file(rommFileId = 14L, category = VariantCategory.MANUAL),
            file(rommFileId = 15L, category = VariantCategory.WALKTHROUGH),
            file(rommFileId = 16L, category = VariantCategory.PATCH),
            file(rommFileId = 17L, category = VariantCategory.DLC)
        )

        val outcome = useCase(ROM_ID, listOf(10L, 11L, 12L, 13L, 16L, 17L))

        assertEquals(PushInstallOutcome.Queued, outcome)
        coVerify(exactly = 1) { downloadGameUseCase(GAME_ID, listOf(10L, 16L, 17L, 14L, 15L)) }
    }

    @Test
    fun `a request holding only media and cheats fails`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME),
            file(rommFileId = 11L, category = VariantCategory.SOUNDTRACK),
            file(rommFileId = 13L, category = VariantCategory.CHEAT)
        )

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.NO_INSTALLABLE_FILES),
            useCase(ROM_ID, listOf(11L, 13L))
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a rom the server does not know fails unresolved`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Error("not found", code = 404)

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED),
            useCase(ROM_ID, emptyList())
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `an unreachable server fails unresolved`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } throws IOException("offline")

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED),
            useCase(ROM_ID, listOf(10L))
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a platform created by the push is shown before queueing`() = runTest {
        coEvery { platformRepository.getAllPlatformIds() } returns emptySet()
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME)
        )

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, listOf(10L)))
        coVerifyOrder {
            platformRepository.getAllPlatformIds()
            romMRepository.syncSingleRom(ROM_ID)
            platformRepository.updateVisibility(PLATFORM_ID, true)
            downloadGameUseCase(GAME_ID, listOf(10L))
        }
    }

    @Test
    fun `an existing platform keeps the visibility the user gave it`() = runTest {
        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 0) { platformRepository.updateVisibility(any(), any()) }
    }

    @Test
    fun `a multi disc queue counts as queued`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.MultiDiscQueued(2)

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `a download error fails with its detail`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns
            DownloadResult.Error(DownloadGameFailureReason.GameNotSynced)

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, "game not synced"),
            useCase(ROM_ID, emptyList())
        )
    }

    private fun game(
        platformId: Long = PLATFORM_ID,
        platformSlug: String = "nes"
    ) = GameEntity(
        id = GAME_ID,
        platformId = platformId,
        platformSlug = platformSlug,
        title = "Test Game",
        sortTitle = "test game",
        localPath = null,
        rommId = ROM_ID,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun file(
        rommFileId: Long,
        category: VariantCategory,
        size: Long = 100L
    ) = GameFileEntity(
        id = rommFileId,
        gameId = GAME_ID,
        rommFileId = rommFileId,
        romId = ROM_ID,
        fileName = "file$rommFileId.bin",
        filePath = "roms/nes",
        category = category.key,
        fileSize = size
    )

    private companion object {
        const val ROM_ID = 456L
        const val GAME_ID = 123L
        const val PLATFORM_ID = 1L
    }
}
