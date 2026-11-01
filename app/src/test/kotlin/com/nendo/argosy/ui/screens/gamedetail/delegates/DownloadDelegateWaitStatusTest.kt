package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.screens.gamedetail.DownloadWaitStatus
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadDelegateWaitStatusTest {
    private val queue = MutableStateFlow(DownloadQueueState())
    private val manager = mockk<DownloadManager> {
        every { state } returns queue
    }
    private val delegate = DownloadDelegate(
        context = mockk(relaxed = true),
        downloadManager = manager,
        gameActions = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        apkInstallManager = mockk(relaxed = true),
        playStoreService = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        filePickerFlow = mockk(relaxed = true),
        artSourceRecorder = mockk(relaxed = true)
    )
    private val progress = DownloadProgress(
        id = 1,
        gameId = 10,
        rommId = 20,
        fileName = "game.zip",
        gameTitle = "Game",
        platformSlug = "snes",
        coverPath = null,
        bytesDownloaded = 25,
        totalBytes = 100,
        state = DownloadState.DOWNLOADING
    )

    @Test
    fun `retry and network waits preserve progress and select menu labels`() = runTest {
        delegate.observeDownloads(backgroundScope, { 10 }) {}
        val cases = listOf(
            Pair(
                DownloadState.RETRYING,
                R.string.gamedetail_menu_play_button_retrying
            ),
            Pair(
                DownloadState.WAITING_FOR_NETWORK,
                R.string.gamedetail_menu_play_button_waiting_for_network
            )
        )
        for ((state, menuLabel) in cases) {
            queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(state = state)))
            runCurrent()

            val actual = delegate.state.value
            assertEquals(GameDownloadStatus.DOWNLOADING, actual.downloadStatus)
            assertEquals(0.25f, actual.downloadProgress, 0f)
            assertEquals(menuLabel, actual.downloadWaitStatus?.menuLabelRes)
        }
    }

    @Test
    fun `a new attempt shows server wait until bytes arrive`() = runTest {
        delegate.observeDownloads(backgroundScope, { 10 }) {}
        queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(state = DownloadState.RETRYING)))
        runCurrent()

        queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(isAwaitingServer = true)))
        runCurrent()
        assertEquals(DownloadWaitStatus.WAITING_FOR_SERVER, delegate.state.value.downloadWaitStatus)
        assertEquals(
            R.string.gamedetail_menu_play_button_waiting_for_server,
            delegate.state.value.downloadWaitStatus?.menuLabelRes
        )

        queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(bytesDownloaded = 50)))
        runCurrent()
        assertNull(delegate.state.value.downloadWaitStatus)
        assertEquals(GameDownloadStatus.DOWNLOADING, delegate.state.value.downloadStatus)
        assertEquals(0.5f, delegate.state.value.downloadProgress, 0f)
    }

    @Test
    fun `completed and failed downloads clear the wait label`() = runTest {
        delegate.observeDownloads(backgroundScope, { 10 }) {}
        for (terminal in listOf(DownloadState.COMPLETED, DownloadState.FAILED)) {
            queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(state = DownloadState.RETRYING)))
            runCurrent()
            queue.value = DownloadQueueState(completed = listOf(progress.copy(state = terminal)))
            runCurrent()

            assertNull(delegate.state.value.downloadWaitStatus)
            assertEquals(
                if (terminal == DownloadState.COMPLETED) GameDownloadStatus.DOWNLOADED else GameDownloadStatus.FAILED,
                delegate.state.value.downloadStatus
            )
        }
    }

    @Test
    fun `reset for a different game clears the previous wait label`() = runTest {
        var gameId = 10L
        delegate.observeDownloads(backgroundScope, { gameId }) {}
        queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(state = DownloadState.WAITING_FOR_NETWORK)))
        runCurrent()
        assertEquals(DownloadWaitStatus.WAITING_FOR_NETWORK, delegate.state.value.downloadWaitStatus)

        gameId = 11L
        delegate.reset()
        assertNull(delegate.state.value.downloadWaitStatus)
        queue.value = queue.value.copy(availableStorageBytes = 100)
        runCurrent()
        assertNull(delegate.state.value.downloadWaitStatus)
        assertEquals(GameDownloadStatus.NOT_DOWNLOADED, delegate.state.value.downloadStatus)
    }

    @Test
    fun `explicit download status updates clear the wait label`() = runTest {
        delegate.observeDownloads(backgroundScope, { 10 }) {}
        queue.value = DownloadQueueState(activeDownloads = listOf(progress.copy(state = DownloadState.RETRYING)))
        runCurrent()

        delegate.updateDownloadStatus(GameDownloadStatus.DOWNLOADED, 1f)

        assertNull(delegate.state.value.downloadWaitStatus)
        assertEquals(GameDownloadStatus.DOWNLOADED, delegate.state.value.downloadStatus)
    }
}
