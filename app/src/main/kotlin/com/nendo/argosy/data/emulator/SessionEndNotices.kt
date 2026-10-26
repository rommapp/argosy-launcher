package com.nendo.argosy.data.emulator

import android.app.Application
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The toasts a session end posts about its save. Every one is keyed `sync-<gameId>`, shown
 * immediately, and replaces any earlier toast under that key. Live session ends report through
 * [sessionSaveSynced] and [sessionSaveNotKept]; orphan recovery reports through
 * [orphanSaveRecovered].
 */
@Singleton
class SessionEndNotices @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val notificationManager: NotificationManager
) {
    companion object {
        private const val TAG = "SessionEndNotices"
    }

    suspend fun sessionSaveSynced(gameId: Long, result: SyncSaveOnSessionEndUseCase.Result) {
        val game = gameDao.getById(gameId)
        when (result) {
            is SyncSaveOnSessionEndUseCase.Result.Conflict -> Unit
            is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Sync result: UPLOADED")
                notificationManager.show(
                    title = NotificationText.Res(R.string.sync_session_save_uploaded),
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
                    title = NotificationText.Res(R.string.sync_session_save_unchanged),
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
                    title = NotificationText.Res(R.string.sync_session_save_upload_failed),
                    subtitle = NotificationText.Res(
                        R.string.sync_session_save_upload_failed_subtitle,
                        listOf(
                            game?.title
                                ?: application.getString(R.string.sync_session_save_upload_failed_name_fallback),
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

    suspend fun sessionSaveNotKept(gameId: Long) = Unit

    suspend fun orphanSaveRecovered(gameId: Long, outcome: SessionSaveOutcome) {
        val sync = (outcome as? SessionSaveOutcome.Synced)?.sync
        if (sync is SyncSaveOnSessionEndUseCase.Result.Uploaded) {
            val game = gameDao.getById(gameId)
            notificationManager.show(
                title = NotificationText.Res(R.string.sync_session_save_uploaded_orphan),
                subtitle = game?.title?.let { NotificationText.Raw(it) },
                type = NotificationType.SUCCESS,
                imagePath = coverPathFor(gameId),
                duration = NotificationDuration.MEDIUM,
                key = syncKey(gameId),
                immediate = true
            )
        }
    }

    private fun syncKey(gameId: Long): String = "sync-$gameId"

    private suspend fun coverPathFor(gameId: Long): String? = gameArtDao.resolved(gameId).coverPath
}
