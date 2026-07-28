package com.nendo.argosy.data.quaypass

import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Manages the QuayPass install registration and server-signed credential. */
@Singleton
class QuayPassCredentialManager @Inject constructor(
    private val keystore: QuayPassKeystore,
    private val fingerprint: ClientFingerprint,
    private val userPrefs: UserPreferencesRepository,
    private val dataStore: DataStore<Preferences>
) {

    private val moshi = Moshi.Builder().build()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastRefreshAttemptMillis = 0L

    @Volatile
    private var lastFetchRejected = false

    private val _credentialChanged = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits when a fetch attempt changes the credential's usability: a fresh
     * credential was stored, or one was refused as unverifiable. Transient
     * network failures do not emit, so a re-evaluation never loops while offline.
     */
    val credentialChanged: SharedFlow<Unit> = _credentialChanged.asSharedFlow()

    /**
     * Resolves the credential gate from local state only, with no network call,
     * so a held unexpired credential runs fully offline. [CredentialGate.REJECTED]
     * reflects the last fetch outcome, not a live check.
     */
    suspend fun localGate(): CredentialGate {
        val state = dataStore.data.first()
        if (state[Keys.CLIENT_INSTALL_ID] == null) return CredentialGate.ABSENT
        val cred = state[Keys.CREDENTIAL]
        val expires = state[Keys.CREDENTIAL_EXPIRES_AT]
        val now = Instant.now().epochSecond
        if (cred != null && expires != null && now < expires) return CredentialGate.VALID
        if (lastFetchRejected) return CredentialGate.REJECTED
        if (cred != null) return CredentialGate.EXPIRED
        return CredentialGate.ABSENT
    }

    /**
     * Fetches a credential only when the held one is missing or inside the
     * refresh window, throttled by the same cooldown as the inline refresh. A
     * held, comfortably-unexpired credential triggers no network call.
     */
    suspend fun ensureFreshCredential() = mutex.withLock {
        val state = dataStore.data.first()
        val installId = state[Keys.CLIENT_INSTALL_ID] ?: return@withLock
        val cred = state[Keys.CREDENTIAL]
        val expires = state[Keys.CREDENTIAL_EXPIRES_AT] ?: 0L
        val now = Instant.now().epochSecond
        if (cred != null && now < expires - REFRESH_THRESHOLD_SECONDS) return@withLock
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastRefreshAttemptMillis < REFRESH_RETRY_COOLDOWN_MS) return@withLock
        lastRefreshAttemptMillis = nowMillis
        fetchCredential(installId, refresh = cred != null)
    }

    private val okHttp by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
                }
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val api: QuayPassApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SOCIAL_API_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(QuayPassApi::class.java)
    }

    suspend fun isRegistered(): Boolean {
        val state = dataStore.data.first()
        return state[Keys.CLIENT_INSTALL_ID] != null
    }

    val isRegisteredFlow: Flow<Boolean> = dataStore.data
        .map { it[Keys.CLIENT_INSTALL_ID] != null }
        .distinctUntilChanged()

    /**
     * Returns a usable credential. Refreshes inline once inside the refresh
     * window, but falls back to the cached credential whenever it is still
     * unexpired so the BLE exchange keeps working fully offline until the
     * credential actually lapses.
     */
    suspend fun getValidCredential(): StoredCredential? = mutex.withLock {
        val state = dataStore.data.first()
        val installId = state[Keys.CLIENT_INSTALL_ID] ?: return null
        val cred = state[Keys.CREDENTIAL]
        val expires = state[Keys.CREDENTIAL_EXPIRES_AT]
        val now = Instant.now().epochSecond
        if (cred != null && expires != null && now < expires - REFRESH_THRESHOLD_SECONDS) {
            return StoredCredential(cred, Instant.ofEpochSecond(expires))
        }
        val cachedValid = if (cred != null && expires != null && now < expires) {
            StoredCredential(cred, Instant.ofEpochSecond(expires))
        } else {
            null
        }
        val nowMillis = System.currentTimeMillis()
        if (cachedValid != null && nowMillis - lastRefreshAttemptMillis < REFRESH_RETRY_COOLDOWN_MS) {
            return cachedValid
        }
        lastRefreshAttemptMillis = nowMillis
        return fetchCredential(installId, refresh = cred != null) ?: cachedValid
    }

    suspend fun refreshIfNeeded() {
        val state = dataStore.data.first()
        val installId = state[Keys.CLIENT_INSTALL_ID] ?: return
        val expires = state[Keys.CREDENTIAL_EXPIRES_AT] ?: 0L
        val now = Instant.now().epochSecond
        if (now >= expires - REFRESH_THRESHOLD_SECONDS) {
            mutex.withLock { fetchCredential(installId, refresh = true) }
        }
    }

    /**
     * Re-evaluates the install registration for the identity now in force.
     *
     * The held install id is only reusable when it was registered by this very social user with
     * this very keystore alias. Reusing it across a token swap with no intervening unlink kept
     * the device advertising the previous person's credential, because only a literal
     * `unknown_install` from the server forced a re-registration.
     */
    fun onSocialLinkChanged(isLinked: Boolean, sessionToken: String?, socialUserId: String?) {
        scope.launch {
            mutex.withLock {
                if (!isLinked) {
                    clearLocal()
                    return@withLock
                }
                val token = sessionToken ?: return@withLock
                val alias = currentKeyAlias()
                if (!isInstallOwnedBy(socialUserId, alias)) {
                    discardInstall()
                }
                val state = dataStore.data.first()
                val installId = state[Keys.CLIENT_INSTALL_ID]
                    ?: registerNewInstall(token, socialUserId, alias)
                if (installId != null) {
                    fetchCredential(installId, refresh = false)
                }
            }
        }
    }

    private suspend fun currentKeyAlias(): String =
        QuayPassKeystore.aliasFor(userPrefs.userPreferences.first().rommUserId)

    private suspend fun isInstallOwnedBy(socialUserId: String?, alias: String): Boolean {
        val state = dataStore.data.first()
        if (state[Keys.CLIENT_INSTALL_ID] == null) return true
        return state[Keys.INSTALL_SOCIAL_USER_ID] == socialUserId &&
            state[Keys.INSTALL_KEY_ALIAS] == alias
    }

    private suspend fun discardInstall() {
        Log.i(TAG, "Install id belongs to a different identity; discarding it")
        dataStore.edit {
            it.remove(Keys.CLIENT_INSTALL_ID)
            it.remove(Keys.CREDENTIAL)
            it.remove(Keys.CREDENTIAL_EXPIRES_AT)
            it.remove(Keys.INSTALL_SOCIAL_USER_ID)
            it.remove(Keys.INSTALL_KEY_ALIAS)
        }
    }

    private suspend fun registerNewInstall(
        sessionToken: String,
        socialUserId: String?,
        alias: String
    ): String? {
        val keyInfo = try {
            keystore.getOrCreateKeyInfo(alias)
        } catch (t: Throwable) {
            Log.e(TAG, "Keystore unavailable; cannot register", t)
            return null
        }

        if (fingerprint.apkSigningCertHash.isBlank()) {
            Log.e(TAG, "APK signing cert hash unavailable; cannot register (server requires it)")
            return null
        }

        val challenge = try {
            val resp = api.getRegisterChallenge(bearerHeader(sessionToken))
            if (!resp.isSuccessful) {
                Log.w(TAG, "Challenge request failed: HTTP ${resp.code()}")
                return null
            }
            resp.body()?.challenge
        } catch (t: Throwable) {
            Log.w(TAG, "Challenge request error", t)
            null
        } ?: return null

        val challengeSignature = try {
            val sig = keystore.signServerVerifiable(challenge.toByteArray(Charsets.UTF_8), alias)
            Base64.encodeToString(sig, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to sign registration challenge", t)
            return null
        }

        val req = RegisterClientRequest(
            publicKey = Base64.encodeToString(keyInfo.publicKeyEncoded, Base64.NO_WRAP),
            publicKeyAlg = when (keyInfo.algorithm) {
                QuayPassKeystore.Algorithm.ED25519 -> "ed25519"
                QuayPassKeystore.Algorithm.EC_P256 -> "ec-p256"
            },
            apkSigningCertHash = fingerprint.apkSigningCertHash,
            challenge = challenge,
            challengeSignature = challengeSignature
        )

        return try {
            val resp = api.registerClient(bearerHeader(sessionToken), req)
            if (resp.isSuccessful) {
                val body = resp.body()
                val id = body?.clientInstallId
                if (id != null) {
                    dataStore.edit {
                        it[Keys.CLIENT_INSTALL_ID] = id
                        it[Keys.INSTALL_KEY_ALIAS] = alias
                        if (socialUserId != null) it[Keys.INSTALL_SOCIAL_USER_ID] = socialUserId
                        else it.remove(Keys.INSTALL_SOCIAL_USER_ID)
                    }
                    Log.i(TAG, "Registered QuayPass install: $id")
                    id
                } else {
                    Log.w(TAG, "Register response empty")
                    null
                }
            } else {
                Log.w(TAG, "Register failed: HTTP ${resp.code()} code=${errorCodeOf(resp)}")
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Register error", t)
            null
        }
    }

    /**
     * Fetches the install credential. `issue` is idempotent (returns the current
     * live credential, minting only if none exists) and is used for the normal
     * path; `refresh` always mints and extends expiry and is used inside the
     * refresh window. On `unknown_install` the stored install id is discarded and
     * a fresh registration is performed once, per the server contract.
     */
    private suspend fun fetchCredential(
        installId: String,
        refresh: Boolean,
        allowReRegister: Boolean = true
    ): StoredCredential? {
        val identity = userPrefs.userPreferences.first()
        val sessionToken = identity.socialSessionToken ?: return null
        val req = IssueCredentialRequest(clientInstallId = installId)
        return try {
            val resp = if (refresh) api.refreshCredential(bearerHeader(sessionToken), req)
            else api.issueCredential(bearerHeader(sessionToken), req)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return null
                if (!verifyServerSignature(body.credential)) {
                    Log.e(TAG, "Server-issued credential failed signature verification; refusing")
                    lastFetchRejected = true
                    _credentialChanged.tryEmit(Unit)
                    return null
                }
                dataStore.edit {
                    it[Keys.CREDENTIAL] = body.credential
                    it[Keys.CREDENTIAL_EXPIRES_AT] = body.expiresAtEpochSecs
                }
                lastFetchRejected = false
                _credentialChanged.tryEmit(Unit)
                Log.i(TAG, "Fetched QuayPass credential (refresh=$refresh), expires ${body.expiresAtEpochSecs}")
                StoredCredential(body.credential, Instant.ofEpochSecond(body.expiresAtEpochSecs))
            } else {
                val code = errorCodeOf(resp)
                if (code == "unknown_install" && allowReRegister) {
                    Log.w(TAG, "unknown_install; discarding install id and re-registering")
                    discardInstall()
                    val newId = registerNewInstall(
                        sessionToken,
                        identity.socialUserId,
                        QuayPassKeystore.aliasFor(identity.rommUserId)
                    ) ?: return null
                    fetchCredential(newId, refresh = false, allowReRegister = false)
                } else {
                    Log.w(TAG, "Credential fetch failed (refresh=$refresh): HTTP ${resp.code()} code=$code")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Credential fetch error", t)
            null
        }
    }

    private fun errorCodeOf(resp: Response<*>): String? {
        val raw = runCatching { resp.errorBody()?.string() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { JSONObject(raw).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
    }

    private suspend fun clearLocal() {
        val alias = currentKeyAlias()
        discardInstall()
        keystore.clear(alias)
        lastFetchRejected = false
        _credentialChanged.tryEmit(Unit)
    }

    private fun verifyServerSignature(credentialBase64: String): Boolean =
        QuayPassCredentialBundle.parseAndVerifyBase64(credentialBase64) != null

    private fun bearerHeader(token: String) = "Bearer $token"

    data class StoredCredential(
        val bytesBase64: String,
        val expiresAt: Instant
    )

    enum class CredentialGate { VALID, EXPIRED, ABSENT, REJECTED }

    private object Keys {
        val CLIENT_INSTALL_ID = stringPreferencesKey("quaypass_client_install_id")
        val CREDENTIAL = stringPreferencesKey("quaypass_credential")
        val CREDENTIAL_EXPIRES_AT = longPreferencesKey("quaypass_credential_expires_at")
        val INSTALL_SOCIAL_USER_ID = stringPreferencesKey("quaypass_install_social_user_id")
        val INSTALL_KEY_ALIAS = stringPreferencesKey("quaypass_install_key_alias")
    }

    companion object {
        private const val TAG = "QuayPassCredentialManager"
        private const val REFRESH_THRESHOLD_SECONDS = 7L * 24 * 60 * 60
        private const val REFRESH_RETRY_COOLDOWN_MS = 5L * 60 * 1000
    }
}
