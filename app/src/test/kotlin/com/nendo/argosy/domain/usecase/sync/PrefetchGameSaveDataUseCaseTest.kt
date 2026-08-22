package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

private const val GAME_ID = 3L
private const val ROMM_ID = 900L

class PrefetchGameSaveDataUseCaseTest {

    private val gameDao: GameDao = mockk(relaxed = true)
    private val gameFileDao: GameFileDao = mockk(relaxed = true)
    private val gameDiscDao: GameDiscDao = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val getUnifiedSaves: GetUnifiedSavesUseCase = mockk(relaxed = true)
    private val getUnifiedStates: GetUnifiedStatesUseCase = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)

    private fun useCase() = PrefetchGameSaveDataUseCase(
        gameDao, gameFileDao, gameDiscDao, preferencesRepository, activeSaveRepository,
        getUnifiedSaves, getUnifiedStates, saveSyncRepository
    )

    private fun game(rommId: Long? = ROMM_ID, localPath: String? = "/roms/test.sfc") = GameEntity(
        id = GAME_ID,
        title = "Test",
        sortTitle = "test",
        platformId = 1L,
        platformSlug = "snes",
        rommId = rommId,
        igdbId = null,
        localPath = localPath,
        source = GameSource.ROMM_SYNCED
    )

    private fun serverEntry(id: Long) = mockk<UnifiedSaveEntry>(relaxed = true).also {
        every { it.source } returns UnifiedSaveEntry.Source.SERVER
        every { it.serverSaveId } returns id
        every { it.channelName } returns "primary"
        every { it.timestamp } returns Instant.EPOCH
    }

    private fun arrange(
        syncEnabled: Boolean = true,
        rommId: Long? = ROMM_ID,
        localPath: String? = "/roms/test.sfc",
        downloadedFiles: Int = 0,
        downloadedDiscs: Int = 0
    ) {
        every { preferencesRepository.userPreferences } returns
            MutableStateFlow(UserPreferences(saveSyncEnabled = syncEnabled))
        coEvery { gameDao.getById(GAME_ID) } returns game(rommId, localPath)
        coEvery { gameFileDao.getDownloadedCount(GAME_ID) } returns downloadedFiles
        coEvery { gameDiscDao.getDownloadedDiscCount(GAME_ID) } returns downloadedDiscs
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns "primary"
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns emptyList()
    }

    @Test
    fun `server-only saves are pulled into the cache`() = runTest {
        arrange()
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns listOf(serverEntry(77L))

        useCase()(GAME_ID)

        coVerify { saveSyncRepository.downloadAndCacheSave(77L, GAME_ID, "primary") }
        coVerify { getUnifiedStates(GAME_ID, any(), "primary", any(), any()) }
    }

    @Test
    fun `a second open inside the window does no work`() = runTest {
        arrange()
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns listOf(serverEntry(77L))
        val useCase = useCase()

        useCase(GAME_ID)
        useCase(GAME_ID)

        coVerify(exactly = 1) { saveSyncRepository.downloadAndCacheSave(77L, GAME_ID, "primary") }
    }

    @Test
    fun `states are still prefetched when the save half throws`() = runTest {
        arrange()
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } throws IllegalStateException("server down")

        useCase()(GAME_ID)

        coVerify { getUnifiedStates(GAME_ID, any(), "primary", any(), any()) }
    }

    @Test
    fun `sync disabled prefetches nothing`() = runTest {
        arrange(syncEnabled = false)

        useCase()(GAME_ID)

        coVerify(exactly = 0) { saveSyncRepository.downloadAndCacheSave(any(), any(), any()) }
        coVerify(exactly = 0) { getUnifiedStates(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a game with no server identity prefetches nothing`() = runTest {
        arrange(rommId = null)

        useCase()(GAME_ID)

        coVerify(exactly = 0) { saveSyncRepository.downloadAndCacheSave(any(), any(), any()) }
        coVerify(exactly = 0) { getUnifiedStates(any(), any(), any(), any(), any()) }
    }

    /**
     * A preserved orphan keeps a synthetic negative id, so a present-but-negative rom id is not a
     * server identity and asking about it can only fail.
     */
    @Test
    fun `a preserved orphan prefetches nothing`() = runTest {
        arrange(rommId = -GAME_ID)
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns listOf(serverEntry(77L))

        useCase()(GAME_ID)

        coVerify(exactly = 0) { saveSyncRepository.downloadAndCacheSave(any(), any(), any()) }
        coVerify(exactly = 0) { getUnifiedStates(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a game that is not downloaded prefetches nothing`() = runTest {
        arrange(localPath = null)
        coEvery { getUnifiedSaves(GAME_ID, true, any()) } returns listOf(serverEntry(77L))

        useCase()(GAME_ID)

        coVerify(exactly = 0) { saveSyncRepository.downloadAndCacheSave(any(), any(), any()) }
        coVerify(exactly = 0) { getUnifiedStates(any(), any(), any(), any(), any()) }
    }

    /**
     * A multi-file or multi-disc game carries its content in `game_files` and `game_discs` rather
     * than in the row's own path, so an empty [GameEntity.localPath] is not evidence of absence.
     */
    @Test
    fun `a multi-disc game with downloaded discs still prefetches`() = runTest {
        arrange(localPath = null, downloadedDiscs = 2)

        useCase()(GAME_ID)

        coVerify { getUnifiedStates(GAME_ID, any(), "primary", any(), any()) }
    }

    @Test
    fun `a multi-file game with downloaded files still prefetches`() = runTest {
        arrange(localPath = null, downloadedFiles = 3)

        useCase()(GAME_ID)

        coVerify { getUnifiedStates(GAME_ID, any(), "primary", any(), any()) }
    }
}
