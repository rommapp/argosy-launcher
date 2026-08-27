package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.data.sync.SyncNotificationCopyResources
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
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
    private lateinit var platformDao: PlatformDao
    private lateinit var notificationManager: NotificationManager
    private lateinit var useCase: SyncPlatformUseCase

    private fun createPlatformEntity(id: Long, slug: String = "gb") = PlatformEntity(
        id = id,
        slug = slug,
        name = "Game Boy",
        shortName = "GB",
        sortOrder = 0,
        isVisible = true,
        logoPath = null,
        romExtensions = "gb,gbc",
        gameCount = 10,
        syncEnabled = true,
        customRomPath = null
    )

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        useCase = SyncPlatformUseCase(
            romMRepository,
            platformDao,
            notificationManager,
            SyncNotificationCopyResources()
        )

        val defaultPlatform = createPlatformEntity(123L)
        coEvery { platformDao.getById(any()) } returns defaultPlatform
    }

    @Test
    fun `invoke returns error when not connected`() = runTest {
        every { romMRepository.isConnected() } returns false

        val result = useCase(123L, "Game Boy")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals(SyncPlatformFailureReason.NotConnected, (result as SyncPlatformResult.Error).reason)
    }

    @Test
    fun `invoke shows persistent notification during sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 2, 0, emptyList())

        useCase(123L, "Game Boy")

        verify {
            notificationManager.showPersistent(
                title = NotificationText.Res(
                    R.string.notif_sync_platform_progress_title,
                    listOf("Game Boy")
                ),
                subtitle = NotificationText.Res(R.string.notif_sync_platform_progress_fetching),
                key = "romm-platform-sync",
                platformSlug = "gb"
            )
        }
    }

    @Test
    fun `invoke calls syncPlatform with numeric platformId`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(123L) } returns SyncResult(1, 5, 2, 0, emptyList())

        val result = useCase(123L, "Game Boy")

        coVerify { romMRepository.syncPlatform(123L) }
        assertTrue(result is SyncPlatformResult.Success)
    }

    @Test
    fun `invoke calls syncPlatform with local platform ID`() = runTest {
        val localPlatform = createPlatformEntity(-1L, "android")
        coEvery { platformDao.getById(-1L) } returns localPlatform
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(-1L) } returns SyncResult(1, 5, 2, 0, emptyList())

        val result = useCase(-1L, "Android")

        coVerify { romMRepository.syncPlatform(-1L) }
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

        val result = useCase(123L, "SNES")

        assertTrue(result is SyncPlatformResult.Success)
        assertEquals(syncResult, (result as SyncPlatformResult.Success).result)
    }

    @Test
    fun `invoke completes notification with success on successful sync`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 3, 1, emptyList())

        useCase(123L, "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = NotificationText.Res(
                    R.string.notif_sync_platform_complete_title,
                    listOf("SNES")
                ),
                subtitle = NotificationText.Res(
                    R.string.notif_sync_platform_complete_counts_with_removed,
                    listOf(5, 3, 1)
                ),
                type = NotificationType.SUCCESS,
                platformSlug = "gb"
            )
        }
    }

    @Test
    fun `invoke completes notification without removed count when zero`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 5, 3, 0, emptyList())

        useCase(123L, "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = NotificationText.Res(
                    R.string.notif_sync_platform_complete_title,
                    listOf("SNES")
                ),
                subtitle = NotificationText.Res(
                    R.string.notif_sync_platform_complete_counts,
                    listOf(5, 3)
                ),
                type = NotificationType.SUCCESS,
                platformSlug = "gb"
            )
        }
    }

    @Test
    fun `invoke completes notification with error when sync has errors`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(0, 0, 0, 0, listOf("Platform not found"))

        useCase(123L, "SNES")

        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = NotificationText.Res(
                    R.string.notif_sync_platform_completed_with_errors_title
                ),
                subtitle = NotificationText.Raw("Platform not found"),
                type = NotificationType.ERROR
            )
        }
    }

    @Test
    fun `invoke dismisses notification when sync already in progress`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns
            SyncResult(0, 0, 0, 0, emptyList(), alreadyInProgress = true)

        val result = useCase(123L, "SNES")

        assertTrue(result is SyncPlatformResult.AlreadyInProgress)
        verify { notificationManager.dismissByKey("romm-platform-sync") }
    }

    @Test
    fun `invoke handles exception and completes notification with error`() = runTest {
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } throws RuntimeException("Network error")

        val result = useCase(123L, "SNES")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals(
            SyncPlatformFailureReason.Unexpected("Network error"),
            (result as SyncPlatformResult.Error).reason
        )
        verify {
            notificationManager.completePersistent(
                key = "romm-platform-sync",
                title = NotificationText.Res(R.string.notif_sync_platform_failed_title),
                subtitle = NotificationText.Raw("Network error"),
                type = NotificationType.ERROR
            )
        }
    }

    @Test
    fun `invoke does not sync sibling platforms sharing canonical slug`() = runTest {
        val mamePlatform = createPlatformEntity(36L, "mame")

        coEvery { platformDao.getById(36L) } returns mamePlatform
        every { romMRepository.isConnected() } returns true
        coEvery { romMRepository.syncPlatform(any()) } returns SyncResult(1, 3, 1, 0, emptyList())

        val result = useCase(36L, "MAME")

        coVerify(exactly = 1) { romMRepository.syncPlatform(any()) }
        coVerify { romMRepository.syncPlatform(36L) }
        assertTrue(result is SyncPlatformResult.Success)
        assertEquals(3, (result as SyncPlatformResult.Success).result.gamesAdded)
    }

    @Test
    fun `invoke returns error when platform not found`() = runTest {
        coEvery { platformDao.getById(999L) } returns null
        every { romMRepository.isConnected() } returns true

        val result = useCase(999L, "Unknown")

        assertTrue(result is SyncPlatformResult.Error)
        assertEquals(SyncPlatformFailureReason.PlatformNotFound, (result as SyncPlatformResult.Error).reason)
    }
}
