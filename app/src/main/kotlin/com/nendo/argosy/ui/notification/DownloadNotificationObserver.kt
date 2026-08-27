package com.nendo.argosy.core.notification

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.common.toNotificationText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager
) {
    private var previousState: DownloadQueueState? = null
    private var isInitialLoad = true

    fun observe(scope: CoroutineScope) {
        scope.launch {
            downloadManager.state
                .map { it.toNotificationState() }
                .distinctUntilChanged()
                .collect {
                    val previous = previousState
                    val current = downloadManager.state.value
                    previousState = current

                    if (previous == null) {
                        isInitialLoad = true
                        return@collect
                    }

                    detectStateChanges(previous, current)
                    isInitialLoad = false
                }
        }
    }

    private fun detectStateChanges(previous: DownloadQueueState, current: DownloadQueueState) {
        val previousGameIds = previous.allGameIds()
        val currentGameIds = current.allGameIds()

        for (gameId in currentGameIds) {
            val prevStatus = previous.statusFor(gameId)
            val currStatus = current.statusFor(gameId)

            if (prevStatus?.state != currStatus?.state && currStatus != null) {
                showTransientNotification(currStatus)
            }
        }

        for (gameId in previousGameIds - currentGameIds) {
            val prevStatus = previous.statusFor(gameId)
            if (prevStatus?.state == DownloadState.DOWNLOADING) {
                notificationManager.dismissByKey("download-$gameId")
            }
        }
    }

    private fun showTransientNotification(progress: DownloadProgress) {
        if (isInitialLoad && progress.state != DownloadState.COMPLETED && progress.state != DownloadState.FAILED) {
            return
        }

        val (titleRes, type, immediate) = when (progress.state) {
            DownloadState.QUEUED ->
                Triple(R.string.ui_download_notice_queued, NotificationType.INFO, false)
            DownloadState.WAITING_FOR_STORAGE ->
                Triple(R.string.ui_download_notice_no_space, NotificationType.WARNING, true)
            DownloadState.DOWNLOADING -> return
            DownloadState.EXTRACTING ->
                Triple(R.string.ui_download_notice_extracting, NotificationType.INFO, false)
            DownloadState.MOVING ->
                Triple(R.string.ui_download_notice_moving, NotificationType.INFO, false)
            DownloadState.PAUSED ->
                Triple(R.string.ui_download_notice_paused, NotificationType.INFO, false)
            DownloadState.COMPLETED ->
                Triple(R.string.ui_download_notice_completed, NotificationType.SUCCESS, true)
            DownloadState.FAILED ->
                Triple(R.string.ui_download_notice_failed, NotificationType.ERROR, true)
            DownloadState.CANCELLED -> return
        }

        val title = NotificationText.Res(titleRes)
        val failureReason = progress.errorReason
        val subtitle = if (progress.state == DownloadState.FAILED && failureReason != null) {
            NotificationText.Res(
                R.string.ui_download_notice_failed_subtitle,
                listOf(progress.gameTitle, failureReason.toNotificationText().resolve(context))
            )
        } else {
            NotificationText.Raw(progress.gameTitle)
        }

        notificationManager.show(
            title = title,
            subtitle = subtitle,
            type = type,
            imagePath = progress.coverPath,
            duration = if (immediate) NotificationDuration.MEDIUM else NotificationDuration.SHORT,
            key = "download-${progress.gameId}",
            immediate = immediate
        )
    }

    private fun DownloadQueueState.allGameIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        activeDownloads.forEach { ids.add(it.gameId) }
        queue.forEach { ids.add(it.gameId) }
        completed.forEach { ids.add(it.gameId) }
        return ids
    }

    private fun DownloadQueueState.statusFor(gameId: Long): DownloadProgress? {
        activeDownloads.find { it.gameId == gameId }?.let { return it }
        queue.find { it.gameId == gameId }?.let { return it }
        completed.find { it.gameId == gameId }?.let { return it }
        return null
    }

    private fun DownloadQueueState.toNotificationState(): List<Pair<Long, DownloadState>> {
        val result = mutableListOf<Pair<Long, DownloadState>>()
        activeDownloads.forEach { result.add(it.gameId to it.state) }
        queue.forEach { result.add(it.gameId to it.state) }
        completed.forEach { result.add(it.gameId to it.state) }
        return result
    }
}
