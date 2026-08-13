package com.nendo.argosy.data.download

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.toNotificationText
import dagger.hilt.android.AndroidEntryPoint
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var steamContentManager: com.nendo.argosy.data.steam.SteamContentManager

    @Inject
    lateinit var mediaDownloadManager: MediaDownloadManager

    private val serviceScope = SafeCoroutineScope(Dispatchers.Main, "DownloadForegroundService")
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundWithNotification("Preparing download...", 0, 0)
        observeDownloadState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
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

    private fun renewWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                it.acquire(MAX_WAKELOCK_DURATION_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun observeDownloadState() {
        serviceScope.launch {
            combine(
                downloadManager.state,
                steamContentManager.downloadState,
                steamContentManager.activeDownload,
                mediaDownloadManager.downloadState,
                mediaDownloadManager.activeDownload
            ) { rommState, steamState, steamDl, mediaState, mediaDl ->
                DownloadServiceSnapshot(rommState, steamState, steamDl, mediaState, mediaDl)
            }
                .collect { snapshot ->
                    val rommState = snapshot.rommState
                    val steamState = snapshot.steamState
                    val steamDl = snapshot.steamDownload
                    val rommActive = rommState.activeDownloads
                    val rommQueued = rommState.queue.filter { it.state == DownloadState.QUEUED }
                    val steamBusy = steamState !is com.nendo.argosy.data.steam.SteamDownloadState.Idle &&
                        steamState !is com.nendo.argosy.data.steam.SteamDownloadState.Completed &&
                        steamState !is com.nendo.argosy.data.steam.SteamDownloadState.Failed
                    val mediaBusy = snapshot.mediaState is MediaDownloadState.Preparing ||
                        snapshot.mediaState is MediaDownloadState.Downloading

                    if (rommActive.isEmpty() && rommQueued.isEmpty() && !steamBusy && !mediaBusy) {
                        stopSelf()
                        return@collect
                    }

                    if (mediaBusy && rommActive.isEmpty() && !steamBusy) {
                        updateMediaNotification(snapshot.mediaState, snapshot.mediaDownload)
                        return@collect
                    }

                    if (steamBusy && steamDl != null) {
                        val text = steamState.toNotificationText(steamDl.gameName)
                        if (text != null) {
                            updateNotification(text, 0, 0)
                        } else if (steamState is com.nendo.argosy.data.steam.SteamDownloadState.Downloading) {
                            val pct = (steamDl.progress * 100).toInt()
                            updateNotification("Downloading: ${steamDl.gameName}", pct, 100)
                        }
                        return@collect
                    }

                    val currentDownload = rommActive.firstOrNull()
                    if (currentDownload != null) {
                        val title = when (currentDownload.state) {
                            DownloadState.EXTRACTING -> "Extracting: ${currentDownload.displayTitle}"
                            else -> "Downloading: ${currentDownload.displayTitle}"
                        }
                        if (currentDownload.state == DownloadState.EXTRACTING) {
                            updateNotification(title, 0, 0)
                        } else {
                            val progressPercent = (currentDownload.progressPercent * 100).toInt()
                            updateNotification(title, progressPercent, 100)
                        }
                    } else {
                        val nextQueued = rommQueued.firstOrNull()
                        val message = nextQueued?.let { "Queued: ${it.displayTitle}" }
                            ?: "Download pending..."
                        updateNotification(message, 0, 0)
                    }
                }
        }
    }

    private fun updateMediaNotification(state: MediaDownloadState, progress: MediaDownloadProgress?) {
        when (state) {
            is MediaDownloadState.Preparing -> updateNotification(
                "${state.detail}: ${progress?.displayTitle ?: state.itemName}", 0, 0
            )
            is MediaDownloadState.Downloading -> {
                val title = progress?.displayTitle ?: state.itemName
                if (progress != null && progress.totalBytes > 0 && !progress.sizeIsEstimated) {
                    updateNotification("Downloading: $title", (state.progress * 100).toInt(), 100)
                } else {
                    updateNotification("Downloading: $title", 0, 0)
                }
            }
            else -> Unit
        }
    }

    private data class DownloadServiceSnapshot(
        val rommState: DownloadQueueState,
        val steamState: com.nendo.argosy.data.steam.SteamDownloadState,
        val steamDownload: com.nendo.argosy.data.steam.SteamDownloadProgress?,
        val mediaState: MediaDownloadState,
        val mediaDownload: MediaDownloadProgress?
    )

    private fun startForegroundWithNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        val notification = buildNotification(contentText, progress, maxProgress)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) {
        renewWakeLock()
        val notification = buildNotification(contentText, progress, maxProgress)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        contentText: String,
        progress: Int,
        maxProgress: Int
    ) = NotificationCompat.Builder(this, DownloadNotificationChannel.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_helm)
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
        private const val NOTIFICATION_ID = 0x2000
        private const val ACTION_STOP = "com.nendo.argosy.STOP_DOWNLOAD_SERVICE"
        private const val WAKELOCK_TAG = "argosy:download_wakelock"
        private const val MAX_WAKELOCK_DURATION_MS = 60 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
