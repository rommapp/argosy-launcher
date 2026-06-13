package com.nendo.argosy.data.quaypass

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.ble.DecodeResult
import com.nendo.argosy.data.quaypass.ble.OutboundProfile
import com.nendo.argosy.data.quaypass.ble.QuayPassAdvertiser
import com.nendo.argosy.data.quaypass.ble.QuayPassExchangeOrchestrator
import com.nendo.argosy.data.quaypass.ble.QuayPassGattClient
import com.nendo.argosy.data.quaypass.ble.QuayPassGattServer
import com.nendo.argosy.data.quaypass.ble.QuayPassScanReceiver
import com.nendo.argosy.data.quaypass.ble.QuayPassScanner
import com.nendo.argosy.data.quaypass.ble.QuayPassWireFormat
import com.nendo.argosy.data.quaypass.ble.colorOnlyAvatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuayPassService @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialManager: QuayPassCredentialManager,
    private val orchestrator: QuayPassExchangeOrchestrator,
    private val playSessionTracker: PlaySessionTracker,
    private val gameDao: GameDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exchangeMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val bluetoothManager: BluetoothManager? by lazy {
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private var advertiser: QuayPassAdvertiser? = null
    private var scanner: QuayPassScanner? = null
    private var gattServer: QuayPassGattServer? = null
    private var gattClient: QuayPassGattClient? = null

    private val cachedOurBytes = AtomicReference<ByteArray?>(null)
    private var cachedLastGame: GameEntity? = null
    private var shouldBeRunning = false
    private var reEncodeJob: Job? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON ->
                    if (shouldBeRunning && !_isRunning.value) scope.launch { tryStart() }
                BluetoothAdapter.STATE_OFF ->
                    if (_isRunning.value) scope.launch { stop() }
            }
        }
    }

    init {
        instance = this
        registerBluetoothStateReceiver()

        scope.launch {
            preferencesRepository.userPreferences
                .map { ServiceTrigger(it.socialSessionToken, it.quayPassEnabled, it.quayPassAvatarConfigured) }
                .distinctUntilChanged()
                .collect { trigger ->
                    val isLinked = trigger.sessionToken != null
                    credentialManager.onSocialLinkChanged(isLinked, trigger.sessionToken)
                    shouldBeRunning = isLinked && trigger.quayPassEnabled && trigger.quayPassAvatarConfigured
                    if (shouldBeRunning && !_isRunning.value) tryStart()
                    else if (!shouldBeRunning && _isRunning.value) stop()
                }
        }

        scope.launch {
            preferencesRepository.userPreferences
                .map { ProfileSnapshot(it.socialUsername, it.socialDisplayName, it.quayPassAvatarBytes) }
                .distinctUntilChanged()
                .collect {
                    if (_isRunning.value) refreshOurBytes()
                }
        }

        scope.launch {
            playSessionTracker.activeSession
                .map { it?.gameId }
                .distinctUntilChanged()
                .collect { gameId ->
                    val game = gameId?.let { gameDao.getById(it) }
                    if (game != null) updateLastGame(game)
                }
        }
    }

    private data class ServiceTrigger(
        val sessionToken: String?,
        val quayPassEnabled: Boolean,
        val quayPassAvatarConfigured: Boolean
    )

    private data class ProfileSnapshot(
        val socialUsername: String?,
        val socialDisplayName: String?,
        val quayPassAvatarBytes: String?
    )

    fun isBleSupported(): Boolean = bluetoothManager?.adapter != null
    fun isBleEnabled(): Boolean = bluetoothManager?.adapter?.isEnabled == true

    private fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ).all {
            ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED
        }
    } else {
        true
    }

    private suspend fun tryStart() {
        if (!isBleSupported() || !isBleEnabled() || !hasPermissions()) {
            Log.w(TAG, "Cannot start QuayPass: ble=${isBleEnabled()} supported=${isBleSupported()} permissions=${hasPermissions()}")
            return
        }
        if (!credentialManager.isRegistered()) {
            Log.d(TAG, "Skipping start: install not yet registered")
            return
        }

        QuayPassScanReceiver.scanResultSink = { device, _ ->
            scope.launch { onDiscovered(device) }
        }

        gattClient = QuayPassGattClient(application)
        gattServer = QuayPassGattServer(
            application = application,
            getOurProfileBytes = { cachedOurBytes.get() },
            onPeerProfileWritten = { bytes ->
                val verified = QuayPassWireFormat.decode(bytes) is DecodeResult.Success
                if (verified) scope.launch { orchestrator.processInbound(bytes) }
                verified
            }
        ).also { it.start() }
        advertiser = QuayPassAdvertiser(application).also { it.start() }
        scanner = QuayPassScanner(application).also { it.start() }

        scope.launch { refreshOurBytes() }
        startReEncodeLoop()

        _isRunning.value = true
        Log.i(TAG, "QuayPass service started")
    }

    private fun startReEncodeLoop() {
        reEncodeJob?.cancel()
        reEncodeJob = scope.launch {
            while (isActive) {
                delay(RE_ENCODE_INTERVAL_MS)
                if (_isRunning.value) refreshOurBytes()
            }
        }
    }

    private suspend fun stop() {
        reEncodeJob?.cancel()
        reEncodeJob = null
        advertiser?.stop()
        scanner?.stop()
        gattServer?.stop()
        QuayPassScanReceiver.scanResultSink = null
        advertiser = null
        scanner = null
        gattServer = null
        gattClient = null
        cachedOurBytes.set(null)
        _isRunning.value = false
        Log.i(TAG, "QuayPass service stopped")
    }

    private suspend fun onDiscovered(device: BluetoothDevice) {
        exchangeMutex.withLock {
            if (!_isRunning.value) return
            val client = gattClient ?: return
            val ourBytes = cachedOurBytes.get() ?: refreshOurBytes() ?: return
            orchestrator.handleClient(device, client, ourBytes)
        }
    }

    private suspend fun refreshOurBytes(): ByteArray? {
        val prefs = preferencesRepository.userPreferences.first()
        val username = prefs.socialUsername ?: return null
        val avatar = decodeStoredAvatar(prefs.quayPassAvatarBytes)
            ?: colorOnlyAvatar(parseColorIndex(prefs.socialAvatarColor))
        val profile = OutboundProfile(
            username = username,
            displayName = prefs.socialDisplayName,
            greeting = prefs.quayPassGreeting,
            lastGameTitle = cachedLastGame?.title,
            lastGamePlatform = cachedLastGame?.platformSlug,
            lastGamePlaytimeMinutes = cachedLastGame?.playTimeMinutes,
            lastGameIgdbId = cachedLastGame?.igdbId,
            avatar = avatar
        )
        val bytes = orchestrator.buildOurWireBytes(profile)
        cachedOurBytes.set(bytes)
        return bytes
    }

    private fun decodeStoredAvatar(bytesBase64: String?): com.nendo.argosy.data.quaypass.ble.QuayPassAvatar? {
        val raw = bytesBase64 ?: return null
        return runCatching {
            val bytes = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
            com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec.decode(bytes)
        }.getOrNull()
    }

    private fun parseColorIndex(stored: String?): Int =
        stored?.toIntOrNull()?.coerceIn(0, 15) ?: 0

    fun updateLastGame(game: GameEntity) {
        cachedLastGame = game
        scope.launch { refreshOurBytes() }
    }

    private fun registerBluetoothStateReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(bluetoothStateReceiver, filter)
        }
    }

    companion object {
        private const val TAG = "QuayPassService"
        private val RE_ENCODE_INTERVAL_MS =
            com.nendo.argosy.data.quaypass.ble.QuayPassConfig.FRESHNESS_WINDOW_SECS * 1000 / 3

        @Volatile
        private var instance: QuayPassService? = null

        fun getInstance(): QuayPassService? = instance
    }
}
