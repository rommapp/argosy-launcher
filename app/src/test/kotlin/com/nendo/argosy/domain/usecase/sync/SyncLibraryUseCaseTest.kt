package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.screens.common.LibrarySyncBus
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncLibraryUseCaseTest {

    private lateinit var romMRepository: RomMRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var librarySyncBus: LibrarySyncBus
    private lateinit var useCase: SyncLibraryUseCase

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        librarySyncBus = mockk(relaxed = true)
        useCase = SyncLibraryUseCase(romMRepository, notificationManager, librarySyncBus)
    }

    @Test
    fun `invoke returns error when not connected`() = runTest {
        every { romMRepository.isConnected() } returns false

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals("RomM not connected", (result as SyncLibraryResult.Error).message)
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
    fun `invoke returns error when getLibrarySummary fails`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Error("Network error")

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals("Network error", (result as SyncLibraryResult.Error).message)
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
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(5, 100))
        coEvery { romMRepository.syncLibrary(any()) } returns syncResult

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Success)
        assertEquals(syncResult, (result as SyncLibraryResult.Success).result)
    }

    @Test
    fun `invoke shows persistent notification during sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(3, 50))
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(3, 5, 2, 0, emptyList())

        useCase()

        verify {
            notificationManager.showPersistent(
                title = "Syncing Library",
                subtitle = "Starting...",
                key = "romm-sync",
                progress = NotificationProgress(0, 3)
            )
        }
    }

    @Test
    fun `invoke completes notification with success on successful sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(2, 20))
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(2, 5, 3, 1, emptyList())

        useCase()

        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = "Sync complete",
                subtitle = "5 added, 3 updated, 1 removed",
                type = NotificationType.SUCCESS
            )
        }
    }

    @Test
    fun `invoke completes notification with error when sync has errors`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(2, 20))
        coEvery { romMRepository.syncLibrary(any()) } returns SyncResult(2, 5, 3, 0, listOf("Platform1 failed"))

        useCase()

        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = "Sync completed with errors",
                subtitle = "1 platform(s) failed",
                type = NotificationType.ERROR
            )
        }
    }

    @Test
    fun `invoke reports progress through callback`() = runTest {
        val progressUpdates = mutableListOf<Triple<Int, Int, String>>()
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(2, 20))
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
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(2, 20))
        coEvery { romMRepository.syncLibrary(any()) } coAnswers {
            val callback = firstArg<(Int, Int, String) -> Unit>()
            callback(1, 2, "NES")
            SyncResult(2, 10, 0, 0, emptyList())
        }

        useCase()

        verify {
            notificationManager.updatePersistent(
                key = "romm-sync",
                subtitle = "NES",
                progress = NotificationProgress(1, 2)
            )
        }
    }

    @Test
    fun `invoke handles exception and completes notification with error`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.getLibrarySummary() } returns RomMResult.Success(Pair(2, 20))
        coEvery { romMRepository.syncLibrary(any()) } throws RuntimeException("Unexpected error")

        val result = useCase()

        assertTrue(result is SyncLibraryResult.Error)
        assertEquals("Unexpected error", (result as SyncLibraryResult.Error).message)
        verify {
            notificationManager.completePersistent(
                key = "romm-sync",
                title = "Sync failed",
                subtitle = "Unexpected error",
                type = NotificationType.ERROR
            )
        }
    }
}
