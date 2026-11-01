package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.download.DownloadEnqueueResult
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayJoinServiceDownloadTest {
    private val downloads = mockk<DownloadManager>()
    private val preflight = mockk<NetplayPreflightChecker>()
    private val games = mockk<GameDao>()
    private val launcher = mockk<LaunchGameUseCase>()
    private val game = GameEntity(
        id = 10L,
        platformId = 1L,
        platformSlug = "snes",
        title = "Synthetic ROM",
        sortTitle = "synthetic rom",
        localPath = null,
        rommId = 20L,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    @Test
    fun conflictFailsWithoutWaitingVerifyingLaunchingOrMutatingDownload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            stubEnqueue(DownloadEnqueueResult.Conflict(99L))
            val service = service()
            service.selectCandidate(game.id)
            runCurrent()

            assertEquals(NetplayJoinState.Failed("Failed to download ROM"), service.state.value)
            verify(exactly = 0) { downloads.state }
            verify { preflight wasNot Called; launcher wasNot Called }
            verifyEnqueue()
            confirmVerified(downloads)
            service.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun acceptedTracksExactQueuedAndActiveIdDespiteSameGameSibling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            stubEnqueue(DownloadEnqueueResult.Accepted(42L))
            val sibling = progress(99L, DownloadState.COMPLETED, 100L)
            val queue = MutableStateFlow(DownloadQueueState(
                activeDownloads = listOf(sibling),
                queue = listOf(progress(42L, DownloadState.QUEUED, 0L))
            ))
            every { downloads.state } returns queue
            val service = service()
            service.selectCandidate(game.id)
            runCurrent()
            assertEquals(null, downloadProgress(service))
            verify { preflight wasNot Called; launcher wasNot Called }

            queue.value = DownloadQueueState(activeDownloads = listOf(
                sibling, progress(42L, DownloadState.DOWNLOADING, 25L)
            ))
            advanceTimeBy(250)
            runCurrent()
            assertEquals(0.25f, downloadProgress(service))
            verify { preflight wasNot Called; launcher wasNot Called }

            coEvery { preflight.verifyGameStep(any(), any()) } returns GameVerifyResult.NeedsDownload(game.id)
            queue.value = DownloadQueueState(
                activeDownloads = listOf(progress(42L, DownloadState.COMPLETED, 100L)),
                queue = listOf(sibling.copy(state = DownloadState.QUEUED))
            )
            advanceTimeBy(250)
            runCurrent()
            coVerify(exactly = 1) { preflight.verifyGameStep(game, any()) }
            assertEquals(NetplayJoinState.Failed("Failed to download ROM"), service.state.value)
            verify { launcher wasNot Called }
            service.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun stubEnqueue(result: DownloadEnqueueResult) {
        coEvery { games.getById(game.id) } returns game
        coEvery {
            downloads.enqueueDownload(
                gameId = game.id, rommId = 20L, fileName = "Synthetic ROM.rom",
                gameTitle = game.title, platformSlug = "snes", coverPath = null,
                expectedSizeBytes = 0L, isMultiFileRom = false, startNow = true
            )
        } returns result
    }

    private fun verifyEnqueue() {
        coVerify(exactly = 1) {
            downloads.enqueueDownload(
                gameId = game.id, rommId = 20L, fileName = "Synthetic ROM.rom",
                gameTitle = game.title, platformSlug = "snes", coverPath = null,
                expectedSizeBytes = 0L, isMultiFileRom = false, startNow = true
            )
        }
    }

    private fun service(): NetplayJoinService {
        val service = NetplayJoinService(
            mockk(), preflight, games, mockk(), mockk(), mockk(), downloads, launcher, mockk()
        )
        val candidate = JoinCandidate(game.id, game.title, "snes", "SNES", null, false, 20L)
        val field = NetplayJoinService::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(service) as MutableStateFlow<NetplayJoinState>
        state.value = NetplayJoinState.VerifyingGame(
            mockk(relaxed = true), mockk(), null, VerifySubState.AmbiguousCandidates(listOf(candidate))
        )
        return service
    }

    private fun downloadProgress(service: NetplayJoinService): Float? =
        ((service.state.value as NetplayJoinState.VerifyingGame).sub as VerifySubState.AmbiguousCandidates)
            .downloadProgress

    private fun progress(id: Long, state: DownloadState, bytes: Long) = DownloadProgress(
        id = id, gameId = game.id, rommId = 20L, fileName = "synthetic.rom",
        gameTitle = game.title, platformSlug = "snes", coverPath = null,
        bytesDownloaded = bytes, totalBytes = 100L, state = state
    )
}
