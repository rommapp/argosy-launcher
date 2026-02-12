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
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ThemeViewModel
import com.nendo.argosy.util.DisplayAffinityHelper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class SecondaryDisplayService : Service(), LifecycleOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "SecondaryDisplayService"
        const val ACTION_START = "com.nendo.argosy.SECONDARY_DISPLAY_START"
        const val ACTION_STOP = "com.nendo.argosy.SECONDARY_DISPLAY_STOP"

        fun start(context: Context) {
            val intent = Intent(context, SecondaryDisplayService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SecondaryDisplayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var platformDao: PlatformDao
    @Inject lateinit var appsRepository: AppsRepository
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var displayAffinityHelper: DisplayAffinityHelper
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var ambientLedManager: AmbientLedManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var displayManager: DisplayManager? = null
    private var presentation: SecondaryDisplayPresentation? = null
    private var viewModel: SecondaryHomeViewModel? = null
    private var themeViewModel: ThemeViewModel? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: $displayId")
            updatePresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            updatePresentation()
        }

        override fun onDisplayChanged(displayId: Int) {
            // Display properties changed, might need to recreate presentation
        }
    }

    override fun onCreate() {
        super.onCreate()
        SecondaryDisplayNotificationChannel.create(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)

        viewModel = createViewModel()
        themeViewModel = createThemeViewModel()

        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundNotification()
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
                updatePresentation()
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopPresentation()
        displayManager?.unregisterDisplayListener(displayListener)
        _viewModelStore.clear()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, SecondaryDisplayNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Secondary display active")
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
        val vm = viewModel ?: return
        val theme = themeViewModel ?: return

        val displayContext = createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
            null
        )

        presentation = SecondaryDisplayPresentation(
            context = displayContext,
            display = display,
            viewModel = vm,
            themeViewModel = theme,
            onKeyEvent = { event -> handleKeyEvent(event) }
        ).also {
            it.show()
            it.onResume()
            Log.d(TAG, "Presentation shown on display ${display.displayId}")
        }
    }

    private fun stopPresentation() {
        presentation?.let {
            it.onPause()
            it.dismiss()
            Log.d(TAG, "Presentation dismissed")
        }
        presentation = null
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        // Reserved for future global key handling (e.g., Home button to dismiss)
        return false
    }

    private fun createViewModel(): SecondaryHomeViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SecondaryHomeViewModel(
                    gameDao = gameDao,
                    platformDao = platformDao,
                    appsRepository = appsRepository,
                    preferencesRepository = preferencesRepository,
                    displayAffinityHelper = displayAffinityHelper,
                    downloadManager = downloadManager,
                    context = applicationContext
                ) as T
            }
        }
        return ViewModelProvider(this, factory)[SecondaryHomeViewModel::class.java]
    }

    private fun createThemeViewModel(): ThemeViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(
                    preferencesRepository = preferencesRepository,
                    ambientLedManager = ambientLedManager
                ) as T
            }
        }
        return ViewModelProvider(this, factory)[ThemeViewModel::class.java]
    }
}
