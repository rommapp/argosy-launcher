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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R

class RecoveryDisplayService : Service() {

    companion object {
        private const val TAG = "RecoveryDisplayService"
        const val ACTION_START = "com.nendo.argosy.RECOVERY_DISPLAY_START"
        const val ACTION_STOP = "com.nendo.argosy.RECOVERY_DISPLAY_STOP"
        const val EXTRA_OOM_RECOVERY = "oom_recovery"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_PLATFORM_NAME = "platform_name"

        fun start(
            context: Context,
            oomRecovery: Boolean = false,
            gameName: String? = null,
            platformName: String? = null
        ) {
            val intent = Intent(context, RecoveryDisplayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OOM_RECOVERY, oomRecovery)
                gameName?.let { putExtra(EXTRA_GAME_NAME, it) }
                platformName?.let { putExtra(EXTRA_PLATFORM_NAME, it) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecoveryDisplayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var currentGameName: String? = null
    private var currentPlatformName: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private var displayManager: DisplayManager? = null
    private var presentation: RecoveryDisplayPresentation? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            updatePresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            updatePresentation()
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        SecondaryDisplayNotificationChannel.create(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)
        Log.d(TAG, "Recovery service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentGameName = intent.getStringExtra(EXTRA_GAME_NAME)
                currentPlatformName = intent.getStringExtra(EXTRA_PLATFORM_NAME)

                startForegroundNotification()
                updatePresentation()

                if (intent.getBooleanExtra(EXTRA_OOM_RECOVERY, false)) {
                    Log.d(TAG, "OOM recovery mode - scheduling full presentation attempt")
                    scheduleFullPresentationAttempt()
                }
            }
            ACTION_STOP -> {
                stopPresentation()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPresentation()
        displayManager?.unregisterDisplayListener(displayListener)
        Log.d(TAG, "Recovery service destroyed")
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, SecondaryDisplayNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Recovering...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()

        startForeground(SecondaryDisplayNotificationChannel.NOTIFICATION_ID, notification)
    }

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

    private fun updatePresentation() {
        val displays = displayManager?.displays ?: return
        val secondaryDisplay = displays.find { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondaryDisplay != null && presentation == null) {
            showPresentation(secondaryDisplay)
        } else if (secondaryDisplay == null && presentation != null) {
            stopPresentation()
        }
    }

    private fun showPresentation(display: Display) {
        val displayContext = createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
            null
        )

        presentation = RecoveryDisplayPresentation(
            context = displayContext,
            display = display
        ).also {
            it.show()
            it.updateGameInfo(currentGameName, currentPlatformName)
            Log.d(TAG, "Recovery presentation shown on display ${display.displayId}")
        }
    }

    private fun scheduleFullPresentationAttempt() {
        // With SECONDARY_HOME architecture, the SecondaryHomeActivity handles the display.
        // No need to manually start any service - system manages SECONDARY_HOME automatically.
        handler.postDelayed({
            Log.d(TAG, "Recovery delay complete - SECONDARY_HOME will handle display")
        }, 5000)
    }

    private fun stopPresentation() {
        presentation?.dismiss()
        presentation = null
    }
}
