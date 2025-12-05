package com.nendo.argosy.ui.notification

import android.util.Log
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadNotifObserver"

@Singleton
class DownloadNotificationObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager
) {
    private var previousState: DownloadQueueState? = null

    fun observe(scope: CoroutineScope) {
        Log.d(TAG, "observe: starting observation")
        scope.launch {
            downloadManager.state
                .map { it.toNotificationState() }
                .distinctUntilChanged()
                .collect {
                    val previous = previousState
                    val current = downloadManager.state.value
                    previousState = current

                    Log.d(TAG, "collect: previous=${previous?.toNotificationState()}, current=${current.toNotificationState()}")

                    if (previous == null) return@collect

                    detectStateChanges(previous, current)
                }
        }
    }

    private fun detectStateChanges(previous: DownloadQueueState, current: DownloadQueueState) {
        val previousGameIds = previous.allGameIds()
        val currentGameIds = current.allGameIds()

        Log.d(TAG, "detectStateChanges: prevIds=$previousGameIds, currIds=$currentGameIds")

        for (gameId in currentGameIds) {
            val prevStatus = previous.statusFor(gameId)
            val currStatus = current.statusFor(gameId)

            Log.d(TAG, "detectStateChanges: gameId=$gameId, prev=${prevStatus?.state}, curr=${currStatus?.state}")

            if (prevStatus?.state != currStatus?.state && currStatus != null) {
                showNotificationFor(currStatus)
            }
        }

        for (gameId in previousGameIds - currentGameIds) {
            val prevStatus = previous.statusFor(gameId)
            if (prevStatus?.state == DownloadState.DOWNLOADING) {
                Log.d(TAG, "detectStateChanges: dismissing $gameId")
                notificationManager.dismissByKey("download-$gameId")
            }
        }
    }

    private fun showNotificationFor(progress: DownloadProgress) {
        val (title, type, immediate) = when (progress.state) {
            DownloadState.QUEUED -> Triple("Queued", NotificationType.INFO, false)
            DownloadState.DOWNLOADING -> Triple("Downloading", NotificationType.INFO, false)
            DownloadState.COMPLETED -> Triple("Completed", NotificationType.SUCCESS, true)
            DownloadState.FAILED -> Triple("Failed", NotificationType.ERROR, true)
            DownloadState.CANCELLED -> return
        }

        val subtitle = if (progress.state == DownloadState.FAILED && progress.errorReason != null) {
            "${progress.gameTitle}: ${progress.errorReason}"
        } else {
            progress.gameTitle
        }

        Log.d(TAG, "showNotificationFor: ${progress.gameTitle} -> $title (immediate=$immediate)")

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
        activeDownload?.let { ids.add(it.gameId) }
        queue.forEach { ids.add(it.gameId) }
        completed.forEach { ids.add(it.gameId) }
        return ids
    }

    private fun DownloadQueueState.statusFor(gameId: Long): DownloadProgress? {
        if (activeDownload?.gameId == gameId) return activeDownload
        queue.find { it.gameId == gameId }?.let { return it }
        completed.find { it.gameId == gameId }?.let { return it }
        return null
    }

    private fun DownloadQueueState.toNotificationState(): List<Pair<Long, DownloadState>> {
        val result = mutableListOf<Pair<Long, DownloadState>>()
        activeDownload?.let { result.add(it.gameId to it.state) }
        queue.forEach { result.add(it.gameId to it.state) }
        completed.forEach { result.add(it.gameId to it.state) }
        return result
    }
}
