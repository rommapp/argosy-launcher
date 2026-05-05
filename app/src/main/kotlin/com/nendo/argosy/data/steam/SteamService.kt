package com.nendo.argosy.data.steam

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.inject.Inject

private const val TAG = "SteamService"

enum class SteamConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGING_IN,
    LOGGED_IN,
    LOGGED_OUT
}

data class SteamServiceState(
    val connectionState: SteamConnectionState = SteamConnectionState.DISCONNECTED,
    val username: String? = null,
    val steamId: Long? = null,
    val error: String? = null
)

@AndroidEntryPoint
class SteamService : Service() {

    @Inject
    lateinit var steamAuthManager: SteamAuthManager

    @Inject
    lateinit var steamLibraryManager: SteamLibraryManager

    @Inject
    lateinit var steamContentManager: SteamContentManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callbackJob: Job? = null

    private var steamClient: SteamClient? = null
    private var callbackManager: CallbackManager? = null
    private var steamUser: SteamUser? = null
    private var steamApps: SteamApps? = null

    private val _state = MutableStateFlow(SteamServiceState())
    val state: StateFlow<SteamServiceState> = _state.asStateFlow()

    private val subscriptions = mutableListOf<Closeable>()
    private var isRunning = false
    private var reconnectAttempt = 0
    private var networkPaused = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): SteamService = this@SteamService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SteamService created")
        initializeSteamClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SteamService started")

        if (intent?.getBooleanExtra(EXTRA_CONNECT_FOR_AUTH, false) == true) {
            promoteToForeground("Signing in")
            observeStateForNotification()
            scope.launch {
                Log.d(TAG, "Connecting for QR auth, stopping reconnect loop")
                isRunning = false
                reconnectAttempt = 0
                kotlinx.coroutines.delay(500)
                connectForAuth()
            }
        } else if (intent?.getBooleanExtra(EXTRA_FORCE_CONNECT, false) == true) {
            promoteToForeground("Connecting")
            observeStateForNotification()
            scope.launch {
                Log.d(TAG, "Force connecting to Steam (on-demand)")
                connect()
            }
        } else if (intent?.getBooleanExtra(EXTRA_AUTO_CONNECT, false) == true) {
            scope.launch {
                kotlinx.coroutines.delay(100)
                val account = steamAuthManager.getActiveAccount()
                if (account == null) {
                    Log.d(TAG, "No saved account, skipping connect")
                    stopSelf()
                    return@launch
                }
                val hasPendingWork = steamContentManager.hasPendingDownloads()
                if (hasPendingWork) {
                    Log.d(TAG, "Pending downloads found in DB, auto-connecting for ${account.username}")
                    promoteToForeground("Syncing Steam library")
                    observeStateForNotification()
                    connect()
                } else {
                    Log.d(TAG, "No pending work, stopping service")
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private var notificationObserverJob: Job? = null

    private fun observeStateForNotification() {
        if (notificationObserverJob?.isActive == true) return
        notificationObserverJob = scope.launch {
            _state.collect { state ->
                when (state.connectionState) {
                    SteamConnectionState.CONNECTING -> updateForegroundNotification("Connecting")
                    SteamConnectionState.CONNECTED,
                    SteamConnectionState.LOGGING_IN -> updateForegroundNotification("Signing in")
                    SteamConnectionState.LOGGED_IN -> {
                        val hasWork = runCatching { steamContentManager.hasPendingDownloads() }.getOrDefault(false)
                        if (hasWork) {
                            updateForegroundNotification("Syncing Steam library")
                        } else {
                            Log.d(TAG, "Logged in with no pending work, dropping foreground notification")
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        }
                    }
                    SteamConnectionState.DISCONNECTED,
                    SteamConnectionState.LOGGED_OUT -> stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    private fun updateForegroundNotification(status: String) {
        val notification = buildNotification(status)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        const val EXTRA_FORCE_CONNECT = "force_connect"
        const val EXTRA_CONNECT_FOR_AUTH = "connect_for_auth"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 9201
    }

    private fun promoteToForeground(status: String) {
        com.nendo.argosy.data.sync.SyncNotificationChannel.create(this)
        val notification = buildNotification(status)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to promote to foreground: ${e.message}")
        }
    }

    private fun buildNotification(status: String): android.app.Notification {
        com.nendo.argosy.data.sync.SyncNotificationChannel.create(this)
        return androidx.core.app.NotificationCompat.Builder(
            this, com.nendo.argosy.data.sync.SyncNotificationChannel.CHANNEL_ID
        )
            .setSmallIcon(com.nendo.argosy.R.drawable.ic_helm)
            .setContentTitle("Steam")
            .setContentText(status)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SteamService destroyed")
        unregisterNetworkCallback()
        steamLibraryManager.cleanup()
        steamContentManager.cleanup()
        disconnect()
        scope.cancel()
    }

    private fun initializeSteamClient() {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val configuration = SteamConfiguration.create { builder ->
            builder
                .withConnectionTimeout(60_000L)
                .withHttpClient(httpClient)
        }

        val client = SteamClient(configuration)
        steamClient = client
        callbackManager = CallbackManager(client)
        steamUser = client.getHandler(SteamUser::class.java)
        steamApps = client.getHandler(SteamApps::class.java)

        val cm = callbackManager!!
        steamLibraryManager.initialize(steamApps!!, cm)
        steamContentManager.initialize(client, steamApps!!, cm)

        registerCallbacks()
    }

    private fun registerCallbacks() {
        val cm = callbackManager ?: return

        subscriptions += cm.subscribe(ConnectedCallback::class.java) { _ ->
            Log.d(TAG, "Connected to Steam")
            _state.value = _state.value.copy(
                connectionState = SteamConnectionState.CONNECTED,
                error = null
            )
            steamAuthManager.onConnected(steamClient!!, steamUser!!)
        }

        subscriptions += cm.subscribe(DisconnectedCallback::class.java) { callback ->
            Log.d(TAG, "Disconnected from Steam, userInitiated=${callback.isUserInitiated}")
            if (suppressDisconnectError) {
                Log.d(TAG, "Disconnect suppressed (reconnecting for auth)")
                return@subscribe
            }
            _state.value = _state.value.copy(
                connectionState = SteamConnectionState.DISCONNECTED,
                error = if (!callback.isUserInitiated) "Connection lost" else null
            )
            steamAuthManager.onDisconnected()
            steamContentManager.onDisconnected()

            if (!callback.isUserInitiated && isRunning) {
                scope.launch {
                    if (!isRunning) {
                        Log.d(TAG, "Not running, stopping reconnect")
                        return@launch
                    }
                    if (steamAuthManager.sessionDead) {
                        Log.d(TAG, "Session dead, stopping reconnect")
                        isRunning = false
                        return@launch
                    }
                    kotlinx.coroutines.delay(500)
                    if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
                        Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
                        isRunning = false
                        _state.value = _state.value.copy(error = "Reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
                        return@launch
                    }

                    val delaySec = minOf(1L shl reconnectAttempt, 60L)
                    reconnectAttempt++
                    Log.d(TAG, "Reconnect attempt $reconnectAttempt in ${delaySec}s")
                    kotlinx.coroutines.delay(delaySec * 1000)

                    if (isRunning) {
                        connect()
                    }
                }
            }
        }

        subscriptions += cm.subscribe(LoggedOnCallback::class.java) { callback ->
            if (callback.result == EResult.OK) {
                Log.d(TAG, "Logged on as ${callback.clientSteamID}")
                reconnectAttempt = 0
                _state.value = _state.value.copy(
                    connectionState = SteamConnectionState.LOGGED_IN,
                    steamId = callback.clientSteamID?.convertToUInt64(),
                    error = null
                )
                steamAuthManager.onLoggedOn(callback)
                steamContentManager.initialize(steamClient!!, steamApps!!, cm)
                steamContentManager.notifyConnected()
                scope.launch {
                    steamContentManager.discoverLocalSteamGames()
                    steamContentManager.restorePausedDownloads()
                }
            } else {
                Log.e(TAG, "Login failed: ${callback.result}")
                _state.value = _state.value.copy(
                    connectionState = SteamConnectionState.LOGGED_OUT,
                    error = "Login failed: ${callback.result}"
                )
                steamAuthManager.onLoginFailed(callback.result)
            }
        }

        subscriptions += cm.subscribe(LoggedOffCallback::class.java) { callback ->
            Log.d(TAG, "Logged off: ${callback.result}")
            _state.value = _state.value.copy(
                connectionState = SteamConnectionState.LOGGED_OUT,
                username = null,
                steamId = null
            )
            if (callback.result == `in`.dragonbra.javasteam.enums.EResult.LogonSessionReplaced) {
                Log.d(TAG, "Session replaced externally, tokens preserved -- will reconnect with unique loginID")
            }
        }
    }

    private var connectStartedAt = 0L

    private var suppressDisconnectError = false

    private fun connectForAuth() {
        steamAuthManager.connectingForAuth = true
        suppressDisconnectError = true
        _state.value = _state.value.copy(connectionState = SteamConnectionState.CONNECTING, error = null)
        try { steamClient?.disconnect() } catch (_: Throwable) {}
        callbackJob?.cancel()
        initializeSteamClient()
        isRunning = true
        suppressDisconnectError = false

        scope.launch {
            try {
                Log.d(TAG, "Connecting fresh client for QR auth...")
                steamClient?.connect()
                startCallbackLoop()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to connect for auth", e)
                _state.value = _state.value.copy(
                    connectionState = SteamConnectionState.DISCONNECTED,
                    error = e.message
                )
            }
        }
    }

    fun connect() {
        val currentState = _state.value.connectionState
        if (currentState == SteamConnectionState.CONNECTING) {
            val elapsed = System.currentTimeMillis() - connectStartedAt
            if (elapsed < 30_000L) {
                Log.d(TAG, "Already connecting (${elapsed / 1000}s)...")
                return
            }
            Log.w(TAG, "Connection stuck at CONNECTING for ${elapsed / 1000}s, reinitializing")
            suppressDisconnectError = true
            try { steamClient?.disconnect() } catch (_: Exception) {}
            callbackJob?.cancel()
            initializeSteamClient()
            suppressDisconnectError = false
            _state.value = _state.value.copy(connectionState = SteamConnectionState.DISCONNECTED)
        }

        isRunning = true
        connectStartedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(connectionState = SteamConnectionState.CONNECTING)

        scope.launch {
            try {
                Log.d(TAG, "Connecting to Steam...")
                steamClient?.connect()
                startCallbackLoop()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to connect: ${e::class.simpleName}, reinitializing client")
                try { steamClient?.disconnect() } catch (_: Throwable) {}
                callbackJob?.cancel()
                initializeSteamClient()
                try {
                    steamClient?.connect()
                    startCallbackLoop()
                } catch (e2: Throwable) {
                    Log.e(TAG, "Retry connect also failed", e2)
                    _state.value = _state.value.copy(
                        connectionState = SteamConnectionState.DISCONNECTED,
                        error = e2.message
                    )
                }
            }
        }
    }

    fun disconnect() {
        isRunning = false
        callbackJob?.cancel()
        callbackJob = null

        try {
            steamUser?.logOff()
            steamClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }

        _state.value = SteamServiceState()
    }

    private fun startCallbackLoop() {
        callbackJob?.cancel()
        callbackJob = scope.launch {
            Log.d(TAG, "Starting callback loop")
            while (isActive && isRunning) {
                try {
                    callbackManager?.runWaitCallbacks(1000L)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
            }
            Log.d(TAG, "Callback loop ended")
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            private var lostTime = 0L

            override fun onLost(network: Network) {
                lostTime = System.currentTimeMillis()
                Log.d(TAG, "Network lost event")
                // Debounce: only pause after sustained loss (checked in onAvailable timeout)
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    // If still no network after 5s and download is active, pause
                    val dlState = steamContentManager.downloadState.value
                    if (!networkPaused && lostTime > 0 &&
                        (dlState is SteamDownloadState.Downloading || dlState is SteamDownloadState.Preparing)) {
                        Log.d(TAG, "Network lost for 5s, pausing active download")
                        networkPaused = true
                        steamContentManager.pauseDownload()
                    }
                }
            }

            override fun onAvailable(network: Network) {
                lostTime = 0 // Cancel pending pause
                if (!networkPaused) return
                Log.d(TAG, "Network restored, resuming download")
                networkPaused = false
                val dlState = steamContentManager.downloadState.value
                if (dlState is SteamDownloadState.Paused) {
                    scope.launch {
                        val game = steamContentManager.activeDownload.value ?: return@launch
                        steamContentManager.queueDownloadOptimistic(game.appId, game.gameName, game.coverPath)
                    }
                }
            }
        }

        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }


    fun getSteamClient(): SteamClient? = steamClient
    fun getSteamUser(): SteamUser? = steamUser
    fun getSteamApps(): SteamApps? = steamApps
    fun getCallbackManager(): CallbackManager? = callbackManager
    fun getContentManager(): SteamContentManager = steamContentManager
    fun getLibraryManager(): SteamLibraryManager = steamLibraryManager
}
