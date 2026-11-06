package com.nendo.argosy.data.emulator

import android.app.Application
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The toasts a session end posts about its save, each keyed `sync-<gameId>` so it replaces any
 * earlier toast for that game. A notice that fails to post is logged and never thrown, so it
 * cannot change how the session end that called it turns out.
 */
@Singleton
class SessionEndNotices @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val notificationManager: NotificationManager,
    private val copy: SessionEndNoticeCopy
) {
    companion object {
        private const val TAG = "SessionEndNotices"
    }

    suspend fun sessionSaveSynced(gameId: Long, result: SyncSaveOnSessionEndUseCase.Result) = guarded(gameId) {
        val game = gameDao.getById(gameId)
        when (result) {
            is SyncSaveOnSessionEndUseCase.Result.Conflict -> Unit
            is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: UPLOADED")
                notificationManager.show(
                    title = NotificationText.Res(copy.uploaded),
                    subtitle = game?.title?.let { NotificationText.Raw(it) },
                    type = NotificationType.SUCCESS,
                    imagePath = coverPathFor(gameId),
                    duration = NotificationDuration.MEDIUM,
                    key = syncKey(gameId),
                    immediate = true
                )
            }
            is SyncSaveOnSessionEndUseCase.Result.NoChange -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: NO_CHANGE")
                notificationManager.show(
                    title = NotificationText.Res(copy.unchanged),
                    subtitle = game?.title?.let { NotificationText.Raw(it) },
                    type = NotificationType.INFO,
                    imagePath = coverPathFor(gameId),
                    duration = NotificationDuration.SHORT,
                    key = syncKey(gameId),
                    immediate = true
                )
            }
            is SyncSaveOnSessionEndUseCase.Result.Queued -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: QUEUED")
                notificationManager.show(
                    title = NotificationText.Res(copy.queued),
                    subtitle = game?.title?.let { NotificationText.Raw(it) },
                    type = NotificationType.INFO,
                    imagePath = coverPathFor(gameId),
                    duration = NotificationDuration.MEDIUM,
                    key = syncKey(gameId),
                    immediate = true
                )
            }
            is SyncSaveOnSessionEndUseCase.Result.NoSaveFound -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: NO_SAVE_FOUND")
            }
            is SyncSaveOnSessionEndUseCase.Result.NotConfigured -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: NOT_CONFIGURED")
            }
            is SyncSaveOnSessionEndUseCase.Result.Error -> {
                Logger.error(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: ERROR | ${result.message}")
                notificationManager.show(
                    title = NotificationText.Res(copy.uploadFailed),
                    subtitle = NotificationText.Res(
                        copy.uploadFailedSubtitle,
                        listOf(
                            game?.title
                                ?: application.getString(copy.uploadFailedNameFallback),
                            result.message
                        )
                    ),
                    type = NotificationType.ERROR,
                    imagePath = coverPathFor(gameId),
                    duration = NotificationDuration.MEDIUM,
                    key = syncKey(gameId),
                    immediate = true
                )
            }
        }
    }

    suspend fun sessionSaveNotKept(gameId: Long) = guarded(gameId) {
        val game = gameDao.getById(gameId)
        notificationManager.show(
            title = NotificationText.Res(copy.notKept),
            subtitle = game?.title?.let { NotificationText.Raw(it) },
            type = NotificationType.ERROR,
            imagePath = coverPathFor(gameId),
            duration = NotificationDuration.LONG,
            key = syncKey(gameId),
            immediate = true
        )
    }

    suspend fun orphanSaveRecovered(gameId: Long, outcome: SessionSaveOutcome) = guarded(gameId) {
        val (title, type) = when ((outcome as? SessionSaveOutcome.Synced)?.sync) {
            is SyncSaveOnSessionEndUseCase.Result.Uploaded ->
                copy.uploadedOrphan to NotificationType.SUCCESS
            is SyncSaveOnSessionEndUseCase.Result.Queued ->
                copy.queuedOrphan to NotificationType.INFO
            else -> return@guarded
        }
        val game = gameDao.getById(gameId)
        notificationManager.show(
            title = NotificationText.Res(title),
            subtitle = game?.title?.let { NotificationText.Raw(it) },
            type = type,
            imagePath = coverPathFor(gameId),
            duration = NotificationDuration.MEDIUM,
            key = syncKey(gameId),
            immediate = true
        )
    }

    private suspend inline fun guarded(gameId: Long, post: () -> Unit) {
        try {
            post()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] SESSION gameId=$gameId | Could not post the session-end notice", e)
        }
    }

    private fun syncKey(gameId: Long): String = "sync-$gameId"

    private suspend fun coverPathFor(gameId: Long): String? = gameArtDao.resolved(gameId).coverPath
}
