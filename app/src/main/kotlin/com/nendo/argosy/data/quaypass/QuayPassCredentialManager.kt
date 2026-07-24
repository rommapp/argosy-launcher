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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        return doRefresh(installId) ?: cachedValid
    }

    suspend fun refreshIfNeeded() {
        val state = dataStore.data.first()
        val installId = state[Keys.CLIENT_INSTALL_ID] ?: return
        val expires = state[Keys.CREDENTIAL_EXPIRES_AT] ?: 0L
        val now = Instant.now().epochSecond
        if (now >= expires - REFRESH_THRESHOLD_SECONDS) {
            mutex.withLock { doRefresh(installId) }
        }
    }

    fun onSocialLinkChanged(isLinked: Boolean, sessionToken: String?) {
        scope.launch {
            mutex.withLock {
                if (!isLinked) {
                    clearLocal()
                    return@withLock
                }
                val token = sessionToken ?: return@withLock
                val state = dataStore.data.first()
                val installId = state[Keys.CLIENT_INSTALL_ID] ?: registerNewInstall(token)
                if (installId != null) {
                    doRefresh(installId)
                }
            }
        }
    }

    private suspend fun registerNewInstall(sessionToken: String): String? {
        val keyInfo = try {
            keystore.getOrCreateKeyInfo()
        } catch (t: Throwable) {
            Log.e(TAG, "Keystore unavailable; cannot register", t)
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
            val sig = keystore.signServerVerifiable(challenge.toByteArray(Charsets.UTF_8))
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
            fingerprintHash = fingerprint.fingerprintHash,
            deviceToken = fingerprint.deviceToken,
            deviceId = fingerprint.deviceToken,
            challenge = challenge,
            challengeSignature = challengeSignature
        )

        return try {
            val resp = api.registerClient(bearerHeader(sessionToken), req)
            if (resp.isSuccessful) {
                val body = resp.body()
                val id = body?.clientInstallId
                if (id != null) {
                    dataStore.edit { it[Keys.CLIENT_INSTALL_ID] = id }
                    Log.i(TAG, "Registered QuayPass install: $id")
                    id
                } else {
                    Log.w(TAG, "Register response empty")
                    null
                }
            } else {
                val detail = runCatching { resp.errorBody()?.string() }.getOrNull().orEmpty()
                Log.w(TAG, "Register failed: HTTP ${resp.code()} $detail")
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Register error", t)
            null
        }
    }

    private suspend fun doRefresh(installId: String): StoredCredential? {
        val sessionToken = userPrefs.userPreferences.first().socialSessionToken ?: return null
        val req = IssueCredentialRequest(clientInstallId = installId)
        return try {
            val resp = api.refreshCredential(bearerHeader(sessionToken), req)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return null
                if (!verifyServerSignature(body.credential)) {
                    Log.e(TAG, "Server-issued credential failed signature verification; refusing")
                    return null
                }
                dataStore.edit {
                    it[Keys.CREDENTIAL] = body.credential
                    it[Keys.CREDENTIAL_EXPIRES_AT] = body.expiresAtEpochSecs
                }
                Log.i(TAG, "Refreshed QuayPass credential, expires ${body.expiresAtEpochSecs}")
                StoredCredential(body.credential, Instant.ofEpochSecond(body.expiresAtEpochSecs))
            } else {
                Log.w(TAG, "Refresh failed: HTTP ${resp.code()}")
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Refresh error", t)
            null
        }
    }

    private suspend fun clearLocal() {
        dataStore.edit {
            it.remove(Keys.CLIENT_INSTALL_ID)
            it.remove(Keys.CREDENTIAL)
            it.remove(Keys.CREDENTIAL_EXPIRES_AT)
        }
        keystore.clear()
    }

    private fun verifyServerSignature(credentialBase64: String): Boolean =
        QuayPassCredentialBundle.parseAndVerifyBase64(credentialBase64) != null

    private fun bearerHeader(token: String) = "Bearer $token"

    data class StoredCredential(
        val bytesBase64: String,
        val expiresAt: Instant
    )

    private object Keys {
        val CLIENT_INSTALL_ID = stringPreferencesKey("quaypass_client_install_id")
        val CREDENTIAL = stringPreferencesKey("quaypass_credential")
        val CREDENTIAL_EXPIRES_AT = longPreferencesKey("quaypass_credential_expires_at")
    }

    companion object {
        private const val TAG = "QuayPassCredentialManager"
        private const val REFRESH_THRESHOLD_SECONDS = 7L * 24 * 60 * 60
        private const val REFRESH_RETRY_COOLDOWN_MS = 5L * 60 * 1000
    }
}
