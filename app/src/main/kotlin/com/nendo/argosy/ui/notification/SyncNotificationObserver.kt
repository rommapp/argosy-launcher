package com.nendo.argosy.core.notification

import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val SUCCESS_COOLDOWN: Duration = Duration.ofHours(1)
private const val BATCH_WINDOW_MS = 3_000L

@Singleton
class SyncNotificationObserver @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val notificationManager: NotificationManager
) {
    private var previousState: SyncQueueState? = null
    private var isInitialLoad = true

    private val lastSuccessAt = mutableMapOf<Long, Instant>()

    private var observerScope: CoroutineScope? = null
    private val pendingSuccesses = mutableListOf<SyncOperation>()
    private var batchFlushJob: Job? = null

    fun observe(scope: CoroutineScope) {
        observerScope = scope
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
                handleStatusTransition(currOp)
            }
        }

        for (gameId in previousGameIds - currentGameIds) {
            notificationManager.dismissByKey("sync-$gameId")
        }
    }

    private fun handleStatusTransition(operation: SyncOperation) {
        when (operation.status) {
            SyncStatus.PENDING, SyncStatus.IN_PROGRESS, SyncStatus.CONFLICT_PENDING -> return
            SyncStatus.COMPLETED -> {
                if (isInitialLoad) return
                val now = Instant.now()
                val last = lastSuccessAt[operation.gameId]
                if (last != null && Duration.between(last, now) < SUCCESS_COOLDOWN) return
                lastSuccessAt[operation.gameId] = now
                enqueueSuccess(operation)
            }
            SyncStatus.FAILED -> showFailureNotification(operation)
        }
    }

    private fun enqueueSuccess(operation: SyncOperation) {
        val scope = observerScope ?: return
        pendingSuccesses.removeAll { it.gameId == operation.gameId }
        pendingSuccesses.add(operation)
        batchFlushJob?.cancel()
        batchFlushJob = scope.launch {
            delay(BATCH_WINDOW_MS)
            flushPendingSuccesses()
        }
    }

    private fun flushPendingSuccesses() {
        val batch = pendingSuccesses.toList()
        pendingSuccesses.clear()
        batchFlushJob = null
        if (batch.isEmpty()) return

        if (batch.size == 1) {
            val op = batch.first()
            val subtitle = op.channelName?.let { "${op.gameName} ($it)" } ?: op.gameName
            notificationManager.show(
                title = "Save Synced",
                subtitle = subtitle,
                type = NotificationType.SUCCESS,
                imagePath = op.coverPath,
                duration = NotificationDuration.MEDIUM,
                key = "sync-${op.gameId}",
                immediate = true
            )
            return
        }

        val titles = batch.take(3).joinToString(", ") { it.gameName }
        val subtitle = if (batch.size > 3) "$titles, +${batch.size - 3} more" else titles
        notificationManager.show(
            title = "${batch.size} saves synced",
            subtitle = subtitle,
            type = NotificationType.SUCCESS,
            duration = NotificationDuration.MEDIUM,
            key = "sync-batch",
            immediate = true
        )
    }

    private fun showFailureNotification(operation: SyncOperation) {
        val title = when (operation.direction) {
            SyncDirection.UPLOAD -> "Upload Failed"
            SyncDirection.DOWNLOAD -> "Download Failed"
        }
        val gameLine = operation.channelName?.let { "${operation.gameName} ($it)" } ?: operation.gameName
        val subtitle = operation.error?.let { "$gameLine: $it" } ?: gameLine
        notificationManager.show(
            title = title,
            subtitle = subtitle,
            type = NotificationType.ERROR,
            imagePath = operation.coverPath,
            duration = NotificationDuration.MEDIUM,
            key = "sync-${operation.gameId}",
            immediate = true
        )
    }

    private fun SyncQueueState.toNotificationState(): List<Pair<Long, SyncStatus>> {
        return operations.map { it.gameId to it.status }
    }
}
