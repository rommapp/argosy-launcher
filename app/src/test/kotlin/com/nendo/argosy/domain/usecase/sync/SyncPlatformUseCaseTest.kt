package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPlatformUseCaseTest {

    private lateinit var romMRepository: RomMRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var useCase: SyncPlatformUseCase

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        useCase = SyncPlatformUseCase(romMRepository, notificationManager)
    }

    @Test
    fun `invoke returns error when not connected`() = runTest {
        every { romMRepository.isConnected() } returns false

        val result = useCase("123", "Game Boy")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals("RomM not connected", (result as SyncPlatformResult.Error).message)
    }

    @Test
    fun `invoke shows persistent notification during sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 2, 0, emptyList())

        useCase("123", "Game Boy")

        verify {
            notificationManager.showPersistent(
                title = "Syncing Game Boy",
                subtitle = "Fetching games...",
                key = "romm-platform-sync"
            )
        }
    }

    @Test
    fun `invoke calls syncPlatform with numeric platformId`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform("123") } returns SyncResult(1, 5, 2, 0, emptyList())

        val result = useCase("123", "Game Boy")

        coVerify { romMRepository.syncPlatform("123") }
        assertTrue(result is SyncPlatformResult.Success)
    }

    @Test
    fun `invoke calls syncPlatform with slug-based platformId`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform("gb") } returns SyncResult(1, 5, 2, 0, emptyList())

        val result = useCase("gb", "Game Boy")

        coVerify { romMRepository.syncPlatform("gb") }
        assertTrue(result is SyncPlatformResult.Success)
    }

    @Test
    fun `invoke returns success with sync result`() = runTest {
        val syncResult = SyncResult(
            platformsSynced = 1,
            gamesAdded = 10,
            gamesUpdated = 3,
            gamesDeleted = 2,
            errors = emptyList()
        )
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns syncResult

        val result = useCase("123", "SNES")

        assertTrue(result is SyncPlatformResult.Success)
        assertEquals(syncResult, (result as SyncPlatformResult.Success).result)
    }

    @Test
    fun `invoke completes notification with success on successful sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 3, 1, emptyList())

        useCase("123", "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = "SNES synced",
                subtitle = "5 added, 3 updated, 1 removed",
                type = NotificationType.SUCCESS
            )
        }
    }

    @Test
    fun `invoke completes notification without removed count when zero`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 3, 0, emptyList())

        useCase("123", "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = "SNES synced",
                subtitle = "5 added, 3 updated",
                type = NotificationType.SUCCESS
            )
        }
    }

    @Test
    fun `invoke completes notification with error when sync has errors`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(0, 0, 0, 0, listOf("Platform not found"))

        useCase("123", "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = "Sync completed with errors",
                subtitle = "Platform not found",
                type = NotificationType.ERROR
            )
        }
    }

    @Test
    fun `invoke dismisses notification when sync already in progress`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(0, 0, 0, 0, listOf("Sync already in progress"))

        val result = useCase("123", "SNES")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals("Sync already in progress", (result as SyncPlatformResult.Error).message)
        verify { notificationManager.dismissByKey("romm-platform-sync") }
    }

    @Test
    fun `invoke handles exception and completes notification with error`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } throws RuntimeException("Network error")

        val result = useCase("123", "SNES")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals("Network error", (result as SyncPlatformResult.Error).message)
        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = "Sync failed",
                subtitle = "Network error",
                type = NotificationType.ERROR
            )
        }
    }
}
