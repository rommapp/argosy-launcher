package com.nendo.argosy.data.sync

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject
    lateinit var saveSyncRepository: SaveSyncRepository

    @Inject
    lateinit var romMRepository: RomMRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundWithNotification("Preparing sync...", 0, 0)
        observeSyncState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(MAX_WAKELOCK_DURATION_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun observeSyncState() {
        serviceScope.launch {
            combine(
                saveSyncRepository.syncQueueState,
                romMRepository.syncProgress
            ) { saveState, libraryProgress ->
                Pair(saveState, libraryProgress)
            }.collect { (saveState, libraryProgress) ->
                val hasSaveWork = saveState.operations.any {
                    it.status == SyncStatus.PENDING || it.status == SyncStatus.IN_PROGRESS
                }

                if (!hasSaveWork && !libraryProgress.isSyncing) {
                    stopSelf()
                    return@collect
                }

                if (libraryProgress.isSyncing) {
                    val title = if (libraryProgress.currentPlatform.isNotEmpty()) {
                        "Syncing: ${libraryProgress.currentPlatform}"
                    } else {
                        "Syncing library..."
                    }
                    updateNotification(title, libraryProgress.platformsDone, libraryProgress.platformsTotal)
                } else if (hasSaveWork) {
                    val current = saveState.currentOperation
                    if (current != null) {
                        val directionText = when (current.direction) {
                            SyncDirection.UPLOAD -> "Uploading"
                            SyncDirection.DOWNLOAD -> "Downloading"
                        }
                        val title = "$directionText: ${current.gameName}"
                        updateNotification(title, saveState.completedCount, saveState.operations.size)
                    } else {
                        updateNotification("Syncing saves...", saveState.completedCount, saveState.operations.size)
                    }
                }
            }
        }
    }

    private fun startForegroundWithNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        val notification = buildNotification(contentText, progress, maxProgress)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        val notification = buildNotification(contentText, progress, maxProgress)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) = NotificationCompat.Builder(this, SyncNotificationChannel.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(maxProgress, progress, progress == 0 && maxProgress == 0)
        .setContentIntent(createContentIntent())
        .build()

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 0x3000
        private const val ACTION_STOP = "com.nendo.argosy.STOP_SYNC_SERVICE"
        private const val WAKELOCK_TAG = "argosy:sync_wakelock"
        private const val MAX_WAKELOCK_DURATION_MS = 30 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
