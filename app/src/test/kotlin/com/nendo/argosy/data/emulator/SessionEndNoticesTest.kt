package com.nendo.argosy.data.emulator

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SessionEndNoticesTest {
    private val gameId = 7L

    private val gameDao: GameDao = mockk(relaxed = true)
    private val gameArtDao: GameArtDao = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)

    private val notices = SessionEndNotices(
        application = mockk(relaxed = true),
        gameDao = gameDao,
        gameArtDao = gameArtDao,
        notificationManager = notificationManager
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(gameId) } returns null
        coEvery { gameArtDao.getForGame(gameId) } returns emptyList()
    }

    @Test
    fun `an uploaded save at session end toasts save uploaded under the game's sync key`() = runTest {
        notices.sessionSaveSynced(gameId, SyncSaveOnSessionEndUseCase.Result.Uploaded())

        verify(exactly = 1) {
            notificationManager.show(
                title = NotificationText.Res(R.string.sync_session_save_uploaded),
                subtitle = null,
                type = NotificationType.SUCCESS,
                imagePath = null,
                platformSlug = null,
                duration = NotificationDuration.MEDIUM,
                key = "sync-$gameId",
                immediate = true,
                accentColor = null
            )
        }
    }
}
