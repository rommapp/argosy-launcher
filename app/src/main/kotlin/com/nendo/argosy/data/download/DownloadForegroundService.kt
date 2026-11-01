package com.nendo.argosy.data.download

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.R
import com.nendo.argosy.core.service.ServiceNotificationIds
import com.nendo.argosy.core.service.startForegroundServiceSafely
import dagger.hilt.android.AndroidEntryPoint
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val tag = com.nendo.argosy.data.preferences.SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, tag))
    }

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var mediaDownloadManager: MediaDownloadManager

    private val serviceScope = SafeCoroutineScope(Dispatchers.Main, "DownloadForegroundService")
    private var wakeLock: PowerManager.WakeLock? = null
    private val downloadWakeLock = DownloadWakeLock(
        scope = serviceScope,
        renewMs = WAKELOCK_RENEW_MS,
        acquire = ::acquireWakeLock,
        release = ::releaseWakeLock
    )
    private var lastRendered: Triple<String, Int, Int>? = null

    private val batchIds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification(getString(R.string.sync_download_service_preparing), 0, 0)
        observeDownloadState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadWakeLock.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .also { wakeLock = it }
        if (!lock.isHeld) lock.acquire(WAKELOCK_LEASE_MS)
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
                mediaDownloadManager.downloadState,
                mediaDownloadManager.activeDownload
            ) { rommState, mediaState, mediaDl ->
                DownloadServiceSnapshot(rommState, mediaState, mediaDl)
            }
                .collect { snapshot ->
                    val rommState = snapshot.rommState
                    val rommActive = rommState.activeDownloads
                    val rommQueued = rommState.queue.filter {
                        it.state == DownloadState.QUEUED || it.state == DownloadState.RETRYING ||
                            it.state == DownloadState.WAITING_FOR_NETWORK
                    }
                    val mediaBusy = snapshot.mediaState is MediaDownloadState.Preparing ||
                        snapshot.mediaState is MediaDownloadState.Downloading
                    downloadWakeLock.update(rommActive.map { it.state }, mediaBusy)

                    if (rommActive.isEmpty() && rommQueued.isEmpty() && !mediaBusy) {
                        stopSelf()
                        return@collect
                    }

                    if (mediaBusy && rommActive.isEmpty()) {
                        updateMediaNotification(snapshot.mediaState, snapshot.mediaDownload)
                        return@collect
                    }

                    val outstanding = rommActive + rommQueued
                    batchIds.addAll(outstanding.map { it.id })
                    val batchTotal = batchIds.size
                    val batchDone = batchTotal - outstanding.size

                    val currentDownload = rommActive.firstOrNull()
                    if (currentDownload != null) {
                        val name = when (currentDownload.state) {
                            DownloadState.RETRYING -> getString(
                                R.string.sync_download_service_retrying, currentDownload.displayTitle
                            )
                            DownloadState.WAITING_FOR_NETWORK -> getString(
                                R.string.sync_download_service_waiting_for_network, currentDownload.displayTitle
                            )
                            DownloadState.EXTRACTING -> getString(
                                R.string.sync_download_service_extracting, currentDownload.displayTitle
                            )
                            DownloadState.MOVING -> getString(
                                R.string.sync_download_service_moving, currentDownload.displayTitle
                            )
                            else -> getString(
                                R.string.sync_download_service_downloading, currentDownload.displayTitle
                            )
                        }
                        val inFlight = rommActive.sumOf { it.batchFraction().toDouble() }.toFloat()
                        updateNotification(
                            withBatchPosition(name, batchDone + rommActive.size, batchTotal),
                            batchPercent(batchDone + inFlight, batchTotal),
                            100
                        )
                    } else {
                        val nextQueued = rommQueued.firstOrNull()
                        val message = nextQueued
                            ?.let { getString(R.string.sync_download_service_queued, it.displayTitle) }
                            ?: getString(R.string.sync_download_service_pending)
                        updateNotification(
                            withBatchPosition(message, batchDone + 1, batchTotal),
                            batchPercent(batchDone.toFloat(), batchTotal),
                            100
                        )
                    }
                }
        }
    }

    private fun DownloadProgress.batchFraction(): Float = when (state) {
        DownloadState.EXTRACTING -> 0f
        DownloadState.MOVING -> extractionPercent
        else -> progressPercent
    }

    private fun batchPercent(done: Float, total: Int): Int =
        if (total <= 0) 0 else ((done / total) * 100).toInt().coerceIn(0, 100)

    private fun withBatchPosition(text: String, position: Int, total: Int): String =
        if (total <= 1) {
            text
        } else {
            getString(
                R.string.sync_download_service_batch_position,
                text,
                position.coerceIn(1, total),
                total
            )
        }

    private fun updateMediaNotification(state: MediaDownloadState, progress: MediaDownloadProgress?) {
        when (state) {
            is MediaDownloadState.Preparing -> updateNotification(
                getString(
                    R.string.sync_download_service_media_preparing,
                    state.detail,
                    progress?.displayTitle ?: state.itemName
                ),
                0,
                0
            )
            is MediaDownloadState.Downloading -> {
                val title = getString(
                    R.string.sync_download_service_media_downloading,
                    progress?.displayTitle ?: state.itemName
                )
                if (progress != null && progress.totalBytes > 0 && !progress.sizeIsEstimated) {
                    updateNotification(title, (state.progress * 100).toInt(), 100)
                } else {
                    updateNotification(title, 0, 0)
                }
            }
            else -> Unit
        }
    }

    private data class DownloadServiceSnapshot(
        val rommState: DownloadQueueState,
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
        val rendered = Triple(contentText, progress, maxProgress)
        if (rendered == lastRendered) return
        lastRendered = rendered
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
        val intent = Intent(this, ArgosyActivity::class.java).apply {
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
        private const val NOTIFICATION_ID = ServiceNotificationIds.DOWNLOAD
        private const val WAKELOCK_TAG = "argosy:download_wakelock"
        private const val WAKELOCK_LEASE_MS = 10 * 60 * 1000L
        private const val WAKELOCK_RENEW_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundServiceSafely(
                Intent(context, DownloadForegroundService::class.java)
            )
        }
    }
}
