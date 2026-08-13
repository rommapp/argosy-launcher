package com.nendo.argosy.data.remote.jellyfin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

private const val TAG = "JellyfinConnectionManager"
private const val QUICK_CONNECT_POLL_INTERVAL_MS = 2_000L
private const val QUICK_CONNECT_TIMEOUT_MS = 300_000L
private const val QUICK_CONNECT_MAX_CONSECUTIVE_FAILURES = 5
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

sealed class JellyfinConnectionState {
    data object Disconnected : JellyfinConnectionState()
    data object Connecting : JellyfinConnectionState()
    data class Connected(val capabilities: JellyfinCapabilities) : JellyfinConnectionState()
    data class Failed(val reason: String) : JellyfinConnectionState()
}

/**
 * What the user needs to see while a Quick Connect attempt is in flight. [AwaitingApproval] carries
 * the code the user types into a client that is already signed in; there is nothing else for them
 * to act on, so the code is the whole state.
 */
sealed class JellyfinQuickConnectState {
    data object Idle : JellyfinQuickConnectState()
    data class AwaitingApproval(val code: String) : JellyfinQuickConnectState()
    data object Authenticating : JellyfinQuickConnectState()
    data class Succeeded(val userName: String?) : JellyfinQuickConnectState()
    data class Failed(val reason: String) : JellyfinQuickConnectState()
}

sealed class JellyfinSignInResult {
    data class Success(val userId: String, val userName: String?) : JellyfinSignInResult()
    data object Expired : JellyfinSignInResult()
    data class Failed(val reason: String) : JellyfinSignInResult()
}

/**
 * The three moments a sign-in caller has to render, in the order they happen.
 *
 * Kept as a plain callback bundle rather than a reference to the settings delegate so this stays a
 * data-layer type: the settings delegate lives in the ui layer and dependencies run inward only.
 * The three map one-to-one onto its `onQuickConnectStarted` / `onSignedIn` / `onSignInFailed`.
 */
data class JellyfinSignInCallbacks(
    val onCodeIssued: (String) -> Unit = {},
    val onSignedIn: (String?) -> Unit = {},
    val onFailed: (String) -> Unit = {}
)

/**
 * Owns the Jellyfin identity: which server, which device, which token, and whether any of it still
 * works.
 *
 * Quick Connect is the primary path because a controller has no keyboard. Username and password
 * remain as the fallback for a server that has Quick Connect switched off, which is a per-server
 * setting rather than a version fact and so is read from the server rather than assumed.
 *
 * A typed address is validated against `/System/Info/Public` before any credential is collected, so
 * a typo fails at the address rather than as a rejected login.
 */
@Singleton
class JellyfinConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val apiFactory: JellyfinApiFactory,
    private val serverDiscovery: JellyfinServerDiscovery
) {
    private var api: JellyfinApi? = null
    private var baseUrl: String = ""
    private var userId: String? = null
    private var deviceId: String? = null

    private val connectMutex = Mutex()

    private val _connectionState = MutableStateFlow<JellyfinConnectionState>(
        JellyfinConnectionState.Disconnected
    )
    val connectionState: StateFlow<JellyfinConnectionState> = _connectionState.asStateFlow()

    private val _quickConnectState = MutableStateFlow<JellyfinQuickConnectState>(
        JellyfinQuickConnectState.Idle
    )
    val quickConnectState: StateFlow<JellyfinQuickConnectState> = _quickConnectState.asStateFlow()

    fun getApi(): JellyfinApi? = api

    fun getBaseUrl(): String = baseUrl

    fun getUserId(): String? = userId

    fun getDeviceId(): String? = deviceId

    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun isConnected(): Boolean = _connectionState.value is JellyfinConnectionState.Connected

    fun getCapabilities(): JellyfinCapabilities =
        (_connectionState.value as? JellyfinConnectionState.Connected)?.capabilities
            ?: JellyfinCapabilities.NONE

    /**
     * Restores the stored session at startup. A stored token that no longer works leaves the state
     * failed rather than clearing the credentials: a server that is merely unreachable must not cost
     * the user their sign-in.
     */
    suspend fun initialize() {
        val prefs = userPreferencesRepository.preferences.first()
        deviceId = prefs.jellyfinDeviceId ?: ensureDeviceId()
        val server = prefs.jellyfinServerUrl
        if (server.isNullOrBlank()) return
        connect(server, prefs.jellyfinAccessToken, prefs.jellyfinUserId)
    }

    suspend fun discoverServers(): List<JellyfinDiscoveredServer> = serverDiscovery.discover()

    /**
     * Confirms an address is a Jellyfin server before anything is asked of the user. Runs
     * unauthenticated, so it works while the client holds no token at all.
     */
    suspend fun validateServer(serverUrl: String): JellyfinResult<JellyfinPublicSystemInfo> {
        val normalized = normalizeServerUrl(serverUrl)
        val probe = apiFactory.create(normalized, ensureDeviceId(), getDeviceName(), null)
        return try {
            val response = probe.getPublicSystemInfo()
            val body = response.body()
            when {
                !response.isSuccessful -> JellyfinResult.Error(
                    "Server did not answer as Jellyfin",
                    response.code()
                )
                body == null || body.version.isNullOrBlank() ->
                    JellyfinResult.Error("Server did not report a version")
                else -> JellyfinResult.Success(body)
            }
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: "Could not reach the server")
        }
    }

    /**
     * Runs the whole Quick Connect exchange: initiate, hand the code to the caller, poll until the
     * user approves it on a signed-in client, then redeem the secret for a token.
     *
     * The poll is bounded on a monotonic clock rather than by counting sleeps, so a slow network
     * cannot stretch the attempt past the window the server keeps the request open for. Transient
     * failures are retried; only an outright rejection of the secret ends it early, because a device
     * that gives up on one bad response strands a user who is mid-approval.
     */
    suspend fun signInWithQuickConnect(
        serverUrl: String,
        callbacks: JellyfinSignInCallbacks = JellyfinSignInCallbacks()
    ): JellyfinSignInResult {
        val normalized = normalizeServerUrl(serverUrl)
        val device = ensureDeviceId()
        val client = apiFactory.create(normalized, device, getDeviceName(), null)

        val initiated = try {
            val response = client.initiateQuickConnect()
            response.body().takeIf { response.isSuccessful }
                ?: return failQuickConnect(callbacks, "Quick Connect is not available on this server")
        } catch (e: Exception) {
            return failQuickConnect(callbacks, e.message ?: "Could not start Quick Connect")
        }

        _quickConnectState.value = JellyfinQuickConnectState.AwaitingApproval(initiated.code)
        callbacks.onCodeIssued(initiated.code)

        return when (val polled = pollQuickConnectUntilResolved(client, initiated.secret)) {
            is QuickConnectPoll.Approved -> redeemQuickConnect(normalized, client, initiated.secret, callbacks)
            QuickConnectPoll.Expired -> {
                _quickConnectState.value = JellyfinQuickConnectState.Failed("The code expired")
                callbacks.onFailed("The code expired")
                JellyfinSignInResult.Expired
            }
            is QuickConnectPoll.Failed -> failQuickConnect(callbacks, polled.message)
        }
    }

    suspend fun signInWithPassword(
        serverUrl: String,
        username: String,
        password: String,
        callbacks: JellyfinSignInCallbacks = JellyfinSignInCallbacks()
    ): JellyfinSignInResult {
        val normalized = normalizeServerUrl(serverUrl)
        val client = apiFactory.create(normalized, ensureDeviceId(), getDeviceName(), null)
        return try {
            val response = client.authenticateByName(
                JellyfinAuthenticateByNameRequest(username = username, pw = password)
            )
            val body = response.body()?.takeIf { !it.accessToken.isNullOrBlank() }
            if (!response.isSuccessful || body == null) {
                val reason = if (response.code() == HTTP_UNAUTHORIZED) "Wrong username or password"
                             else "Sign-in was refused by the server"
                callbacks.onFailed(reason)
                JellyfinSignInResult.Failed(reason)
            } else {
                completeSignIn(normalized, body, callbacks)
            }
        } catch (e: Exception) {
            val reason = e.message ?: "Could not reach the server"
            callbacks.onFailed(reason)
            JellyfinSignInResult.Failed(reason)
        }
    }

    suspend fun signOut() {
        connectMutex.withLock {
            api = null
            userId = null
            baseUrl = ""
            _connectionState.value = JellyfinConnectionState.Disconnected
            _quickConnectState.value = JellyfinQuickConnectState.Idle
        }
        userPreferencesRepository.clearJellyfinCredentials()
    }

    fun clearQuickConnectState() {
        _quickConnectState.value = JellyfinQuickConnectState.Idle
    }

    private suspend fun redeemQuickConnect(
        normalizedUrl: String,
        client: JellyfinApi,
        secret: String,
        callbacks: JellyfinSignInCallbacks
    ): JellyfinSignInResult {
        _quickConnectState.value = JellyfinQuickConnectState.Authenticating
        return try {
            val response = client.authenticateWithQuickConnect(
                JellyfinAuthenticateWithQuickConnectRequest(secret)
            )
            val body = response.body()?.takeIf { !it.accessToken.isNullOrBlank() }
            if (!response.isSuccessful || body == null) {
                failQuickConnect(callbacks, "The server would not complete the sign-in")
            } else {
                val outcome = completeSignIn(normalizedUrl, body, callbacks)
                if (outcome is JellyfinSignInResult.Success) {
                    _quickConnectState.value = JellyfinQuickConnectState.Succeeded(outcome.userName)
                }
                outcome
            }
        } catch (e: Exception) {
            failQuickConnect(callbacks, e.message ?: "The sign-in could not be completed")
        }
    }

    private suspend fun completeSignIn(
        normalizedUrl: String,
        auth: JellyfinAuthenticationResult,
        callbacks: JellyfinSignInCallbacks
    ): JellyfinSignInResult {
        val token = auth.accessToken.orEmpty()
        val signedInUser = auth.user
            ?: return failQuickConnect(callbacks, "The server did not identify the signed-in user")
        userPreferencesRepository.setJellyfinServerUrl(normalizedUrl)
        userPreferencesRepository.setJellyfinCredentials(token, signedInUser.id, signedInUser.name)
        connect(normalizedUrl, token, signedInUser.id)
        callbacks.onSignedIn(signedInUser.name)
        Logger.info(TAG, "signed in as ${signedInUser.id}")
        return JellyfinSignInResult.Success(signedInUser.id, signedInUser.name)
    }

    private fun failQuickConnect(
        callbacks: JellyfinSignInCallbacks,
        reason: String
    ): JellyfinSignInResult {
        _quickConnectState.value = JellyfinQuickConnectState.Failed(reason)
        callbacks.onFailed(reason)
        return JellyfinSignInResult.Failed(reason)
    }

    private sealed class QuickConnectPoll {
        data object Approved : QuickConnectPoll()
        data object Expired : QuickConnectPoll()
        data class Failed(val message: String) : QuickConnectPoll()
    }

    private enum class QuickConnectAttempt { APPROVED, PENDING, EXPIRED, UNREACHABLE }

    private suspend fun pollQuickConnectUntilResolved(
        client: JellyfinApi,
        secret: String
    ): QuickConnectPoll {
        val started = TimeSource.Monotonic.markNow()
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive) {
            delay(QUICK_CONNECT_POLL_INTERVAL_MS)
            if (started.elapsedNow().inWholeMilliseconds >= QUICK_CONNECT_TIMEOUT_MS) {
                return QuickConnectPoll.Expired
            }
            when (attemptQuickConnectPoll(client, secret)) {
                QuickConnectAttempt.APPROVED -> return QuickConnectPoll.Approved
                QuickConnectAttempt.EXPIRED -> return QuickConnectPoll.Expired
                QuickConnectAttempt.PENDING -> consecutiveFailures = 0
                QuickConnectAttempt.UNREACHABLE -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= QUICK_CONNECT_MAX_CONSECUTIVE_FAILURES) {
                        return QuickConnectPoll.Failed("Lost contact with the server")
                    }
                }
            }
        }
        return QuickConnectPoll.Expired
    }

    private suspend fun attemptQuickConnectPoll(
        client: JellyfinApi,
        secret: String
    ): QuickConnectAttempt = try {
        val response = client.pollQuickConnect(secret)
        when {
            response.code() == HTTP_NOT_FOUND -> QuickConnectAttempt.EXPIRED
            !response.isSuccessful -> QuickConnectAttempt.UNREACHABLE
            response.body()?.authenticated == true -> QuickConnectAttempt.APPROVED
            else -> QuickConnectAttempt.PENDING
        }
    } catch (e: Exception) {
        Logger.debug(TAG, "quick connect poll failed: ${e.message}")
        QuickConnectAttempt.UNREACHABLE
    }

    private suspend fun connect(serverUrl: String, token: String?, signedInUserId: String?) {
        connectMutex.withLock {
            _connectionState.value = JellyfinConnectionState.Connecting
            val normalized = normalizeServerUrl(serverUrl)
            val device = ensureDeviceId()
            val client = apiFactory.create(normalized, device, getDeviceName(), token)
            val info = try {
                client.getPublicSystemInfo().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                Logger.info(TAG, "connect failed: ${e.message}")
                null
            }
            val version = info?.version
            if (info == null || version.isNullOrBlank()) {
                _connectionState.value = JellyfinConnectionState.Failed("Could not reach the server")
                return
            }
            api = client
            baseUrl = normalized
            userId = signedInUserId
            _connectionState.value = JellyfinConnectionState.Connected(
                JellyfinCapabilities.from(
                    version = version,
                    serverName = info.serverName,
                    quickConnectEnabled = readQuickConnectEnabled(client)
                )
            )
            Logger.info(TAG, "connected to ${info.serverName} $version")
        }
    }

    private suspend fun readQuickConnectEnabled(client: JellyfinApi): Boolean? = try {
        val response = client.isQuickConnectEnabled()
        if (response.isSuccessful) response.body() else null
    } catch (_: Exception) {
        null
    }

    /**
     * The device id is the identity the server ties Quick Connect approvals and transcode sessions
     * to, so it has to outlive a process and survive a reinstall of the app's caches. It is derived
     * once and then persisted; a device that refuses to report an android id gets a random one,
     * which is stable from the moment it is stored.
     */
    @SuppressLint("HardwareIds")
    private suspend fun ensureDeviceId(): String {
        deviceId?.let { return it }
        val stored = userPreferencesRepository.preferences.first().jellyfinDeviceId
        if (!stored.isNullOrBlank()) {
            deviceId = stored
            return stored
        }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val generated = if (!androidId.isNullOrBlank()) "argosy-$androidId" else "argosy-${UUID.randomUUID()}"
        userPreferencesRepository.setJellyfinDeviceId(generated)
        deviceId = generated
        return generated
    }

    companion object {
        fun normalizeServerUrl(url: String): String = url.trim().trimEnd('/')
    }
}
