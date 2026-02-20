package com.nendo.argosy.data.emulator

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.nendo.argosy.MainActivity
import com.nendo.argosy.R
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class GameSessionService : Service() {

    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var gameDao: GameDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val fileObservers = mutableListOf<FileObserver>()
    private val sessionStateStore by lazy { SessionStateStore(this) }

    private var wakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var currentGameId: Long = -1
    private var currentEmulatorId: String? = null
    private var currentSavePath: String? = null
    private var currentChannelName: String? = null
    private var currentIsHardcore: Boolean = false
    private var currentGameTitle: String = "Game"
    private var sessionStartTime: Long = 0
    private var isOverlayVisible = false
    private var isWaitingForDirectory = false
    private var lastMidGameCacheId: Long = -1

    private var helmIcon: ImageView? = null
    private var checkIcon: ImageView? = null
    private var overlayText: TextView? = null
    private var textWrapper: FrameLayout? = null
    private var introAnimator: AnimatorSet? = null
    private var exitAnimator: AnimatorSet? = null
    private var textFullWidth: Int = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        acquireWakeLock()
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "Game Session",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Monitors save files during gameplay"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val watchPath = intent?.getStringExtra(EXTRA_WATCH_PATH)
                val gameTitle = intent?.getStringExtra(EXTRA_GAME_TITLE) ?: "Game"
                val gameId = intent?.getLongExtra(EXTRA_GAME_ID, -1) ?: -1
                val emulatorId = intent?.getStringExtra(EXTRA_EMULATOR_ID)
                val savePath = intent?.getStringExtra(EXTRA_SAVE_PATH)
                val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME)
                val isHardcore = intent?.getBooleanExtra(EXTRA_IS_HARDCORE, false) ?: false
                val startTime = intent?.getLongExtra(EXTRA_SESSION_START_TIME, 0) ?: 0

                currentGameTitle = gameTitle
                currentGameId = gameId
                currentEmulatorId = emulatorId
                currentSavePath = savePath
                currentChannelName = channelName
                currentIsHardcore = isHardcore
                sessionStartTime = if (startTime > 0) startTime else System.currentTimeMillis()
                lastMidGameCacheId = -1

                startForegroundWithNotification(gameTitle, NotificationState.PLAYING)

                // Reset save state to clean when starting a new session
                broadcastSaveStateChanged(isDirty = false)

                if (watchPath != null) {
                    stopWatching()
                    removeOverlay()
                    startWatching(watchPath)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.debug(TAG, "Service destroyed")
        stopWatching()
        removeOverlay()
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

    private fun startWatching(watchPath: String) {
        val watchDir = File(watchPath)

        if (!watchDir.exists()) {
            Logger.debug(TAG, "Save directory doesn't exist yet, polling: ${watchDir.absolutePath}")
            isWaitingForDirectory = true
            pollForDirectory(watchDir)
            return
        }

        startFileObserver(watchDir)
    }

    private fun pollForDirectory(watchDir: File) {
        handler.postDelayed({
            if (!isWaitingForDirectory) return@postDelayed

            if (watchDir.exists()) {
                Logger.debug(TAG, "Save directory now exists: ${watchDir.absolutePath}")
                isWaitingForDirectory = false
                startFileObserver(watchDir)
            } else {
                pollForDirectory(watchDir)
            }
        }, POLL_INTERVAL_MS)
    }

    private fun shouldIgnoreDirectory(dir: File): Boolean {
        val name = dir.name.lowercase()
        return IGNORED_DIRECTORY_PATTERNS.any { pattern ->
            name.contains(pattern)
        }
    }

    private fun startFileObserver(watchDir: File) {
        val dirsToWatch = mutableListOf(watchDir)
        watchDir.walkTopDown().maxDepth(3).filter { it.isDirectory }.forEach { dir ->
            if (dir != watchDir) {
                if (shouldIgnoreDirectory(dir)) {
                    Logger.debug(TAG, "Skipping ignored directory: ${dir.name}")
                } else {
                    dirsToWatch.add(dir)
                }
            }
        }

        val elapsedSinceStart = System.currentTimeMillis() - sessionStartTime
        Logger.debug(TAG, "Starting file watcher on ${dirsToWatch.size} directories (session elapsed: ${elapsedSinceStart}ms)")

        dirsToWatch.forEach { dir ->
            @Suppress("DEPRECATION")
            val observer = object : FileObserver(dir.absolutePath, CLOSE_WRITE or MOVED_TO or CREATE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    if (path.startsWith(".") || path.endsWith(".tmp") || path.endsWith(".bak")) return

                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    if (elapsed < STARTUP_COOLDOWN_MS) {
                        Logger.debug(TAG, "Ignoring early event (${elapsed}ms): $path in ${dir.name}")
                        return
                    }

                    handler.post {
                        Logger.debug(TAG, "Save change detected: $path in ${dir.name} (event=$event)")
                        onSaveDetected()
                    }
                }
            }
            observer.startWatching()
            fileObservers.add(observer)
        }
    }

    private fun stopWatching() {
        isWaitingForDirectory = false
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()
        handler.removeCallbacksAndMessages(null)
    }

    private val cacheRunnable = Runnable { performCacheAndNotify() }

    private fun onSaveDetected() {
        handler.removeCallbacks(cacheRunnable)
        handler.postDelayed(cacheRunnable, CACHE_DEBOUNCE_MS)
    }

    private fun performCacheAndNotify() {
        val gameId = currentGameId
        val emulatorId = currentEmulatorId
        val savePath = currentSavePath

        if (gameId == -1L || emulatorId == null || savePath == null) {
            Logger.warn(TAG, "Cannot cache save - missing context: gameId=$gameId, emulatorId=$emulatorId, savePath=$savePath")
            return
        }

        updateNotification(currentGameTitle, NotificationState.SAVE_DETECTED)
        showOverlayBriefly()

        // Notify secondary home that save is dirty (being cached)
        broadcastSaveStateChanged(isDirty = true)

        serviceScope.launch {
            try {
                if (lastMidGameCacheId > 0) {
                    saveCacheManager.deleteCachedSave(lastMidGameCacheId)
                    lastMidGameCacheId = -1
                }

                val result = saveCacheManager.cacheCurrentSave(
                    gameId = gameId,
                    emulatorId = emulatorId,
                    savePath = savePath,
                    channelName = currentChannelName,
                    isLocked = false,
                    isHardcore = currentIsHardcore,
                    skipDuplicateCheck = true,
                    needsRemoteSync = true
                )
                when (result) {
                    is SaveCacheManager.CacheResult.Created -> {
                        lastMidGameCacheId = result.cacheId
                        Logger.info(TAG, "Live cache created for gameId=$gameId (cacheId=${result.cacheId}), updating activeSaveTimestamp to ${result.timestamp}")
                        gameDao.updateActiveSaveTimestamp(gameId, result.timestamp)
                    }
                    is SaveCacheManager.CacheResult.Duplicate -> {
                        Logger.debug(TAG, "Live cache skipped (duplicate) for gameId=$gameId")
                    }
                    is SaveCacheManager.CacheResult.Failed -> {
                        Logger.warn(TAG, "Live cache failed for gameId=$gameId")
                    }
                }
            } catch (e: Exception) {
                Logger.error(TAG, "Live cache error for gameId=$gameId", e)
            }
        }

        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, RESET_DELAY_MS)
    }

    private val resetRunnable = Runnable {
        updateNotification(currentGameTitle, NotificationState.PLAYING)
        hideOverlay()
    }

    // region Notification

    private enum class NotificationState {
        PLAYING, SAVE_DETECTED
    }

    private fun startForegroundWithNotification(gameTitle: String, state: NotificationState) {
        val notification = buildNotification(gameTitle, state)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(gameTitle: String, state: NotificationState) {
        val notification = buildNotification(gameTitle, state)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(gameTitle: String, state: NotificationState) = when (state) {
        NotificationState.PLAYING -> NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_controller)
            .setContentTitle("Playing")
            .setContentText(gameTitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()

        NotificationState.SAVE_DETECTED -> NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_save)
            .setContentTitle("Save detected")
            .setContentText(gameTitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()
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

    // endregion

    // region Overlay (brief flash on save detection)

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
               Configuration.UI_MODE_NIGHT_YES
    }

    private fun showOverlayBriefly() {
        if (!Settings.canDrawOverlays(this)) return
        if (isOverlayVisible) return

        val dp = { value: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        }

        val isDark = isDarkTheme()
        val backgroundColor = if (isDark) Color.parseColor("#DD1E1E1E") else Color.parseColor("#DDF5F5F5")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")
        val helmTint = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")

        val iconSize = dp(18)
        val iconContainerWidth = iconSize + dp(20)

        val iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        helmIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.ic_helm)
            setColorFilter(helmTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        checkIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.ic_check)
            scaleType = ImageView.ScaleType.FIT_CENTER
            scaleX = 0f
            scaleY = 0f
        }

        iconContainer.addView(helmIcon)
        iconContainer.addView(checkIcon)

        overlayText = TextView(this).apply {
            text = "save cached"
            setTextColor(textColor)
            textSize = 12f
            maxLines = 1
        }

        overlayText?.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        textFullWidth = overlayText?.measuredWidth ?: 0

        overlayText?.layoutParams = FrameLayout.LayoutParams(
            textFullWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        textWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
            }
            clipChildren = true
            clipToPadding = true
            addView(overlayText)
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(backgroundColor)
            }

            addView(iconContainer)
            addView(textWrapper)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 24
            y = 24
        }

        try {
            windowManager?.addView(overlayView, params)
            isOverlayVisible = true

            startIntroAnimation()
            Logger.debug(TAG, "Overlay shown")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to show overlay", e)
        }
    }

    private fun startIntroAnimation() {
        val helm = helmIcon ?: return
        val check = checkIcon ?: return
        val wrapper = textWrapper ?: return

        val helmRotation = ObjectAnimator.ofFloat(helm, "rotation", 0f, 900f).apply {
            duration = 800
            interpolator = AccelerateInterpolator(2.5f)
        }

        val helmFadeOut = ObjectAnimator.ofFloat(helm, "alpha", 1f, 0f).apply {
            duration = 200
            startDelay = 600
        }

        val textReveal = ValueAnimator.ofInt(0, textFullWidth).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val lp = wrapper.layoutParams
                lp.width = animator.animatedValue as Int
                wrapper.layoutParams = lp
            }
        }

        val checkBounce = ObjectAnimator.ofPropertyValuesHolder(
            check,
            PropertyValuesHolder.ofFloat("scaleX", 0f, 1.15f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 0f, 1.15f, 1f)
        ).apply {
            duration = 300
            startDelay = 700
            interpolator = OvershootInterpolator(2f)
        }

        introAnimator = AnimatorSet().apply {
            playTogether(helmRotation, helmFadeOut, textReveal, checkBounce)
            start()
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return

        introAnimator?.cancel()
        introAnimator = null

        val overlay = overlayView ?: return
        val wrapper = textWrapper ?: return

        val textCollapse = ValueAnimator.ofInt(textFullWidth, 0).apply {
            duration = 200
            interpolator = AccelerateInterpolator()
            addUpdateListener { animator ->
                val lp = wrapper.layoutParams
                lp.width = animator.animatedValue as Int
                wrapper.layoutParams = lp
            }
        }

        val fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 150
            startDelay = 150
        }

        exitAnimator = AnimatorSet().apply {
            playTogether(textCollapse, fadeOut)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeOverlay()
                }
            })
            start()
        }
    }

    private fun removeOverlay() {
        introAnimator?.cancel()
        exitAnimator?.cancel()
        introAnimator = null
        exitAnimator = null

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Logger.debug(TAG, "Overlay removed")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to remove overlay", e)
            }
        }
        overlayView = null
        helmIcon = null
        checkIcon = null
        overlayText = null
        textWrapper = null
        isOverlayVisible = false
    }

    // endregion

    private fun broadcastSaveStateChanged(isDirty: Boolean) {
        sessionStateStore.setSaveDirty(isDirty)
        DualScreenManagerHolder.instance?.companionHost?.onSaveDirtyChanged(isDirty)
    }

    companion object {
        private const val TAG = "GameSessionService"
        private const val CHANNEL_ID = "game_session_channel"
        private const val NOTIFICATION_ID = 0x5000
        private const val ACTION_STOP = "com.nendo.argosy.STOP_GAME_SESSION"
        private const val EXTRA_WATCH_PATH = "watch_path"
        private const val EXTRA_GAME_TITLE = "game_title"
        private const val EXTRA_GAME_ID = "game_id"
        private const val EXTRA_EMULATOR_ID = "emulator_id"
        private const val EXTRA_SAVE_PATH = "save_path"
        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_IS_HARDCORE = "is_hardcore"
        private const val EXTRA_SESSION_START_TIME = "session_start_time"
        private const val WAKELOCK_TAG = "argosy:game_session_wakelock"
        private const val MAX_WAKELOCK_DURATION_MS = 4 * 60 * 60 * 1000L // 4 hours max
        private const val RESET_DELAY_MS = 3000L
        private const val POLL_INTERVAL_MS = 2000L
        private const val STARTUP_COOLDOWN_MS = 45000L
        private const val CACHE_DEBOUNCE_MS = 250L

        private val IGNORED_DIRECTORY_PATTERNS = setOf(
            "cache",
            "shader",
            "shaders",
            "gpu_cache",
            "temp",
            "log",
            "logs",
        )

        fun start(
            context: Context,
            watchPath: String?,
            savePath: String?,
            gameId: Long,
            emulatorId: String?,
            gameTitle: String,
            channelName: String?,
            isHardcore: Boolean,
            sessionStartTime: Long
        ) {
            Logger.debug(TAG, "Starting service for: $gameTitle (gameId=$gameId, sessionStart=$sessionStartTime)")
            val intent = Intent(context, GameSessionService::class.java).apply {
                putExtra(EXTRA_WATCH_PATH, watchPath)
                putExtra(EXTRA_SAVE_PATH, savePath)
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_EMULATOR_ID, emulatorId)
                putExtra(EXTRA_GAME_TITLE, gameTitle)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_IS_HARDCORE, isHardcore)
                putExtra(EXTRA_SESSION_START_TIME, sessionStartTime)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Logger.debug(TAG, "Stop requested")
            context.stopService(Intent(context, GameSessionService::class.java))
        }
    }
}
