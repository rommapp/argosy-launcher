package com.nendo.argosy.data.emulator

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SessionEndNoticesTest {
    private val gameId = 7L
    private val gameTitle = "Synthetic Quest"
    private val syncKey = "sync-$gameId"

    private val gameDao: GameDao = mockk(relaxed = true)
    private val gameArtDao: GameArtDao = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val game: GameEntity = mockk(relaxed = true)

    private val notices = SessionEndNotices(
        application = mockk(relaxed = true),
        gameDao = gameDao,
        gameArtDao = gameArtDao,
        notificationManager = notificationManager
    )

    @Before
    fun setup() {
        every { game.title } returns gameTitle
        coEvery { gameDao.getById(gameId) } returns game
        coEvery { gameArtDao.getForGame(gameId) } returns emptyList()
    }

    private fun synced(sync: SyncSaveOnSessionEndUseCase.Result) =
        SessionSaveOutcome.Synced(SaveCacheManager.CacheResult.Created(timestamp = 1L), sync, conflictId = null)

    private fun verifyToast(titleRes: Int, type: NotificationType, duration: NotificationDuration) {
        verify(exactly = 1) {
            notificationManager.show(
                title = NotificationText.Res(titleRes),
                subtitle = NotificationText.Raw(gameTitle),
                type = type,
                imagePath = null,
                platformSlug = null,
                duration = duration,
                key = syncKey,
                immediate = true,
                accentColor = null
            )
        }
    }

    private fun verifyNoToast() {
        verify(exactly = 0) { notificationManager.show(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an uploaded save at session end toasts save uploaded under the game's sync key`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Uploaded())

        verifyToast(R.string.sync_session_save_uploaded, NotificationType.SUCCESS, NotificationDuration.MEDIUM)
    }

    @Test
    fun `an unchanged save at session end toasts no save changes briefly`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.NoChange)

        verifyToast(R.string.sync_session_save_unchanged, NotificationType.INFO, NotificationDuration.SHORT)
    }

    @Test
    fun `a queued upload at session end toasts that the save will upload later`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Queued)

        verifyToast(R.string.sync_session_save_queued, NotificationType.INFO, NotificationDuration.MEDIUM)
    }

    @Test
    fun `a conflict at session end posts no toast and leaves it to the conflict modal`() = runTest {
        val conflict = SyncSaveOnSessionEndUseCase.Result.Conflict(
            gameId = gameId,
            emulatorId = "test-emulator",
            channelName = null,
            upload = SaveSyncResult.Conflict(gameId, Instant.EPOCH, Instant.EPOCH)
        )

        notices.sessionSaveSynced(gameId, conflict)

        verifyNoToast()
    }

    @Test
    fun `no save found or sync not configured at session end posts no toast`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.NoSaveFound)
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.NotConfigured)

        verifyNoToast()
    }

    @Test
    fun `a session end that cannot find its game toasts upload failed with the reason`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Error("Game not found"))

        verify(exactly = 1) {
            notificationManager.show(
                title = NotificationText.Res(R.string.sync_session_save_upload_failed),
                subtitle = NotificationText.Res(
                    R.string.sync_session_save_upload_failed_subtitle,
                    listOf(gameTitle, "Game not found")
                ),
                type = NotificationType.ERROR,
                imagePath = null,
                platformSlug = null,
                duration = NotificationDuration.MEDIUM,
                key = syncKey,
                immediate = true,
                accentColor = null
            )
        }
    }

    @Test
    fun `a save the session end could not back up toasts that it could not back it up`() = runTest {
        notices.sessionSaveNotKept(gameId)

        verifyToast(R.string.sync_session_save_not_kept, NotificationType.ERROR, NotificationDuration.LONG)
    }

    @Test
    fun `an orphaned save that uploads toasts save uploaded`() = runTest {
        notices.orphanSaveRecovered(gameId, synced(SyncSaveOnSessionEndUseCase.Result.Uploaded()))

        verifyToast(R.string.sync_session_save_uploaded_orphan, NotificationType.SUCCESS, NotificationDuration.MEDIUM)
    }

    @Test
    fun `an orphaned save whose upload is queued toasts that it will upload later`() = runTest {
        notices.orphanSaveRecovered(gameId, synced(SyncSaveOnSessionEndUseCase.Result.Queued))

        verifyToast(R.string.sync_session_save_queued_orphan, NotificationType.INFO, NotificationDuration.MEDIUM)
    }

    @Test
    fun `any other orphan recovery outcome posts no toast`() = runTest {
        listOf(
            SessionSaveOutcome.CacheFailed,
            SessionSaveOutcome.Exempt,
            SessionSaveOutcome.NothingToSave,
            SessionSaveOutcome.Unreadable("/saves/test", "test-emulator"),
            synced(SyncSaveOnSessionEndUseCase.Result.NoChange),
            synced(SyncSaveOnSessionEndUseCase.Result.NoSaveFound),
            synced(SyncSaveOnSessionEndUseCase.Result.Error("Game not found"))
        ).forEach { notices.orphanSaveRecovered(gameId, it) }

        verifyNoToast()
    }

    @Test
    fun `every session-end toast uses the sync key of its game`() = runTest {
        val keys = mutableListOf<String?>()
        every {
            notificationManager.show(any(), any(), any(), any(), any(), any(), captureNullable(keys), any(), any())
        } returns "id"

        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Uploaded())
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.NoChange)
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Queued)
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Error("Game not found"))
        notices.sessionSaveNotKept(gameId)
        notices.orphanSaveRecovered(gameId, synced(SyncSaveOnSessionEndUseCase.Result.Uploaded()))
        notices.orphanSaveRecovered(gameId, synced(SyncSaveOnSessionEndUseCase.Result.Queued))

        assertEquals(List(7) { syncKey }, keys)
    }

    @Test
    fun `a toast that fails to post never throws into the session end`() = runTest {
        every {
            notificationManager.show(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("host gone")

        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Uploaded())
        notices.sessionSaveNotKept(gameId)
        notices.orphanSaveRecovered(gameId, synced(SyncSaveOnSessionEndUseCase.Result.Uploaded()))
    }
}
