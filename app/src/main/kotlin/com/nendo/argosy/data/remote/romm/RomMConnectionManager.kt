package com.nendo.argosy.data.remote.romm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import android.net.ConnectivityManager
import android.net.Network
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMConnectionManager"
private const val MIN_DEVICE_API_VERSION = "4.7.0"
private const val DOWNLOAD_STALL_TIMEOUT_SECONDS = 300

private val RECONNECT_BACKOFF_MS = listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L)

private val DEVICE_AUTH_SCOPES = listOf(
    "me.read", "me.write",
    "platforms.read", "platforms.write",
    "roms.read", "roms.write",
    "roms.user.read", "roms.user.write",
    "assets.read", "assets.write",
    "firmware.read", "firmware.write",
    "collections.read", "collections.write",
    "devices.read", "devices.write",
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(
        val version: String,
        val capabilities: RomMCapabilities = RomMCapabilities.from(version)
    ) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

sealed class DeviceAuthPoll {
    data object Pending : DeviceAuthPoll()
    data object SlowDown : DeviceAuthPoll()
    data object Denied : DeviceAuthPoll()
    data object Expired : DeviceAuthPoll()
    data class Approved(val token: String) : DeviceAuthPoll()
    data class AddedAccount(val accountId: Long) : DeviceAuthPoll()
    data class Failed(val message: String) : DeviceAuthPoll()
}

@Singleton
class RomMConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val databaseAdminRepository: dagger.Lazy<com.nendo.argosy.data.repository.DatabaseAdminRepository>,
    private val saveCacheRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveCacheRepository>,
    private val biosRepository: BiosRepository,
    private val rommAccountRepository: dagger.Lazy<com.nendo.argosy.data.repository.RomMAccountRepository>,
    private val apiFactory: RomMApiFactory
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private var cachedDeviceId: String? = null
    private var deviceAuthApi: RomMApi? = null
    private var deviceAuthBaseUrl: String? = null
    private val detailAdapter by lazy { Moshi.Builder().build().adapter(RomMDetailResponse::class.java) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private var reconnectJob: Job? = null
    private var networkCallbackRegistered = false
    @Volatile private var reconnectPending = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun getApi(): RomMApi? = api

    fun getBaseUrl(): String = baseUrl

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun getDeviceId(): String? = cachedDeviceId

    fun getConnectedVersion(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.version
    }

    fun getCapabilities(): RomMCapabilities {
        return (_connectionState.value as? ConnectionState.Connected)?.capabilities
            ?: RomMCapabilities.NONE
    }

    fun isVersionAtLeast(minVersion: String): Boolean {
        val current = getConnectedVersion() ?: return false
        return RomMCapabilities.compareVersions(current, minVersion) >= 0
    }

    suspend fun initialize() {
        val prefs = userPreferencesRepository.preferences.first()
        Logger.info(TAG, "initialize: baseUrl=${prefs.rommBaseUrl?.take(30)}, hasToken=${prefs.rommToken != null}")
        rommAccountRepository.get().adoptLegacyCredentialsIfNeeded()
        cachedDeviceId = prefs.rommDeviceId
        if (cachedDeviceId != null) {
            saveSyncRepository.get().setDeviceId(cachedDeviceId)
        }
        if (prefs.rommBaseUrl.isNullOrBlank()) return
        registerNetworkCallback()
        val result = attemptConnection(prefs.rommBaseUrl, prefs.rommToken)
        Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
        if (result is RomMResult.Error) scheduleReconnect()
    }

    /**
     * Retries the persisted connection on a backoff ladder, preserving the current
     * connection state until the ladder is exhausted so transient network loss
     * (sleep/wake, spotty wifi) does not read as a dead server.
     */
    private fun scheduleReconnect() {
        reconnectPending = true
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            for (backoffMs in RECONNECT_BACKOFF_MS) {
                delay(backoffMs)
                if (!reconnectPending) return@launch
                val prefs = userPreferencesRepository.preferences.first()
                val url = prefs.rommBaseUrl
                if (url.isNullOrBlank()) return@launch
                Logger.info(TAG, "scheduleReconnect: retrying after ${backoffMs}ms")
                if (attemptConnection(url, prefs.rommToken) is RomMResult.Success) return@launch
            }
            if (!reconnectPending) return@launch
            Logger.info(TAG, "scheduleReconnect: exhausted retries, marking disconnected")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        networkCallbackRegistered = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isConnected()) return
                scope.launch {
                    val prefs = userPreferencesRepository.preferences.first()
                    val url = prefs.rommBaseUrl
                    if (url.isNullOrBlank()) return@launch
                    Logger.info(TAG, "network available, attempting reconnect")
                    if (attemptConnection(url, prefs.rommToken) is RomMResult.Error) scheduleReconnect()
                }
            }
        })
    }

    private fun normalizeServerKey(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

    private suspend fun fetchCurrentUser(target: RomMApi): RomMUser? = try {
        val response = target.getCurrentUser()
        if (response.isSuccessful) response.body() else null
    } catch (_: Exception) {
        null
    }

    private suspend fun persistRommCredentials(newBaseUrl: String, token: String, user: RomMUser?) {
        val stored = userPreferencesRepository.preferences.first()
        val storedKey = stored.rommBaseUrl?.let { normalizeServerKey(it) }
        val newKey = normalizeServerKey(newBaseUrl)
        val storedUserId = stored.rommUserId

        val serverChanged = !storedKey.isNullOrBlank() && storedKey != newKey
        val userChanged = storedUserId != null && user != null && storedUserId != user.id

        if (serverChanged || userChanged) {
            val pendingUploads = saveCacheRepository.get().getPendingSyncCounts().pendingUploads
            if (pendingUploads > 0) {
                Logger.info(TAG, "persistRommCredentials: identity switch blocked, $pendingUploads saves pending upload")
                throw IllegalStateException(
                    "Sync saves first - $pendingUploads pending upload. Switching accounts would delete them."
                )
            }
            val reason = if (serverChanged) "server changed ($storedKey -> $newKey)"
                else "user changed ($storedUserId -> ${user?.id})"
            Logger.info(TAG, "persistRommCredentials: $reason, purging RomM library")
            databaseAdminRepository.get().purgeRomMLibrary()
        }
        userPreferencesRepository.setRomMCredentials(newBaseUrl, token, user?.username, user?.id)
        if (user != null) {
            val stored = userPreferencesRepository.preferences.first()
            rommAccountRepository.get().onSignedIn(
                rommUserId = user.id,
                username = user.username,
                baseUrl = newBaseUrl,
                token = token,
                deviceId = stored.rommDeviceId,
                deviceClientVersion = stored.rommDeviceClientVersion
            )
        }
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting
        val result = attemptConnection(url, token)
        if (result is RomMResult.Error) {
            _connectionState.value = ConnectionState.Failed(result.message)
        }
        return result
    }

    /** Probes a server URL with a throwaway client, leaving the live session untouched. */
    suspend fun probeServerVersion(url: String): RomMResult<String> {
        var lastError: String? = null
        for (candidateUrl in buildUrlsToTry(url)) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val response = createApi(normalizedUrl, null).heartbeat()
                if (response.isSuccessful) {
                    return RomMResult.Success(response.body()?.version ?: "unknown")
                }
                lastError = "Server returned ${response.code()}"
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
            }
        }
        return RomMResult.Error(lastError ?: "Connection failed")
    }

    private suspend fun attemptConnection(
        url: String,
        token: String?,
        registerDevice: Boolean = true
    ): RomMResult<String> = connectMutex.withLock {
        val urlsToTry = buildUrlsToTry(url)
        var lastError: String? = null

        for (candidateUrl in urlsToTry) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val newApi = createApi(normalizedUrl, token)
                val response = newApi.heartbeat()

                if (response.isSuccessful) {
                    baseUrl = normalizedUrl
                    accessToken = token
                    api = newApi
                    saveSyncRepository.get().setApi(api)
                    biosRepository.setApi(api)
                    val body = response.body()
                    val version = body?.version ?: "unknown"
                    val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled, body?.steamGridDbEnabled)
                    _connectionState.value = ConnectionState.Connected(version, capabilities)
                    saveSyncRepository.get().setCapabilities(capabilities)
                    reconnectPending = false
                    Logger.info(TAG, "connect: success at $normalizedUrl, version=$version, capabilities=$capabilities")
                    if (registerDevice && token != null && isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                        registerDeviceIfNeeded()
                    }
                    return RomMResult.Success(normalizedUrl)
                } else {
                    lastError = "Server returned ${response.code()}"
                    Logger.info(TAG, "connect: heartbeat failed at $normalizedUrl with ${response.code()}")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                Logger.info(TAG, "connect: exception at $normalizedUrl: ${e.message}")
            }
        }

        return RomMResult.Error(lastError ?: "Connection failed")
    }

    suspend fun connectWithToken(url: String, token: String): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting
        val connectResult = attemptConnection(url, token, registerDevice = false)
        if (connectResult is RomMResult.Error) {
            _connectionState.value = ConnectionState.Failed(connectResult.message)
            return connectResult
        }

        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            persistRommCredentials(baseUrl, token, fetchCurrentUser(currentApi))

            if (isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                registerDeviceIfNeeded()
            }

            RomMResult.Success(token)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to verify token")
        }
    }

    suspend fun exchangePairingCode(url: String, code: String): RomMResult<String> {
        val urlsToTry = buildUrlsToTry(url)
        var lastError: String? = null

        for (candidateUrl in urlsToTry) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val tempApi = createApi(normalizedUrl, null)
                val response = tempApi.exchangePairingCode(RomMPairingExchangeRequest(code))
                if (response.isSuccessful) {
                    val token = response.body()?.rawToken
                        ?: return RomMResult.Error("No token received")
                    return connectWithToken(normalizedUrl, token)
                } else {
                    lastError = when (response.code()) {
                        404 -> "Invalid or expired pairing code"
                        429 -> "Too many attempts, try again later"
                        else -> "Exchange failed (${response.code()})"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
            }
        }

        return RomMResult.Error(lastError ?: "Pairing failed")
    }

    suspend fun beginDeviceAuth(url: String): RomMResult<RomMDeviceAuthInitResponse> {
        val urlsToTry = buildUrlsToTry(url)
        var lastError: String? = null

        for (candidateUrl in urlsToTry) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val tempApi = createApi(normalizedUrl, null)
                val hb = tempApi.heartbeat()
                if (!hb.isSuccessful) {
                    lastError = "Server returned ${hb.code()}"
                    continue
                }
                val version = hb.body()?.version ?: "unknown"
                if (!RomMCapabilities.from(version).supportsDeviceAuth) {
                    return RomMResult.Error(
                        "Device pairing requires RomM ${RomMCapabilities.DEVICE_AUTH_MIN_VERSION}+ (server is $version)"
                    )
                }

                val request = RomMDeviceAuthInitRequest(
                    clientDeviceIdentifier = clientDeviceIdentifier(),
                    name = deviceDisplayName(),
                    clientVersion = BuildConfig.VERSION_NAME,
                    requestedScopes = DEVICE_AUTH_SCOPES,
                )
                val initResponse = tempApi.deviceAuthInit(request)
                if (initResponse.isSuccessful) {
                    val body = initResponse.body() ?: return RomMResult.Error("Empty pairing response")
                    deviceAuthApi = tempApi
                    deviceAuthBaseUrl = normalizedUrl
                    Logger.info(TAG, "beginDeviceAuth: init ok at $normalizedUrl, userCode=${body.userCode}")
                    return RomMResult.Success(
                        body.copy(
                            verificationPath = absolutizeUrl(body.verificationPath, normalizedUrl),
                            verificationPathComplete = absolutizeUrl(body.verificationPathComplete, normalizedUrl)
                        )
                    )
                } else {
                    lastError = when (initResponse.code()) {
                        429 -> "Too many attempts, try again later"
                        else -> "Pairing init failed (${initResponse.code()})"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                Logger.info(TAG, "beginDeviceAuth: exception at $normalizedUrl: ${e.message}")
            }
        }

        return RomMResult.Error(lastError ?: "Pairing failed")
    }

    /**
     * [activateOnSuccess] false pairs an ADDITIONAL account: the row is stored but the device
     * stays signed in as whoever it was. Activating on pair would skip the switch teardown and
     * leave the new account playing on the previous one's saves.
     */
    suspend fun pollDeviceAuthOnce(
        deviceCode: String,
        activateOnSuccess: Boolean = true
    ): DeviceAuthPoll {
        val authApi = deviceAuthApi ?: return DeviceAuthPoll.Failed("Pairing not started")
        val base = deviceAuthBaseUrl ?: return DeviceAuthPoll.Failed("Pairing not started")
        return try {
            val response = authApi.deviceAuthToken(RomMDeviceAuthTokenRequest(deviceCode))
            if (response.isSuccessful) {
                val body = response.body() ?: return DeviceAuthPoll.Failed("Empty token response")
                if (activateOnSuccess) {
                    finalizeDeviceAuth(base, body)
                    DeviceAuthPoll.Approved(body.accessToken)
                } else {
                    val accountId = registerAdditionalAccount(base, body)
                        ?: return DeviceAuthPoll.Failed("Could not identify the paired user")
                    DeviceAuthPoll.AddedAccount(accountId)
                }
            } else {
                when (parseDetail(response.errorBody()?.string())) {
                    "authorization_pending" -> DeviceAuthPoll.Pending
                    "slow_down" -> DeviceAuthPoll.SlowDown
                    "access_denied" -> DeviceAuthPoll.Denied
                    "expired_token" -> DeviceAuthPoll.Expired
                    else -> DeviceAuthPoll.Failed("Pairing failed (${response.code()})")
                }
            }
        } catch (e: Exception) {
            DeviceAuthPoll.Failed(e.message ?: "Connection failed")
        }
    }

    fun cancelDeviceAuth() {
        deviceAuthApi = null
        deviceAuthBaseUrl = null
    }

    private suspend fun registerAdditionalAccount(
        base: String,
        body: RomMDeviceAuthTokenResponse
    ): Long? {
        val newApi = createApi(base, body.accessToken)
        val user = fetchCurrentUser(newApi) ?: return null
        val accountId = rommAccountRepository.get().registerAdditional(
            rommUserId = user.id,
            username = user.username,
            baseUrl = base,
            token = body.accessToken,
            deviceId = body.deviceId,
            deviceClientVersion = BuildConfig.VERSION_NAME
        )
        deviceAuthApi = null
        deviceAuthBaseUrl = null
        Logger.info(TAG, "registerAdditionalAccount: stored account $accountId for user ${user.id} without activating")
        return accountId
    }

    private suspend fun finalizeDeviceAuth(base: String, body: RomMDeviceAuthTokenResponse) {
        val newApi = createApi(base, body.accessToken)
        val heartbeat = try { newApi.heartbeat() } catch (_: Exception) { null }
        val version = heartbeat?.body()?.version ?: "unknown"
        val capabilities = RomMCapabilities.from(version, heartbeat?.body()?.libretroApiEnabled, heartbeat?.body()?.steamGridDbEnabled)

        persistRommCredentials(base, body.accessToken, fetchCurrentUser(newApi))
        userPreferencesRepository.setRommDeviceId(body.deviceId, BuildConfig.VERSION_NAME)
        rommAccountRepository.get().recordDeviceRegistration(body.deviceId, BuildConfig.VERSION_NAME)

        baseUrl = base
        accessToken = body.accessToken
        api = newApi
        cachedDeviceId = body.deviceId
        saveSyncRepository.get().setApi(newApi)
        biosRepository.setApi(newApi)
        saveSyncRepository.get().setCapabilities(capabilities)
        saveSyncRepository.get().setDeviceId(body.deviceId)
        _connectionState.value = ConnectionState.Connected(version, capabilities)

        deviceAuthApi = null
        deviceAuthBaseUrl = null
        Logger.info(TAG, "finalizeDeviceAuth: connected, deviceId=${body.deviceId}, version=$version")
    }

    private fun parseDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try { detailAdapter.fromJson(body)?.detail } catch (_: Exception) { null }
    }

    @SuppressLint("HardwareIds")
    private fun clientDeviceIdentifier(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) "argosy-$androidId" else "argosy-${java.util.UUID.randomUUID()}"
    }

    private fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun disconnect() {
        reconnectPending = false
        reconnectJob?.cancel()
        reconnectJob = null
        api = null
        biosRepository.setApi(null)
        saveSyncRepository.get().setCapabilities(RomMCapabilities.NONE)
        accessToken = null
        baseUrl = ""
        cachedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Forgets the stored RomM identity and tears down the live session. Library rows and
     * downloaded content are left alone; signing back in as the same user reuses them, and
     * signing in as a different user purges via [persistRommCredentials].
     */
    suspend fun signOut() {
        disconnect()
        rommAccountRepository.get().activeAccount()?.let { rommAccountRepository.get().forget(it.id) }
        userPreferencesRepository.clearRomMCredentials()
        Logger.info(TAG, "signOut: cleared stored RomM identity")
    }

    /**
     * Repoints the live session at whichever account is now stored as active.
     *
     * The teardown of the previous session runs first and unconditionally: leaving the old api
     * object, token or device id in place while the stored identity says otherwise is the
     * split-brain that makes one account's uploads land under the other's device.
     */
    suspend fun rebindToActiveAccount(): RomMResult<String> {
        disconnect()
        val prefs = userPreferencesRepository.preferences.first()
        val url = prefs.rommBaseUrl
        if (url.isNullOrBlank()) {
            Logger.info(TAG, "rebindToActiveAccount: no stored server for the active account")
            return RomMResult.Error("No server configured for this account")
        }
        cachedDeviceId = prefs.rommDeviceId
        saveSyncRepository.get().setDeviceId(cachedDeviceId)
        val result = attemptConnection(url, prefs.rommToken)
        if (result is RomMResult.Error) {
            Logger.info(TAG, "rebindToActiveAccount: offline after swap, scheduling reconnect")
            scheduleReconnect()
        }
        return result
    }

    suspend fun checkConnection() {
        val currentApi = api
        if (currentApi == null) {
            Logger.info(TAG, "checkConnection: api is null, initializing")
            initialize()
            return
        }

        try {
            val response = currentApi.heartbeat()
            if (response.isSuccessful) {
                val body = response.body()
                val version = body?.version ?: "unknown"
                val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled, body?.steamGridDbEnabled)
                _connectionState.value = ConnectionState.Connected(version, capabilities)
                saveSyncRepository.get().setCapabilities(capabilities)
                reconnectPending = false
                Logger.info(TAG, "checkConnection: connected, version=$version")
            } else {
                Logger.info(TAG, "checkConnection: heartbeat failed with ${response.code()}, scheduling reconnect")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Logger.info(TAG, "checkConnection: exception: ${e.message}, scheduling reconnect")
            scheduleReconnect()
        }
    }

    private suspend fun registerDeviceIfNeeded() {
        val currentApi = api ?: return
        val clientVersion = BuildConfig.VERSION_NAME

        val prefs = userPreferencesRepository.preferences.first()
        val existingDeviceId = prefs.rommDeviceId
        val existingClientVersion = prefs.rommDeviceClientVersion

        if (existingDeviceId != null && existingClientVersion == clientVersion) {
            cachedDeviceId = existingDeviceId
            saveSyncRepository.get().setDeviceId(existingDeviceId)
            Logger.info(TAG, "Device already registered: $existingDeviceId")
            return
        }

        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val caps = getCapabilities()
            val registration = RomMDeviceRegistration(
                name = deviceName,
                clientVersion = clientVersion,
                syncMode = if (caps.supportsDeviceSyncMode) "api" else null
            )

            if (existingDeviceId != null) {
                val updateResponse = currentApi.updateDevice(existingDeviceId, registration)
                if (updateResponse.isSuccessful) {
                    val device = updateResponse.body()
                    if (device != null) {
                        cachedDeviceId = device.id
                        saveSyncRepository.get().setDeviceId(device.id)
                        userPreferencesRepository.setRommDeviceId(device.id, clientVersion)
                        rommAccountRepository.get().recordDeviceRegistration(device.id, clientVersion)
                        Logger.info(TAG, "Device updated: ${device.id}")
                        return
                    }
                }
            }

            val response = currentApi.registerDevice(registration)
            if (response.isSuccessful) {
                val device = response.body()
                if (device != null) {
                    cachedDeviceId = device.deviceId
                    saveSyncRepository.get().setDeviceId(device.deviceId)
                    userPreferencesRepository.setRommDeviceId(device.deviceId, clientVersion)
                    rommAccountRepository.get().recordDeviceRegistration(device.deviceId, clientVersion)
                    Logger.info(TAG, "Device registered: ${device.deviceId}")
                }
            } else {
                Logger.error(TAG, "Device registration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Device registration error: ${e.message}")
        }
    }

    private fun absolutizeUrl(value: String, base: String): String {
        if (value.isBlank()) return value
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return base.trimEnd('/') + "/" + value.trimStart('/')
    }

    private fun buildUrlsToTry(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return listOf(trimmed)
        }

        val hostPart = trimmed.removePrefix("//")
        val isIpAddress = hostPart.split("/").first().split(":").first().let { host ->
            host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) ||
                host == "localhost"
        }

        return if (isIpAddress) {
            listOf("http://$hostPart", "https://$hostPart")
        } else {
            listOf("https://$hostPart", "http://$hostPart")
        }
    }

    fun createApi(baseUrl: String, token: String?): RomMApi = apiFactory.create(baseUrl, token)
}
