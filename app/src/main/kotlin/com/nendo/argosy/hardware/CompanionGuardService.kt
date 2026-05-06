package com.nendo.argosy.hardware

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R

class CompanionGuardService : Service() {

    companion object {
        private const val TAG = "CompanionGuard"

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
        Log.d(TAG, "Companion guard service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        Log.d(TAG, "Companion guard service stopped")
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(
            this, SecondaryDisplayNotificationChannel.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_helm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Dual display active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()

        val notificationId = SecondaryDisplayNotificationChannel.NOTIFICATION_ID + 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
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
