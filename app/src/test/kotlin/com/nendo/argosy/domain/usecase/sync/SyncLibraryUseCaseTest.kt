package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.SyncProgress
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.data.sync.SyncNotificationCopyResources
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.ui.screens.common.LibrarySyncBus
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncLibraryUseCaseTest {

    private lateinit var romMRepository: RomMRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var librarySyncBus: LibrarySyncBus
    private lateinit var useCase: SyncLibraryUseCase

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        every { romMRepository.syncProgress } returns MutableStateFlow(SyncProgress())
        notificationManager = mockk(relaxed = true)
        librarySyncBus = mockk(relaxed = true)
        useCase = SyncLibraryUseCase(
            romMRepository,
            notificationManager,
            librarySyncBus,
            SyncNotificationCopyResources()
        ).apply {
            progressDispatcher = UnconfinedTestDispatcher()
        }
    }

    @Test
    fun `invoke returns error when not connected`() = runTest {
        every { romMRepository.isConnected() } returns false

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals(SyncLibraryFailureReason.NotConnected, (result as SyncLibraryResult.Error).reason)
    }

    @Test
    fun `invoke initializes repository when initializeFirst is true`() = runTest {
        every { romMRepository.isConnected() } returns false

        useCase(initializeFirst = true)

        coVerify { romMRepository.initialize() }
    }

    @Test
    fun `invoke does not initialize repository when initializeFirst is false`() = runTest {
        every { romMRepository.isConnected() } returns false

        useCase(initializeFirst = false)

        coVerify(exactly = 0) { romMRepository.initialize() }
    }

    @Test
    fun `invoke returns error when platform count fails`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Error("Network error")

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals(
            SyncLibraryFailureReason.PlatformCountFailed("Network error"),
            (result as SyncLibraryResult.Error).reason
        )
    }

    @Test
    fun `invoke calls syncLibrary and returns success`() = runTest {
        val syncResult = SyncResult(
            platformsSynced = 5,
            gamesAdded = 10,
            gamesUpdated = 3,
            gamesDeleted = 0,
            errors = emptyList()
        )
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(5)
        coEvery { romMRepository.syncLibrary(any()) } returns syncResult

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Success)
        assertEquals(syncResult, (result as SyncLibraryResult.Success).result)
    }

    @Test
    fun `invoke shows persistent notification during sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(3)
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(3, 5, 2, 0, emptyList())

        useCase()

        verify {
            notificationManager.showPersistent(
                title = NotificationText.Res(R.string.notif_sync_library_progress_title),
                subtitle = NotificationText.Res(R.string.notif_sync_library_progress_starting),
                key = "romm-sync",
                progress = NotificationProgress(0, 3)
            )
        }
    }

    @Test
    fun `invoke completes notification with success on successful sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(2)
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(2, 5, 3, 1, emptyList())

        useCase()

        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = NotificationText.Res(R.string.notif_sync_library_complete_title),
                subtitle = NotificationText.Res(
                    R.string.notif_sync_library_complete_counts_with_removed,
                    listOf(5, 3, 1)
                ),
                type = NotificationType.SUCCESS
            )
        }
    }

    @Test
    fun `invoke completes notification with error when sync has errors`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(2)
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(2, 5, 3, 0, listOf("Platform1 failed"))

        useCase()

        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = NotificationText.Res(
                    R.string.notif_sync_library_completed_with_errors_title
                ),
                subtitle = NotificationText.Plural(
                    R.plurals.notif_sync_library_failed_platforms,
                    1,
                    listOf(1)
                ),
                type = NotificationType.ERROR
            )
        }
    }

    @Test
    fun `invoke reports progress through callback`() = runTest {
        val progressUpdates = mutableListOf<Triple<Int, Int, String>>()
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(2)
        coEvery { romMRepository.syncLibrary(any()) } coAnswers {
            val callback = firstArg<(Int, Int, String) -> Unit>()
            callback(1, 2, "Platform 1")
            callback(2, 2, "Platform 2")
            SyncResult(2, 10, 0, 0, emptyList())
        }

        useCase { current, total, platform ->
            progressUpdates.add(Triple(current, total, platform))
        }

        assertEquals(2, progressUpdates.size)
        assertEquals(Triple(1, 2, "Platform 1"), progressUpdates[0])
        assertEquals(Triple(2, 2, "Platform 2"), progressUpdates[1])
    }

    @Test
    fun `invoke updates persistent notification on progress`() = runTest {
        val syncProgressFlow = MutableStateFlow(SyncProgress())
        every { romMRepository.syncProgress } returns syncProgressFlow
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(2)
        coEvery { romMRepository.syncLibrary(any()) } coAnswers {
            syncProgressFlow.value = SyncProgress(
                isSyncing = true,
                currentPlatform = "NES",
                platformsTotal = 2,
                platformsDone = 0,
                gamesTotal = 10,
                gamesDone = 3
            )
            kotlinx.coroutines.delay(50)
            SyncResult(2, 10, 0, 0, emptyList())
        }

        useCase()

        verify {
            notificationManager.updatePersistent(
                key = "romm-sync",
                subtitle = NotificationText.Res(
                    R.string.notif_sync_library_progress_platform_games,
                    listOf("NES", 3, 10)
                ),
                progress = NotificationProgress(1, 2)
            )
        }
    }

    @Test
    fun `invoke handles exception and completes notification with error`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getPlatformCount() } returns RomMResult.Success(2)
        coEvery { romMRepository.syncLibrary(any()) } throws RuntimeException("Unexpected error")

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals(
            SyncLibraryFailureReason.Unexpected("Unexpected error"),
            (result as SyncLibraryResult.Error).reason
        )
        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = NotificationText.Res(R.string.notif_sync_library_failed_title),
                subtitle = NotificationText.Raw("Unexpected error"),
                type = NotificationType.ERROR
            )
        }
    }
}
