package com.nendo.argosy.ui.components.activity

import com.nendo.argosy.core.notification.ActivityEntry
import com.nendo.argosy.core.notification.ActivityHistory
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class QuickDownloadLine(
    val key: String,
    val title: String,
    val fraction: Float
)

/**
 * The save sync in progress: [gameName] is the game syncing now, [remaining] the operations not
 * yet finished, the current one included.
 */
data class QuickSyncLine(
    val gameName: String?,
    val remaining: Int
)

data class QuickActivityState(
    val downloads: List<QuickDownloadLine> = emptyList(),
    val queuedDownloads: Int = 0,
    val sync: QuickSyncLine? = null,
    val recent: List<ActivityEntry> = emptyList()
)

/**
 * State of the quick panel's Activity page: live downloads and save sync, then the notices posted
 * this session as [ActivityHistory] folds them.
 */
class QuickActivityController(
    downloadManager: DownloadManager,
    syncQueueManager: SyncQueueManager,
    private val history: ActivityHistory,
    scope: CoroutineScope
) {
    val state: StateFlow<QuickActivityState> = combine(
        downloadManager.state,
        syncQueueManager.state,
        history.log
    ) { downloads, sync, log ->
        QuickActivityState(
            downloads = downloadLines(downloads),
            queuedDownloads = downloads.queue.size,
            sync = syncLine(sync),
            recent = log.entries
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuickActivityState())

    val hasUnseen: StateFlow<Boolean> = history.log
        .map { it.hasUnseen }
        .stateIn(scope, SharingStarted.Eagerly, history.log.value.hasUnseen)

    /**
     * Marks every recent entry seen, and keeps marking new ones, until the calling coroutine is
     * cancelled.
     */
    suspend fun markSeenWhileShown() {
        history.log.collect { if (it.hasUnseen) history.markSeen() }
    }

    private fun downloadLines(state: DownloadQueueState): List<QuickDownloadLine> =
        state.activeDownloads.map { progress ->
            QuickDownloadLine(
                key = "${progress.id}:${progress.gameId}:${progress.fileName}",
                title = progress.displayTitle,
                fraction = if (progress.state == DownloadState.EXTRACTING) {
                    progress.extractionPercent
                } else {
                    progress.progressPercent
                }.coerceIn(0f, 1f)
            )
        }

    private fun syncLine(state: SyncQueueState): QuickSyncLine? {
        if (!state.hasPendingWork()) return null
        val remaining = state.operations.count {
            it.status == SyncStatus.PENDING || it.status == SyncStatus.IN_PROGRESS
        }
        return QuickSyncLine(gameName = state.currentOperation?.gameName, remaining = remaining)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
