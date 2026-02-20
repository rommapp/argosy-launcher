package com.nendo.argosy.hardware

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CompanionGuardService : Service() {

    companion object {
        private const val TAG = "CompanionGuard"
        private const val RELAUNCH_DELAY_MS = 800L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, CompanionGuardService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, CompanionGuardService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private var displayManager: DisplayManager? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {
            val dm = displayManager ?: return
            if (dm.displays.size <= 1) {
                Log.d(TAG, "No secondary display, stopping guard service")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SecondaryDisplayNotificationChannel.create(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)
        startForegroundNotification()
        startObserving()
        Log.d(TAG, "Companion guard service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        scope.cancel()
        displayManager?.unregisterDisplayListener(displayListener)
        Log.d(TAG, "Companion guard service stopped")
        super.onDestroy()
    }

    private fun startObserving() {
        observerJob = scope.launch {
            val dsm = DualScreenManagerHolder.instance ?: run {
                Log.w(TAG, "DSM not available, waiting...")
                var attempts = 0
                while (DualScreenManagerHolder.instance == null && attempts < 50) {
                    delay(200)
                    attempts++
                }
                DualScreenManagerHolder.instance
            }
            if (dsm == null) {
                Log.e(TAG, "DSM never became available, stopping")
                stopSelf()
                return@launch
            }

            dsm.isCompanionActive.collect { active ->
                if (!active && dsm.displayAffinityHelper.hasSecondaryDisplay) {
                    if (!dsm.sessionStateStore.hasActiveSession()) {
                        Log.d(TAG, "Companion inactive, no game session -- user may have opened another app, skipping relaunch")
                        return@collect
                    }
                    Log.d(TAG, "Companion inactive during game session, scheduling relaunch")
                    delay(RELAUNCH_DELAY_MS)
                    if (!dsm.isCompanionActive.value &&
                        dsm.sessionStateStore.hasActiveSession()
                    ) {
                        Log.d(TAG, "Companion still inactive during session, relaunching")
                        dsm.ensureCompanionLaunched()
                    }
                }
            }
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(
            this, SecondaryDisplayNotificationChannel.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Dual display active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()

        startForeground(
            SecondaryDisplayNotificationChannel.NOTIFICATION_ID + 1,
            notification
        )
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
