package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import com.nendo.argosy.data.local.entity.StateCacheEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

private const val GAME_ID = 7L
private const val EMULATOR_PACKAGE = "com.retroarch"
private const val EMULATOR_ID = "retroarch"
private const val CORE_ID = "snes9x"

/**
 * Every channel operation moves the save half and the state half together. These are the assertions
 * that catch the half that goes missing; the audit that prompted them found five call sites where
 * the save moved alone.
 */
class SaveChannelStateParityTest {

    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val stateCacheManager: StateCacheManager = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
    private val restoreCachedStates: RestoreCachedStatesUseCase = mockk(relaxed = true)
    private val restoreCachedSave: RestoreCachedSaveUseCase = mockk(relaxed = true)
    private val getUnifiedSaves: GetUnifiedSavesUseCase = mockk(relaxed = true)
    private val contextResolver: SaveChannelContextResolver = mockk()

    private fun context(supportsStates: Boolean = true) = SaveChannelContext(
        gameId = GAME_ID,
        emulatorId = EMULATOR_ID,
        emulatorPackage = EMULATOR_PACKAGE,
        coreId = CORE_ID,
        romPath = "/roms/game.sfc",
        platformSlug = "snes",
        supportsStates = supportsStates
    )

    private fun stubContext(supportsStates: Boolean = true) {
        coEvery { contextResolver.resolve(GAME_ID, any()) } returns context(supportsStates)
    }

    @Test
    fun `activating a channel restores that channel's states`() = runTest {
        stubContext()
        val useCase = ActivateSaveChannelUseCase(
            activeSaveRepository, restoreCachedStates, contextResolver
        )

        useCase(GAME_ID, "speedrun")

        coVerify { activeSaveRepository.activateChannel(GAME_ID, "speedrun") }
        coVerify {
            restoreCachedStates(GAME_ID, "speedrun", EMULATOR_PACKAGE, CORE_ID)
        }
    }

    @Test
    fun `a platform without state support moves the save alone`() = runTest {
        stubContext(supportsStates = false)
        val useCase = ActivateSaveChannelUseCase(
            activeSaveRepository, restoreCachedStates, contextResolver
        )

        useCase(GAME_ID, "speedrun")

        coVerify { activeSaveRepository.activateChannel(GAME_ID, "speedrun") }
        coVerify(exactly = 0) { restoreCachedStates(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `creating a slot clears the previous channel's live states`() = runTest {
        stubContext()
        val useCase = CreateSaveChannelUseCase(
            activeSaveRepository, restoreCachedSave, restoreCachedStates, contextResolver
        )

        useCase(GAME_ID, "fresh")

        coVerify { activeSaveRepository.createChannel(GAME_ID, "fresh") }
        coVerify { restoreCachedStates(GAME_ID, "fresh", EMULATOR_PACKAGE, CORE_ID) }
    }

    @Test
    fun `renaming a channel moves its states to the new name`() = runTest {
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns "old"
        val useCase = RenameSaveChannelUseCase(
            saveCacheManager, stateCacheManager, activeSaveRepository
        )

        useCase(GAME_ID, "old", "new")

        coVerify { saveCacheManager.renameChannel(GAME_ID, "old", "new") }
        coVerify { stateCacheManager.moveStatesToChannel(GAME_ID, "old", "new") }
        coVerify { activeSaveRepository.activateChannel(GAME_ID, "new") }
    }

    @Test
    fun `renaming to the same name changes nothing`() = runTest {
        val useCase = RenameSaveChannelUseCase(
            saveCacheManager, stateCacheManager, activeSaveRepository
        )

        useCase(GAME_ID, "same", "same")

        coVerify(exactly = 0) { saveCacheManager.renameChannel(any(), any(), any()) }
        coVerify(exactly = 0) { stateCacheManager.moveStatesToChannel(any(), any(), any()) }
    }

    @Test
    fun `copying a slot duplicates its states`() = runTest {
        stubContext()
        coEvery { saveCacheManager.copyToChannel(42L, "locked") } returns 99L
        val useCase = CopySaveChannelUseCase(
            saveCacheManager, saveSyncRepository, stateCacheManager
        )

        val copied = useCase(GAME_ID, "primary", "locked", 42L, null, EMULATOR_ID)

        assert(copied)
        coVerify { stateCacheManager.duplicateStatesForChannel(GAME_ID, "primary", "locked") }
    }

    @Test
    fun `a copy that fails does not duplicate states`() = runTest {
        stubContext()
        coEvery { saveCacheManager.copyToChannel(42L, "locked") } returns null
        val useCase = CopySaveChannelUseCase(
            saveCacheManager, saveSyncRepository, stateCacheManager
        )

        val copied = useCase(GAME_ID, "primary", "locked", 42L, null, EMULATOR_ID)

        assert(!copied)
        coVerify(exactly = 0) { stateCacheManager.duplicateStatesForChannel(any(), any(), any()) }
    }

    @Test
    fun `deleting a channel purges its states locally and on the server`() = runTest {
        stubContext()
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns "doomed"
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns emptyList()
        coEvery { stateCacheManager.getStatesForChannel(GAME_ID, "doomed") } returns listOf(
            StateCacheEntity(
                id = 5L,
                gameId = GAME_ID,
                platformSlug = "snes",
                emulatorId = EMULATOR_ID,
                slotNumber = 1,
                channelName = "doomed",
                cachedAt = Instant.EPOCH,
                stateSize = 10L,
                cachePath = "states/doomed/snes9x/slot1.state",
                rommSaveId = 808L
            )
        )
        coEvery { stateCacheManager.purgeState(any(), any(), any()) } just Runs

        val useCase = DeleteSaveChannelUseCase(
            getUnifiedSaves, saveCacheManager, saveSyncRepository,
            stateCacheManager, activeSaveRepository
        )

        useCase(GAME_ID, "doomed")

        coVerify { stateCacheManager.purgeState(GAME_ID, 5L, 808L) }
        coVerify { activeSaveRepository.forgetChannel(GAME_ID, "doomed") }
        coVerify { activeSaveRepository.clearActive(GAME_ID) }
    }

    @Test
    fun `an uninstalled emulator still lets a channel's states be renamed`() = runTest {
        stubContext(supportsStates = false)
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns null
        val useCase = RenameSaveChannelUseCase(
            saveCacheManager, stateCacheManager, activeSaveRepository
        )

        useCase(GAME_ID, "old", "new")

        coVerify { stateCacheManager.moveStatesToChannel(GAME_ID, "old", "new") }
    }

    @Test
    fun `an uninstalled emulator still lets a channel's states be purged`() = runTest {
        stubContext(supportsStates = false)
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns null
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns emptyList()
        coEvery { stateCacheManager.getStatesForChannel(GAME_ID, "doomed") } returns listOf(
            StateCacheEntity(
                id = 5L,
                gameId = GAME_ID,
                platformSlug = "snes",
                emulatorId = EMULATOR_ID,
                slotNumber = 1,
                channelName = "doomed",
                cachedAt = Instant.EPOCH,
                stateSize = 10L,
                cachePath = "states/doomed/snes9x/slot1.state",
                rommSaveId = 808L
            )
        )
        coEvery { stateCacheManager.purgeState(any(), any(), any()) } just Runs

        val useCase = DeleteSaveChannelUseCase(
            getUnifiedSaves, saveCacheManager, saveSyncRepository,
            stateCacheManager, activeSaveRepository
        )

        useCase(GAME_ID, "doomed")

        coVerify { stateCacheManager.purgeState(GAME_ID, 5L, 808L) }
    }

    @Test
    fun `restoring an older point drops the auto-resume state`() = runTest {
        stubContext()
        val useCase = RestoreSaveChannelPointUseCase(
            restoreCachedStates, stateCacheManager, contextResolver
        )

        useCase(GAME_ID, "primary", isLatest = false)

        coVerify {
            restoreCachedStates(GAME_ID, "primary", EMULATOR_PACKAGE, CORE_ID, true)
        }
        coVerify {
            stateCacheManager.deleteAutoResumeStatesFromDisk(
                EMULATOR_ID, "/roms/game.sfc", "snes", EMULATOR_PACKAGE, CORE_ID, GAME_ID
            )
        }
    }

    @Test
    fun `restoring the latest point keeps the auto-resume state`() = runTest {
        stubContext()
        val useCase = RestoreSaveChannelPointUseCase(
            restoreCachedStates, stateCacheManager, contextResolver
        )

        useCase(GAME_ID, "primary", isLatest = true)

        coVerify {
            restoreCachedStates(GAME_ID, "primary", EMULATOR_PACKAGE, CORE_ID, false)
        }
        coVerify(exactly = 0) {
            stateCacheManager.deleteAutoResumeStatesFromDisk(
                any(), any(), any(), any(), any(), any()
            )
        }
    }
}
