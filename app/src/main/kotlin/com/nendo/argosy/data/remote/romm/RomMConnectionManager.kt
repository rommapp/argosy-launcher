package com.nendo.argosy.data.remote.romm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.local.entity.serverInstanceKey
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.sync.UnflushedQueuePolicy
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
import com.nendo.argosy.data.remote.ssl.isCertificateTrustFailure
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
private const val CANDIDATE_PROBE_TIMEOUT_SECONDS = 5L

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
    /**
     * [retryable] false means no later poll can succeed either - the approval record was already
     * consumed server-side, or the flow was never started. Retryable failures are transient
     * transport or gateway noise and the caller is expected to keep polling.
     */
    data class Failed(val message: String, val retryable: Boolean = true) : DeviceAuthPoll()
}

@Singleton
class RomMConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val biosRepository: BiosRepository,
    private val rommAccountRepository: dagger.Lazy<com.nendo.argosy.data.repository.RomMAccountRepository>,
    private val accountRemovalService: dagger.Lazy<com.nendo.argosy.data.sync.AccountRemovalService>,
    private val syncCoordinator: dagger.Lazy<com.nendo.argosy.data.sync.SyncCoordinator>,
    private val retroAchievementsRepository: dagger.Lazy<com.nendo.argosy.data.repository.RetroAchievementsRepository>,
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
        rommAccountRepository.get().adoptLegacyCredentialsIfNeeded()
        val stored = storedConnection()
        Logger.info(TAG, "initialize: candidates=${stored.candidates.map { it.take(30) }}, hasToken=${stored.token != null}")
        cachedDeviceId = stored.deviceId
        if (cachedDeviceId != null) {
            saveSyncRepository.get().setDeviceId(cachedDeviceId)
        }
        if (stored.candidates.isEmpty()) return
        registerNetworkCallback()
        val result = attemptConnection(stored.candidates, stored.token)
        Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
        if (result is RomMResult.Error) scheduleReconnect() else backfillIdentityIfMissing(stored.token)
    }

    private data class StoredConnection(
        val candidates: List<String>,
        val token: String?,
        val deviceId: String?
    )

    private suspend fun storedConnection(): StoredConnection {
        val prefs = userPreferencesRepository.preferences.first()
        val candidates = rommAccountRepository.get().activeAddresses()
            .ifEmpty { listOfNotNull(prefs.rommBaseUrl?.takeIf { it.isNotBlank() }) }
        return StoredConnection(candidates, prefs.rommToken, prefs.rommDeviceId)
    }

    /**
     * Gives an install that predates account identity a user id and an account row.
     *
     * Such an install reconnects with a stored token and never passes through a pairing path, so
     * nothing would ever record who it belongs to and every owner stamp would stay null. Only the
     * absence of an id triggers this, so it costs one request once.
     */
    private suspend fun backfillIdentityIfMissing(token: String?) {
        if (token.isNullOrBlank()) return
        val stored = userPreferencesRepository.preferences.first()
        if (stored.rommUserId != null) return
        val currentApi = api ?: return
        val user = fetchCurrentUser(currentApi) ?: return
        Logger.info(TAG, "backfillIdentityIfMissing: adopting user ${user.id} for an install with no stored identity")
        persistRommCredentials(baseUrl, token, user)
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
                val stored = storedConnection()
                if (stored.candidates.isEmpty()) return@launch
                Logger.info(TAG, "scheduleReconnect: retrying after ${backoffMs}ms")
                if (attemptConnection(stored.candidates, stored.token) is RomMResult.Success) return@launch
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
                scope.launch {
                    Logger.info(TAG, "network available, re-evaluating addresses (connected=${isConnected()})")
                    reconnectWithStoredAddresses()
                }
            }
        })
    }

    /**
     * Runs the stored LAN-then-WAN candidate pass again without tearing the live session down,
     * so an address that just started answering (new network, edited address) takes over now.
     * A pass that fails everywhere leaves the current session as it is and starts the backoff.
     */
    suspend fun reconnectWithStoredAddresses(): RomMResult<String> {
        val stored = storedConnection()
        if (stored.candidates.isEmpty()) return RomMResult.Error("No server configured")
        val result = attemptConnection(stored.candidates, stored.token)
        if (result is RomMResult.Error) scheduleReconnect()
        return result
    }

    private suspend fun fetchCurrentUser(target: RomMApi): RomMUser? = try {
        val response = target.getCurrentUser()
        if (response.isSuccessful) response.body() else null
    } catch (_: Exception) {
        null
    }

    private suspend fun requireSameInstance(newBaseUrl: String) {
        val accounts = rommAccountRepository.get().accounts()
        if (accounts.isEmpty()) return
        val known = accounts.flatMap { it.addressCandidates() }.map(::serverInstanceKey).toSet()
        val newKey = serverInstanceKey(newBaseUrl)
        if (newKey in known) return
        val reference = accounts.firstOrNull { it.isActive } ?: accounts.first()
        if (identifiesStoredUser(newBaseUrl, reference)) return
        Logger.info(TAG, "persistRommCredentials: refused sign-in to $newKey, device is registered to ${known.joinToString()}")
        throw IllegalStateException(
            "This device is already signed in to a different RomM server. Remove the existing accounts before connecting to another server."
        )
    }

    private suspend fun identifiesStoredUser(normalizedUrl: String, account: RomMAccountEntity): Boolean {
        val user = fetchCurrentUser(createProbeApi(normalizedUrl, account.token)) ?: return false
        return user.id == account.rommUserId
    }

    /**
     * Checks that [url] reaches the instance the active account is signed in to, without touching
     * the live session. The saved token has to identify the saved user there; a server that is
     * merely alive does not qualify. Returns the normalized URL the caller should store.
     */
    suspend fun validateAddress(url: String): RomMResult<String> {
        val account = rommAccountRepository.get().activeAccount()
            ?: return RomMResult.Error("Not signed in")
        var lastError: String? = null
        var lastKind: RomMErrorKind? = null
        for (candidateUrl in buildUrlsToTry(url)) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val response = createProbeApi(normalizedUrl).heartbeat()
                if (!response.isSuccessful) {
                    lastError = "Server returned ${response.code()}"
                    lastKind = null
                    continue
                }
                if (identifiesStoredUser(normalizedUrl, account)) return RomMResult.Success(normalizedUrl)
                lastError = "That address is not the server this account is signed in to"
                lastKind = null
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                lastKind = if (e.isCertificateTrustFailure()) RomMErrorKind.UNTRUSTED_CERTIFICATE else null
            }
        }
        return RomMResult.Error(lastError ?: "Connection failed", kind = lastKind)
    }

    private suspend fun persistRommCredentials(newBaseUrl: String, token: String, user: RomMUser?) {
        requireSameInstance(newBaseUrl)
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
        val result = attemptConnection(listOf(url), token, recordAddress = false)
        if (result is RomMResult.Error) {
            _connectionState.value = ConnectionState.Failed(result.message)
        }
        return result
    }

    /** Probes a server URL with a throwaway client, leaving the live session untouched. */
    suspend fun probeServerVersion(url: String): RomMResult<String> {
        var lastError: String? = null
        var lastKind: RomMErrorKind? = null
        for (candidateUrl in buildUrlsToTry(url)) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val response = createApi(normalizedUrl, null).heartbeat()
                if (response.isSuccessful) {
                    return RomMResult.Success(response.body()?.version ?: "unknown")
                }
                lastError = "Server returned ${response.code()}"
                lastKind = null
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                lastKind = if (e.isCertificateTrustFailure()) {
                    RomMErrorKind.UNTRUSTED_CERTIFICATE
                } else {
                    null
                }
            }
        }
        return RomMResult.Error(lastError ?: "Connection failed", kind = lastKind)
    }

    private suspend fun attemptConnection(
        candidates: List<String>,
        token: String?,
        registerDevice: Boolean = true,
        recordAddress: Boolean = true
    ): RomMResult<String> = connectMutex.withLock {
        var lastFailure: RomMResult.Error? = null

        for (address in candidates) {
            for (candidateUrl in buildUrlsToTry(address)) {
                val normalizedUrl = candidateUrl.trimEnd('/') + "/"
                when (val outcome = connectAt(normalizedUrl, token, registerDevice)) {
                    is RomMResult.Success -> {
                        if (recordAddress && token != null) {
                            userPreferencesRepository.setRomMCredentials(normalizedUrl, token)
                        }
                        return outcome
                    }
                    is RomMResult.Error -> lastFailure = outcome
                }
            }
        }

        return lastFailure ?: RomMResult.Error("Connection failed")
    }

    private suspend fun connectAt(
        normalizedUrl: String,
        token: String?,
        registerDevice: Boolean
    ): RomMResult<String> {
        try {
            val response = createProbeApi(normalizedUrl).heartbeat()
            if (!response.isSuccessful) {
                Logger.info(TAG, "connect: heartbeat failed at $normalizedUrl with ${response.code()}")
                return RomMResult.Error("Server returned ${response.code()}")
            }
            val newApi = createApi(normalizedUrl, token)
            if (token != null && !isTokenAccepted(newApi)) {
                Logger.info(TAG, "connect: server live at $normalizedUrl but the token was rejected")
                return RomMResult.Error("Sign in again")
            }
            if (baseUrl.isNotEmpty() && baseUrl != normalizedUrl) {
                Logger.info(TAG, "connect: moving from $baseUrl to $normalizedUrl")
            }
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
        } catch (e: Exception) {
            Logger.info(TAG, "connect: exception at $normalizedUrl: ${e.message}")
            val kind = if (e.isCertificateTrustFailure()) RomMErrorKind.UNTRUSTED_CERTIFICATE else null
            return RomMResult.Error(e.message ?: "Connection failed", kind = kind)
        }
    }

    suspend fun connectWithToken(url: String, token: String): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting
        val connectResult = attemptConnection(listOf(url), token, registerDevice = false, recordAddress = false)
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
                val hb = tempApi.heartbeat()
                if (!hb.isSuccessful) {
                    lastError = "Server returned ${hb.code()}"
                    continue
                }
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
        val authApi = deviceAuthApi
            ?: return DeviceAuthPoll.Failed("Pairing not started", retryable = false)
        val base = deviceAuthBaseUrl
            ?: return DeviceAuthPoll.Failed("Pairing not started", retryable = false)
        return try {
            val response = authApi.deviceAuthToken(RomMDeviceAuthTokenRequest(deviceCode))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return DeviceAuthPoll.Failed("Empty token response", retryable = false)
                try {
                    if (activateOnSuccess) {
                        finalizeDeviceAuth(base, body)
                        DeviceAuthPoll.Approved(body.accessToken)
                    } else {
                        val accountId = registerAdditionalAccount(base, body)
                            ?: return DeviceAuthPoll.Failed(
                                "Could not identify the paired user",
                                retryable = false
                            )
                        DeviceAuthPoll.AddedAccount(accountId)
                    }
                } catch (e: Exception) {
                    Logger.info(TAG, "pollDeviceAuthOnce: approval landed but sign-in failed: ${e.message}")
                    DeviceAuthPoll.Failed(
                        e.message ?: "Approved, but signing in failed",
                        retryable = false
                    )
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
        saveSyncRepository.get().setApi(null)
        saveSyncRepository.get().setCapabilities(RomMCapabilities.NONE)
        accessToken = null
        baseUrl = ""
        cachedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Signs the active account out: its own rows, cached saves, preferences and credentials go,
     * and nothing another account or the shared library owns is touched.
     *
     * This is removal aimed at the account that happens to be live, so it runs through the same
     * service rather than a second, weaker path - that is what brings the switch-in-progress
     * guard and the unflushed-work policy with it.
     *
     * The queue is drained first, while the token still authenticates. Everything the account
     * cached and never sent can only be sent as that account, so an upload deferred past sign-out
     * is an upload that never happens; refusing here is what keeps the local copy that is still
     * the only copy.
     *
     * A refusal is a question, not a failure: the caller raises it and [discardUnflushed] carries
     * the answer back. Discarding on the app's own initiative, which is what an unreachable
     * server used to trigger, threw that work away without asking.
     */
    suspend fun signOut(discardUnflushed: Boolean = false): AccountRemovalResult {
        val active = rommAccountRepository.get().activeAccount()
            ?: run {
                disconnect()
                userPreferencesRepository.clearRomMCredentials()
                return AccountRemovalResult.UnknownAccount
            }
        val drained = syncCoordinator.get().processQueue()
        Logger.info(TAG, "signOut: drained queued work before removal, result=$drained")
        val policy = if (discardUnflushed) {
            UnflushedQueuePolicy.DISCARD
        } else {
            UnflushedQueuePolicy.REFUSE
        }
        val result = accountRemovalService.get().remove(active.id, policy)
        if (result is AccountRemovalResult.Refused || result is AccountRemovalResult.SwitchInProgress) {
            Logger.info(TAG, "signOut: not signed out, $result")
            return result
        }
        disconnect()
        userPreferencesRepository.clearRomMCredentials()
        retroAchievementsRepository.get().syncRetroArchCredentials()
        Logger.info(TAG, "signOut: removed user ${active.rommUserId} and cleared the stored identity")
        return result
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
        val stored = storedConnection()
        if (stored.candidates.isEmpty()) {
            Logger.info(TAG, "rebindToActiveAccount: no stored server for the active account")
            return RomMResult.Error("No server configured for this account")
        }
        cachedDeviceId = stored.deviceId
        saveSyncRepository.get().setDeviceId(cachedDeviceId)
        val result = attemptConnection(stored.candidates, stored.token)
        if (result is RomMResult.Error) {
            Logger.info(TAG, "rebindToActiveAccount: offline after swap, scheduling reconnect")
            scheduleReconnect()
        }
        return result
    }

    /**
     * Whether the stored token is still accepted.
     *
     * `api/heartbeat` answers liveness and is unauthenticated, so it returns 200 for a token the
     * server has revoked. Reading it as proof of a session left a revoked account permanently
     * "connected": every request 401s while nothing ever leaves the connected state.
     */
    private suspend fun isTokenAccepted(candidate: RomMApi): Boolean = try {
        candidate.getCurrentUser().isSuccessful
    } catch (e: Exception) {
        Logger.info(TAG, "isTokenAccepted: identity call failed: ${e.message}")
        false
    }

    /**
     * Re-checks the live session with the token rather than with liveness.
     *
     * A rejected token is reported as offline: there is nothing the app can do about it, and
     * treating it as connected is what hid it.
     */
    suspend fun checkConnection() {
        val currentApi = api
        if (currentApi == null) {
            Logger.info(TAG, "checkConnection: api is null, initializing")
            initialize()
            return
        }

        try {
            if (accessToken != null && !isTokenAccepted(currentApi)) {
                Logger.info(TAG, "checkConnection: token rejected, scheduling reconnect")
                scheduleReconnect()
                return
            }

            val known = _connectionState.value as? ConnectionState.Connected
            if (known != null) {
                reconnectPending = false
                Logger.info(TAG, "checkConnection: session still valid, version=${known.version}")
                return
            }

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

    /**
     * Re-runs device registration after the server has answered that the stored device id is
     * not registered to this user. Updates the stored id in place when the server accepts it and
     * registers a fresh one otherwise.
     */
    suspend fun reregisterDevice() {
        if (!isConnected() || !isVersionAtLeast(MIN_DEVICE_API_VERSION)) return
        registerDeviceIfNeeded(trustStoredRegistration = false)
    }

    private suspend fun registerDeviceIfNeeded(trustStoredRegistration: Boolean = true) {
        val currentApi = api ?: return
        val clientVersion = BuildConfig.VERSION_NAME

        val prefs = userPreferencesRepository.preferences.first()
        val existingDeviceId = prefs.rommDeviceId
        val existingClientVersion = prefs.rommDeviceClientVersion

        if (trustStoredRegistration && existingDeviceId != null && existingClientVersion == clientVersion) {
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
        if (hasScheme(trimmed)) return listOf(trimmed)
        val hostPart = trimmed.removePrefix("//")
        val preferred = withDefaultScheme(hostPart)
        val fallback = if (preferred.startsWith("http://")) "https://$hostPart" else "http://$hostPart"
        return listOf(preferred, fallback)
    }

    companion object {
        private val IPV4 = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

        private fun hasScheme(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")

        /**
         * A bare address gains the scheme the app tries first for it: plain http for an IP or
         * localhost, https for a hostname. Addresses that already carry a scheme are returned as is.
         */
        fun withDefaultScheme(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isBlank() || hasScheme(trimmed)) return trimmed
            val hostPart = trimmed.removePrefix("//")
            val host = hostPart.split("/").first().split(":").first()
            val plain = host.matches(IPV4) || host == "localhost"
            return if (plain) "http://$hostPart" else "https://$hostPart"
        }
    }

    fun createApi(baseUrl: String, token: String?): RomMApi = apiFactory.create(baseUrl, token)

    private fun createProbeApi(baseUrl: String, token: String? = null): RomMApi =
        apiFactory.create(baseUrl, token, probeTimeoutSeconds = CANDIDATE_PROBE_TIMEOUT_SECONDS)
}
