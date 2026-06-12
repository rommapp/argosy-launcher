package com.nendo.argosy.data.sync

import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncConflictNotifier"
private const val NOTIFICATION_KEY = "sync_conflict_pending"

@Singleton
class SyncConflictNotifier @Inject constructor(
    private val pendingConflictDao: PendingConflictDao,
    private val notificationManager: NotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastCount: Int = 0

    fun start() {
        scope.launch {
            pendingConflictDao.getOpenCountFlow()
                .distinctUntilChanged()
                .collect { count -> onCountChanged(count) }
        }
    }

    private fun onCountChanged(count: Int) {
        val previous = lastCount
        lastCount = count
        when {
            count <= 0 -> {
                if (previous > 0) {
                    notificationManager.dismissByKey(NOTIFICATION_KEY)
                    Logger.debug(TAG, "Conflicts cleared; notification dismissed")
                }
            }
            count > previous -> {
                Logger.info(TAG, "Conflict count rose $previous -> $count; surfacing notification")
                notificationManager.show(
                    title = if (count == 1) "1 save needs your attention" else "$count saves need your attention",
                    subtitle = "Open Save Sync to resolve. Local saves are unchanged.",
                    type = NotificationType.WARNING,
                    duration = NotificationDuration.LONG,
                    key = NOTIFICATION_KEY,
                    immediate = false
                )
            }
        }
    }
}
