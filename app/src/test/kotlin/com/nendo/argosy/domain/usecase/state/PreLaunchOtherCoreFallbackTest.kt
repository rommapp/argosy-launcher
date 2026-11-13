package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMState
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.repository.StateCacheManager.StateCloudResult
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * A slot whose newest server state was written by another core is filled from the slot's next
 * newest state the running core can read, instead of being left empty.
 */
class PreLaunchOtherCoreFallbackTest {

    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>()
    private val gameDao = mockk<GameDao>()
    private val emulatorConfigDao = mockk<EmulatorConfigDao>()
    private val emulatorResolver = mockk<EmulatorResolver>()
    private val preferencesRepository = mockk<UserPreferencesRepository>()
    private val stateSerialResolver = mockk<StateSerialResolver>()
    private val api = mockk<RomMApi>()

    private val newest = serverState(id = 2L, writtenAt = 2_000)
    private val older = serverState(id = 1L, writtenAt = 1_000)

    private lateinit var useCase: PreLaunchStateSyncUseCase

    @Before
    fun setUp() {
        val game = mockk<GameEntity> {
            every { id } returns GAME_ID
            every { title } returns "Super Mario Bros. 3"
            every { rommId } returns ROMM_ID
            every { platformId } returns 3L
            every { platformSlug } returns "nes"
            every { localPath } returns null
        }
        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        coEvery { saveSyncRepository.getApi() } returns api
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { emulatorConfigDao.getByGameId(any()) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        every { emulatorResolver.resolveEmulatorId(any()) } returns EmulatorRegistry.BUILTIN_ID
        coEvery { stateSerialResolver.serialsFor(any(), any()) } returns emptySet()

        coEvery { stateCacheManager.checkServerStates(ROMM_ID, api) } returns listOf(older, newest)
        coEvery { stateCacheManager.getStateTombstones(GAME_ID) } returns emptySet()
        coEvery { stateCacheManager.getByGameAndEmulator(GAME_ID, any()) } returns emptyList()
        coEvery { stateCacheManager.runningCore(GAME_ID, any()) } returns "fceumm"
        every { stateCacheManager.parseStateFileName(any()) } returns
            StateCacheManager.ParsedStateFileName(romBaseName = "smb3", channelName = null, slotNumber = 1)
        every { stateCacheManager.serverStateWrittenAt(any()) } answers {
            Instant.ofEpochSecond(if (firstArg<RomMState>().id == newest.id) 2_000 else 1_000)
        }
        coEvery { stateCacheManager.getByRommSaveId(any()) } returns null

        useCase = PreLaunchStateSyncUseCase(
            stateCacheManager = stateCacheManager,
            saveSyncRepository = saveSyncRepository,
            gameDao = gameDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorResolver = emulatorResolver,
            coreVersionExtractor = mockk(relaxed = true),
            preferencesRepository = preferencesRepository,
            restoreStateUseCase = mockk(relaxed = true),
            stateSerialResolver = stateSerialResolver
        )
    }

    @Test
    fun `newest state from another core falls back to the slot's older readable state`() = runTest {
        downloadReturns(newest, StateCloudResult.OtherCoreFormat("nestopia"))
        downloadReturns(older, StateCloudResult.Success)

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(PreLaunchStateSyncUseCase.Result.Downloaded(1), result)
        coVerify(exactly = 1) { download(older) }
    }

    @Test
    fun `a readable newest state is used without trying the older one`() = runTest {
        downloadReturns(newest, StateCloudResult.Success)

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(PreLaunchStateSyncUseCase.Result.Downloaded(1), result)
        coVerify(exactly = 0) { download(older) }
    }

    @Test
    fun `a slot with no readable state downloads nothing`() = runTest {
        downloadReturns(newest, StateCloudResult.OtherCoreFormat("nestopia"))
        downloadReturns(older, StateCloudResult.OtherCoreFormat("nestopia"))

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(PreLaunchStateSyncUseCase.Result.Ready, result)
    }

    private fun downloadReturns(state: RomMState, result: StateCloudResult) {
        coEvery { download(state) } returns result
    }

    private suspend fun MockKMatcherScope.download(state: RomMState) = stateCacheManager.downloadStateFromRomM(
        rommStateId = state.id,
        fileName = state.fileName,
        api = api,
        gameId = GAME_ID,
        platformSlug = "nes",
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        coreId = any(),
        serverState = state,
        readableByCore = "fceumm"
    )

    private fun serverState(id: Long, writtenAt: Long) = RomMState(
        id = id,
        romId = ROMM_ID,
        userId = 1L,
        fileName = "smb3 [$writtenAt].state1",
        updatedAt = Instant.ofEpochSecond(writtenAt).toString()
    )

    private companion object {
        const val GAME_ID = 9L
        const val ROMM_ID = 90L
    }
}
