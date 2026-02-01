package com.nendo.argosy.ui.notification

import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotificationObserver @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val notificationManager: NotificationManager
) {
    private var previousState: SyncQueueState? = null
    private var isInitialLoad = true

    fun observe(scope: CoroutineScope) {
        scope.launch {
            saveSyncRepository.syncQueueState
                .map { it.toNotificationState() }
                .distinctUntilChanged()
                .collect {
                    val previous = previousState
                    val current = saveSyncRepository.syncQueueState.value
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

    private fun detectStateChanges(previous: SyncQueueState, current: SyncQueueState) {
        val previousGameIds = previous.operations.map { it.gameId }.toSet()
        val currentGameIds = current.operations.map { it.gameId }.toSet()

        for (gameId in currentGameIds) {
            val prevOp = previous.operations.find { it.gameId == gameId }
            val currOp = current.operations.find { it.gameId == gameId }

            if (prevOp?.status != currOp?.status && currOp != null) {
                showNotificationFor(currOp)
            }
        }

        for (gameId in previousGameIds - currentGameIds) {
            notificationManager.dismissByKey("sync-$gameId")
        }
    }

    private fun showNotificationFor(operation: SyncOperation) {
        if (isInitialLoad && operation.status != SyncStatus.COMPLETED && operation.status != SyncStatus.FAILED) {
            return
        }

        val directionText = when (operation.direction) {
            SyncDirection.UPLOAD -> "Upload"
            SyncDirection.DOWNLOAD -> "Download"
        }

        val (title, type, immediate) = when (operation.status) {
            SyncStatus.PENDING -> Triple("Sync Queued", NotificationType.INFO, false)
            SyncStatus.IN_PROGRESS -> Triple("${directionText}ing Save", NotificationType.INFO, false)
            SyncStatus.COMPLETED -> Triple("$directionText Complete", NotificationType.SUCCESS, true)
            SyncStatus.FAILED -> Triple("$directionText Failed", NotificationType.ERROR, true)
        }

        val subtitle = if (operation.status == SyncStatus.FAILED && operation.error != null) {
            "${operation.gameName}: ${operation.error}"
        } else {
            operation.gameName
        }

        notificationManager.show(
            title = title,
            subtitle = subtitle,
            type = type,
            imagePath = operation.coverPath,
            duration = if (immediate) NotificationDuration.MEDIUM else NotificationDuration.SHORT,
            key = "sync-${operation.gameId}",
            immediate = immediate
        )
    }

    private fun SyncQueueState.toNotificationState(): List<Pair<Long, SyncStatus>> {
        return operations.map { it.gameId to it.status }
    }
}
