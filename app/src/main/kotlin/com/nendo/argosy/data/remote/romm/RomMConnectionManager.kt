package com.nendo.argosy.data.remote.romm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.nendo.argosy.data.remote.ssl.isCertificateTrustFailure
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMConnectionManager"
private const val MIN_DEVICE_API_VERSION = "4.7.0"
private const val CANDIDATE_PROBE_TIMEOUT_SECONDS = 5L
private const val TOKEN_RECHECK_WINDOW_MS = 60_000L
private const val RECENT_CONNECT_MS = 5_000L

private class RomMConnectionFailure(val kind: RomMErrorKind) : Exception(kind.name)

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

private const val PLAYLISTS_READ_SCOPE = "playlists.read"

private fun deviceAuthScopesFor(capabilities: RomMCapabilities): List<String> =
    if (capabilities.supportsMusicPlaylists) DEVICE_AUTH_SCOPES + PLAYLISTS_READ_SCOPE else DEVICE_AUTH_SCOPES

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
    data class Failed(val message: String, val retryable: Boolean = true, val kind: RomMErrorKind? = null) : DeviceAuthPoll()
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
    private val _baseUrl = MutableStateFlow("")
    val baseUrlState: StateFlow<String> = _baseUrl.asStateFlow()
    @get:JvmName("currentBaseUrl")
    @set:JvmName("assignBaseUrl")
    private var baseUrl: String
        get() = _baseUrl.value
        set(value) {
            _baseUrl.value = value
        }
    private var accessToken: String? = null
    private val sessionGeneration = AtomicLong()
    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceIdState: StateFlow<String?> = _deviceId.asStateFlow()
    private var cachedDeviceId: String?
        get() = _deviceId.value
        set(value) {
            _deviceId.value = value
        }
    private var deviceAuthApi: RomMApi? = null
    private var deviceAuthBaseUrl: String? = null
    private val detailAdapter by lazy { Moshi.Builder().build().adapter(RomMDetailResponse::class.java) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val initializeGate = Mutex()
    private var initializing: Deferred<Boolean>? = null
    private var connectedWith: StoredConnection? = null
    private var reconnectJob: Job? = null
    private var networkCallbackRegistered = false
    @Volatile private var reconnectPending = false

    /**
     * The signed-in user as the server last returned it, refreshed on every connect and token
     * check; null while disconnected.
     */
    @Volatile
    var currentUser: RomMUser? = null
        private set

    @Volatile private var lastTokenVerifiedAt = 0L
    @Volatile private var rememberedSnapshots: Boolean? = null
    @Volatile private var lastConnectedAt = 0L

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _capabilityRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val capabilityRefreshes: SharedFlow<Unit> = _capabilityRefreshes.asSharedFlow()

    fun getApi(): RomMApi? = api

    fun getBaseUrl(): String = baseUrl

    fun getAccessToken(): String? = accessToken

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        isConnected() && baseUrl.isNotEmpty() && apiFactory.reachability.isReachable(baseUrl)
    }

    fun responseMark(): Long = apiFactory.reachability.mark()

    fun answeredSince(mark: Long): Boolean =
        baseUrl.isNotEmpty() && apiFactory.reachability.answeredSince(baseUrl, mark)

    fun getDeviceId(): String? = cachedDeviceId

    fun getConnectedVersion(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.version
    }

    fun getCapabilities(): RomMCapabilities {
        return (_connectionState.value as? ConnectionState.Connected)?.capabilities
            ?: RomMCapabilities.NONE
    }

    /**
     * Whether saves sync through snapshots: the live server's capability while connected, else
     * what the stored server last reported. False for a server never reached.
     */
    fun snapshotsEnabled(): Boolean =
        (_connectionState.value as? ConnectionState.Connected)?.capabilities?.supportsSnapshots
            ?: rememberedSnapshots
            ?: false

    private suspend fun loadRememberedSnapshots(candidates: List<String>) {
        rememberedSnapshots = candidates.firstNotNullOfOrNull {
            userPreferencesRepository.getRommSnapshotSupport(serverInstanceKey(it))
        }
    }

    private suspend fun rememberSnapshots(url: String, capabilities: RomMCapabilities) {
        rememberedSnapshots = capabilities.supportsSnapshots
        userPreferencesRepository.setRommSnapshotSupport(serverInstanceKey(url), capabilities.supportsSnapshots)
    }

    fun isVersionAtLeast(minVersion: String): Boolean {
        val current = getConnectedVersion() ?: return false
        return RomMCapabilities.compareVersions(RomMCapabilities.comparableVersion(current), minVersion) >= 0
    }

    /**
     * Connects with the stored credentials. Overlapping calls share one attempt, and a call made
     * while already connected with the same credentials returns without reconnecting unless
     * [reprobe] asks for the addresses to be checked again. Returns whether this call established
     * a new connection.
     */
    suspend fun initialize(reprobe: Boolean = false): Boolean {
        val attempt = initializeGate.withLock {
            initializing?.takeIf { it.isActive }
                ?: scope.async { initializeOnce(reprobe) }.also { initializing = it }
        }
        return attempt.await()
    }

    private suspend fun initializeOnce(reprobe: Boolean): Boolean {
        rommAccountRepository.get().adoptLegacyCredentialsIfNeeded()
        val stored = storedConnection()
        Logger.info(TAG, "initialize: candidates=${stored.candidates.map { it.take(30) }}, hasToken=${stored.token != null}, reprobe=$reprobe")
        cachedDeviceId = stored.deviceId
        if (cachedDeviceId != null) {
            saveSyncRepository.get().setDeviceId(cachedDeviceId)
        }
        if (stored.candidates.isEmpty()) return false
        if (!reprobe && isConnected() && stored == connectedWith) return false
        loadRememberedSnapshots(stored.candidates)
        registerNetworkCallback()
        val result = attemptConnection(stored.candidates, stored.token)
        Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
        if (result is RomMResult.Error) {
            scheduleReconnect()
            return false
        }
        connectedWith = stored
        backfillIdentityIfMissing(stored.token)
        return true
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

    private suspend fun backfillIdentityIfMissing(token: String?) {
        if (token.isNullOrBlank()) return
        val stored = userPreferencesRepository.preferences.first()
        if (stored.rommUserId != null) return
        if (stored.rommToken != token || accessToken != token) return
        val user = currentUser ?: return
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
                    awaitConnectInFlight()
                    if (isConnected() && SystemClock.elapsedRealtime() - lastConnectedAt < RECENT_CONNECT_MS) {
                        Logger.debug(TAG, "network available, a connect just finished; keeping it")
                        return@launch
                    }
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

    private suspend fun awaitConnectInFlight() = connectMutex.withLock { }

    private suspend fun fetchCurrentUser(target: RomMApi): RomMUser? = try {
        val response = target.getCurrentUser()
        if (response.isSuccessful) response.body() else null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
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
    suspend fun validateAddress(url: String): RomMResult<String> = withContext(Dispatchers.IO) {
        validateAddressOnIo(url)
    }

    private suspend fun validateAddressOnIo(url: String): RomMResult<String> {
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
        if (user == null) throw RomMConnectionFailure(RomMErrorKind.IDENTITY_UNAVAILABLE)
        val generation = sessionGeneration.get()
        val session = profileSession()
        requireSameInstance(newBaseUrl)
        if (generation != sessionGeneration.get() || session != profileSession()) {
            throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
        }
        userPreferencesRepository.setRomMCredentials(newBaseUrl, token, user.username, user.id, user.avatarPath)
        val stored = userPreferencesRepository.preferences.first()
        rommAccountRepository.get().onSignedIn(
            rommUserId = user.id,
            username = user.username,
            baseUrl = newBaseUrl,
            token = token,
            deviceId = stored.rommDeviceId,
            deviceClientVersion = stored.rommDeviceClientVersion,
            avatarPath = user.avatarPath
        )
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
    suspend fun probeServerVersion(url: String): RomMResult<String> = withContext(Dispatchers.IO) {
        probeServerVersionOnIo(url)
    }

    private suspend fun probeServerVersionOnIo(url: String): RomMResult<String> {
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
    ): RomMResult<String> = withContext(Dispatchers.IO) {
        attemptConnectionOnIo(candidates, token, registerDevice, recordAddress)
    }

    private suspend fun attemptConnectionOnIo(
        candidates: List<String>,
        token: String?,
        registerDevice: Boolean,
        recordAddress: Boolean
    ): RomMResult<String> = connectMutex.withLock {
        val generation = sessionGeneration.get()
        val session = profileSession()
        var lastFailure: RomMResult.Error? = null

        for (address in candidates) {
            for (candidateUrl in buildUrlsToTry(address)) {
                val normalizedUrl = candidateUrl.trimEnd('/') + "/"
                when (val outcome = connectAt(normalizedUrl, token, registerDevice, recordAddress)) {
                    is RomMResult.Success -> {
                        return outcome
                    }
                    is RomMResult.Error -> {
                        if (generation != sessionGeneration.get() || session != profileSession()) {
                            return RomMResult.Error(RomMErrorKind.SESSION_CHANGED.name, kind = RomMErrorKind.SESSION_CHANGED)
                        }
                        lastFailure = outcome
                    }
                }
            }
        }

        return lastFailure ?: RomMResult.Error("Connection failed")
    }

    private suspend fun connectAt(
        normalizedUrl: String,
        token: String?,
        registerDevice: Boolean,
        recordAddress: Boolean
    ): RomMResult<String> {
        val generation = sessionGeneration.get()
        val profileSession = profileSession()
        try {
            val response = createProbeApi(normalizedUrl).heartbeat()
            if (!response.isSuccessful) {
                Logger.info(TAG, "connect: heartbeat failed at $normalizedUrl with ${response.code()}")
                return RomMResult.Error("Server returned ${response.code()}")
            }
            apiFactory.reachability.recordReachable(normalizedUrl)
            val newApi = createApi(normalizedUrl, token)
            val user = if (token != null) {
                verifiedUser(newApi) ?: run {
                    Logger.info(TAG, "connect: server live at $normalizedUrl but the token was rejected")
                    return RomMResult.Error("Sign in again")
                }
            } else null
            if (sessionGeneration.get() != generation) throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            if (profileSession != profileSession()) throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            if (recordAddress && (!profileSession.matchesAccount || profileSession.token != token)) {
                throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            }
            if (recordAddress && user != null && profileSession.userId != null && profileSession.userId != user.id) {
                throw RomMConnectionFailure(RomMErrorKind.IDENTITY_UNAVAILABLE)
            }
            if (recordAddress && token != null && user != null && token == profileSession.token) {
                refreshProfile(profileSession, token, user, normalizedUrl)
                if (profileSession.copy(baseUrl = normalizedUrl) != profileSession()) throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            }
            if (sessionGeneration.get() != generation) throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            if (baseUrl.isNotEmpty() && baseUrl != normalizedUrl) {
                Logger.info(TAG, "connect: moving from $baseUrl to $normalizedUrl")
            }
            baseUrl = normalizedUrl
            accessToken = token
            api = newApi
            currentUser = user
            lastTokenVerifiedAt = if (user == null) 0L else SystemClock.elapsedRealtime()
            saveSyncRepository.get().setApi(api)
            biosRepository.setApi(api)
            val body = response.body()
            val version = body?.version ?: "unknown"
            val capabilities = RomMCapabilities.from(
                version, body?.libretroApiEnabled, body?.steamGridDbEnabled, body?.snapshotsEnabled == true
            )
            _connectionState.value = ConnectionState.Connected(version, capabilities)
            saveSyncRepository.get().setCapabilities(capabilities)
            rememberSnapshots(normalizedUrl, capabilities)
            _capabilityRefreshes.tryEmit(Unit)
            reconnectPending = false
            lastConnectedAt = SystemClock.elapsedRealtime()
            Logger.info(TAG, "connect: success at $normalizedUrl, version=$version, capabilities=$capabilities")
            if (registerDevice && token != null && isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                registerDeviceIfNeeded()
            }
            return RomMResult.Success(normalizedUrl)
        } catch (e: Exception) {
            Logger.info(TAG, "connect: exception at $normalizedUrl: ${e.message}")
            if (e is CancellationException) throw e
            val kind = (e as? RomMConnectionFailure)?.kind
                ?: if (e.isCertificateTrustFailure()) RomMErrorKind.UNTRUSTED_CERTIFICATE else null
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
            if (accessToken != token || api !== currentApi) throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
            persistRommCredentials(baseUrl, token, currentUser)

            if (isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                registerDeviceIfNeeded()
            }

            RomMResult.Success(token)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            RomMResult.Error(e.message ?: "Failed to verify token", kind = (e as? RomMConnectionFailure)?.kind)
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
                val capabilities = RomMCapabilities.from(version)
                if (!capabilities.supportsDeviceAuth) {
                    return RomMResult.Error(
                        "Device pairing requires RomM ${RomMCapabilities.DEVICE_AUTH_MIN_VERSION}+ (server is $version)"
                    )
                }

                val request = RomMDeviceAuthInitRequest(
                    clientDeviceIdentifier = clientDeviceIdentifier(),
                    name = deviceDisplayName(),
                    clientVersion = BuildConfig.VERSION_NAME,
                    requestedScopes = deviceAuthScopesFor(capabilities),
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
     * [activateOnSuccess] false stores a different identity without activating it. Re-pairing
     * the active identity refreshes its credentials and live connection.
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
                        registerAdditionalAccount(base, body)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.info(TAG, "pollDeviceAuthOnce: approval landed but sign-in failed: ${e.message}")
                    DeviceAuthPoll.Failed(
                        e.message ?: "Approved, but signing in failed",
                        retryable = false,
                        kind = (e as? RomMConnectionFailure)?.kind
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
            if (e is CancellationException) throw e
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
    ): DeviceAuthPoll {
        val generation = sessionGeneration.get()
        val session = profileSession()
        val newApi = createApi(base, body.accessToken)
        val user = fetchCurrentUser(newApi) ?: throw RomMConnectionFailure(RomMErrorKind.IDENTITY_UNAVAILABLE)
        val active = rommAccountRepository.get().activeAccount()
        if (generation != sessionGeneration.get() || session.accountId != active?.id || session != profileSession()) {
            throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
        }
        if (active != null && active.id == session.accountId && active.rommUserId == user.id && session.userId == user.id) {
            finalizeDeviceAuth(base, body, user)
            return DeviceAuthPoll.Approved(body.accessToken)
        }
        val accountId = rommAccountRepository.get().registerAdditional(
            rommUserId = user.id,
            username = user.username,
            baseUrl = base,
            token = body.accessToken,
            deviceId = body.deviceId,
            deviceClientVersion = BuildConfig.VERSION_NAME,
            avatarPath = user.avatarPath
        )
        deviceAuthApi = null
        deviceAuthBaseUrl = null
        Logger.info(TAG, "registerAdditionalAccount: stored account $accountId for user ${user.id} without activating")
        return DeviceAuthPoll.AddedAccount(accountId)
    }

    private suspend fun finalizeDeviceAuth(base: String, body: RomMDeviceAuthTokenResponse, verifiedUser: RomMUser? = null) {
        val generation = sessionGeneration.get()
        val session = profileSession()
        val newApi = createApi(base, body.accessToken)
        val heartbeat = try { newApi.heartbeat() } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        val version = heartbeat?.body()?.version ?: "unknown"
        val capabilities = RomMCapabilities.from(
            version,
            heartbeat?.body()?.libretroApiEnabled,
            heartbeat?.body()?.steamGridDbEnabled,
            heartbeat?.body()?.snapshotsEnabled == true
        )

        val user = verifiedUser ?: fetchCurrentUser(newApi)
        if (generation != sessionGeneration.get() || session != profileSession()) {
            throw RomMConnectionFailure(RomMErrorKind.SESSION_CHANGED)
        }
        persistRommCredentials(base, body.accessToken, user)
        userPreferencesRepository.setRommDeviceId(body.deviceId, BuildConfig.VERSION_NAME)
        rommAccountRepository.get().recordDeviceRegistration(body.deviceId, BuildConfig.VERSION_NAME)

        baseUrl = base
        accessToken = body.accessToken
        api = newApi
        currentUser = user
        lastTokenVerifiedAt = if (user == null) 0L else SystemClock.elapsedRealtime()
        cachedDeviceId = body.deviceId
        saveSyncRepository.get().setApi(newApi)
        biosRepository.setApi(newApi)
        saveSyncRepository.get().setCapabilities(capabilities)
        saveSyncRepository.get().setDeviceId(body.deviceId)
        _connectionState.value = ConnectionState.Connected(version, capabilities)
        rememberSnapshots(base, capabilities)
        _capabilityRefreshes.tryEmit(Unit)

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
        sessionGeneration.incrementAndGet()
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
        currentUser = null
        lastTokenVerifiedAt = 0L
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
                rememberedSnapshots = null
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
        rememberedSnapshots = null
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
        loadRememberedSnapshots(stored.candidates)
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
    private suspend fun verifiedUser(candidate: RomMApi): RomMUser? = try {
        val response = candidate.getCurrentUser()
        response.body()?.takeIf { response.isSuccessful }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.info(TAG, "verifiedUser: identity call failed: ${e.message}")
        null
    }

    private fun tokenVerifiedRecently(): Boolean =
        currentUser != null && SystemClock.elapsedRealtime() - lastTokenVerifiedAt < TOKEN_RECHECK_WINDOW_MS

    private data class ProfileSession(
        val accountId: Long?,
        val token: String?,
        val userId: Long?,
        val baseUrl: String?,
        val matchesAccount: Boolean
    )

    private suspend fun profileSession(): ProfileSession {
        val prefs = userPreferencesRepository.preferences.first()
        val account = rommAccountRepository.get().activeAccount()
        return ProfileSession(
            account?.id, prefs.rommToken, prefs.rommUserId, prefs.rommBaseUrl,
            account == null || (account.token == prefs.rommToken && account.rommUserId == prefs.rommUserId)
        )
    }

    private suspend fun refreshProfile(session: ProfileSession, token: String, user: RomMUser, newBaseUrl: String? = null) {
        if (!session.matchesAccount || session.token != token || (session.userId != null && session.userId != user.id)) return
        rommAccountRepository.get().refreshProfile(
            session.accountId, user.id, token, session.userId, session.baseUrl, user.username, user.avatarPath, newBaseUrl
        )
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

        val generation = sessionGeneration.get()
        val token = accessToken
        val session = profileSession()
        fun sessionChanged() = generation != sessionGeneration.get() || api !== currentApi || accessToken != token
        if (!session.matchesAccount || session.token != token) return

        try {
            if (token != null && !tokenVerifiedRecently()) {
                val user = verifiedUser(currentApi)
                if (sessionChanged() || session != profileSession()) return
                if (user == null || (session.userId != null && session.userId != user.id)) {
                    Logger.info(TAG, "checkConnection: token rejected, scheduling reconnect")
                    scheduleReconnect()
                    return
                }
                refreshProfile(session, token, user)
                if (sessionChanged() || session != profileSession()) return
                currentUser = user
                lastTokenVerifiedAt = SystemClock.elapsedRealtime()
            }

            val known = _connectionState.value as? ConnectionState.Connected
            if (known != null) {
                reconnectPending = false
                Logger.info(TAG, "checkConnection: session still valid, version=${known.version}")
                return
            }

            val response = currentApi.heartbeat()
            if (sessionChanged()) return
            if (response.isSuccessful) {
                val body = response.body()
                val version = body?.version ?: "unknown"
                val capabilities = RomMCapabilities.from(
                    version, body?.libretroApiEnabled, body?.steamGridDbEnabled, body?.snapshotsEnabled == true
                )
                _connectionState.value = ConnectionState.Connected(version, capabilities)
                saveSyncRepository.get().setCapabilities(capabilities)
                rememberSnapshots(baseUrl, capabilities)
                _capabilityRefreshes.tryEmit(Unit)
                reconnectPending = false
                Logger.info(TAG, "checkConnection: connected, version=$version")
            } else {
                Logger.info(TAG, "checkConnection: heartbeat failed with ${response.code()}, scheduling reconnect")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            if (sessionChanged()) return
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
