package com.nendo.argosy.data.steam

import android.app.Service
import android.content.Intent
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
            scope.launch {
                Log.d(TAG, "Connecting for QR auth")
                connect()
            }
        } else if (intent?.getBooleanExtra(EXTRA_AUTO_CONNECT, false) == true) {
            scope.launch {
                val account = steamAuthManager.getActiveAccount()
                if (account != null) {
                    Log.d(TAG, "Auto-connecting for saved account: ${account.username}")
                    connect()
                } else {
                    Log.d(TAG, "No saved account, skipping auto-connect")
                }
            }
        }

        return START_STICKY
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        const val EXTRA_CONNECT_FOR_AUTH = "connect_for_auth"
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SteamService destroyed")
        steamLibraryManager.cleanup()
        steamContentManager.cleanup()
        disconnect()
        scope.cancel()
    }

    private fun initializeSteamClient() {
        val configuration = SteamConfiguration.create { builder ->
            builder
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
            _state.value = _state.value.copy(
                connectionState = SteamConnectionState.DISCONNECTED,
                error = if (!callback.isUserInitiated) "Connection lost" else null
            )
            steamAuthManager.onDisconnected()
            steamContentManager.onDisconnected()

            if (!callback.isUserInitiated && isRunning) {
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (isRunning) {
                        Log.d(TAG, "Attempting reconnect...")
                        connect()
                    }
                }
            }
        }

        subscriptions += cm.subscribe(LoggedOnCallback::class.java) { callback ->
            if (callback.result == EResult.OK) {
                Log.d(TAG, "Logged on as ${callback.clientSteamID}")
                _state.value = _state.value.copy(
                    connectionState = SteamConnectionState.LOGGED_IN,
                    steamId = callback.clientSteamID?.convertToUInt64(),
                    error = null
                )
                steamAuthManager.onLoggedOn(callback)
                steamContentManager.notifyConnected()
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
        }
    }

    fun connect() {
        if (_state.value.connectionState == SteamConnectionState.CONNECTING) {
            Log.d(TAG, "Already connecting...")
            return
        }

        isRunning = true
        _state.value = _state.value.copy(connectionState = SteamConnectionState.CONNECTING)

        scope.launch {
            try {
                Log.d(TAG, "Connecting to Steam...")
                steamClient?.connect()
                startCallbackLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                _state.value = _state.value.copy(
                    connectionState = SteamConnectionState.DISCONNECTED,
                    error = e.message
                )
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

    fun getSteamClient(): SteamClient? = steamClient
    fun getSteamUser(): SteamUser? = steamUser
    fun getSteamApps(): SteamApps? = steamApps
    fun getCallbackManager(): CallbackManager? = callbackManager
    fun getContentManager(): SteamContentManager = steamContentManager
    fun getLibraryManager(): SteamLibraryManager = steamLibraryManager
}
