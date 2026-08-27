package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncConflictNotifier"
private const val NOTIFICATION_KEY = "sync_conflict_pending"

@Singleton
class SyncConflictNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingConflictDao: PendingConflictDao,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val notificationManager: NotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastCount: Int = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            syncPreferencesRepository.preferences
                .map { it.rommUserId }
                .distinctUntilChanged()
                .flatMapLatest { pendingConflictDao.getOpenCountFlow(PendingConflictEntity.ownerScope(it)) }
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
                    title = NotificationText.Plural(
                        R.plurals.sync_conflict_pending_title,
                        count,
                        listOf(count)
                    ),
                    subtitle = NotificationText.Res(R.string.sync_conflict_pending_subtitle),
                    type = NotificationType.WARNING,
                    duration = NotificationDuration.LONG,
                    key = NOTIFICATION_KEY,
                    immediate = false
                )
            }
        }
    }
}
