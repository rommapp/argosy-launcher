package com.nendo.argosy.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.quaypass.QuayPassKeystore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Debug-only adb hooks for server-less QuayPass validation. Never compiled into release. */
class QuayPassTestHarness : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        fun keystore(): QuayPassKeystore
        fun dataStore(): DataStore<Preferences>
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HarnessEntryPoint::class.java
        )
        when (intent.action) {
            ACTION_DUMP_PUBKEY -> dumpPubkey(entryPoint)
            ACTION_DUMP_STATE -> dumpState(entryPoint, goAsync())
            ACTION_SEED -> seed(entryPoint, intent, goAsync())
            ACTION_CLEAR_KEY -> {
                entryPoint.keystore().clear()
                Log.i(TAG, "keystore alias cleared")
            }
            ACTION_VERIFY -> {
                val cred = intent.getStringExtra("credential")
                Log.i(TAG, "baked_pubkeys=${com.nendo.argosy.BuildConfig.QUAYPASS_SERVER_PUBKEYS}")
                if (cred != null) {
                    val bundle = com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
                        .parseAndVerifyBase64(cred)
                    Log.i(TAG, "verify result=${if (bundle != null) "VALID alg=${bundle.pubkeyAlg}" else "INVALID"}")
                }
            }
        }
    }

    private fun dumpPubkey(entryPoint: HarnessEntryPoint) {
        runCatching {
            val info = entryPoint.keystore().getOrCreateKeyInfo()
            val alg = when (info.algorithm) {
                QuayPassKeystore.Algorithm.ED25519 -> "ed25519"
                QuayPassKeystore.Algorithm.EC_P256 -> "ec-p256"
            }
            Log.i(TAG, "alg=$alg backing=${info.backing}")
            Log.i(TAG, "pubkey=${Base64.encodeToString(info.publicKeyEncoded, Base64.NO_WRAP)}")
        }.onFailure { Log.e(TAG, "dumpPubkey failed", it) }
    }

    private fun dumpState(entryPoint: HarnessEntryPoint, result: PendingResult) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = entryPoint.dataStore().data.first()
                STATE_KEYS.forEach { name ->
                    Log.i(TAG, "$name=${prefs[stringPreferencesKey(name)] ?: prefs[booleanPreferencesKey(name)] ?: prefs[longPreferencesKey(name)]}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "dumpState failed", t)
            } finally {
                result.finish()
            }
        }
    }

    private fun seed(entryPoint: HarnessEntryPoint, intent: Intent, result: PendingResult) {
        val installId = intent.getStringExtra("install_id")
        val credential = intent.getStringExtra("credential")
        val expiresAt = intent.getLongExtra("expires_at", 0L)
        val username = intent.getStringExtra("username")
        val sessionToken = intent.getStringExtra("session_token")
        val avatar = intent.getStringExtra("avatar")
        val enable = intent.getBooleanExtra("enable", false)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                entryPoint.dataStore().edit { prefs ->
                    installId?.let { prefs[stringPreferencesKey("quaypass_client_install_id")] = it }
                    credential?.let { prefs[stringPreferencesKey("quaypass_credential")] = it }
                    if (expiresAt > 0) prefs[longPreferencesKey("quaypass_credential_expires_at")] = expiresAt
                    username?.let { prefs[stringPreferencesKey("social_username")] = it }
                    sessionToken?.let { prefs[stringPreferencesKey("social_session_token")] = it }
                    avatar?.let {
                        prefs[stringPreferencesKey("quaypass_avatar_bytes")] = it
                        prefs[booleanPreferencesKey("quaypass_avatar_configured")] = true
                    }
                    if (enable) prefs[booleanPreferencesKey("quaypass_enabled")] = true
                }
                Log.i(TAG, "seed applied")
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuayPassTestHarness"
        private const val ACTION_DUMP_PUBKEY = "com.nendo.argosy.debug.QUAYPASS_DUMP_PUBKEY"
        private const val ACTION_DUMP_STATE = "com.nendo.argosy.debug.QUAYPASS_DUMP_STATE"
        private const val ACTION_SEED = "com.nendo.argosy.debug.QUAYPASS_SEED"
        private const val ACTION_CLEAR_KEY = "com.nendo.argosy.debug.QUAYPASS_CLEAR_KEY"
        private const val ACTION_VERIFY = "com.nendo.argosy.debug.QUAYPASS_VERIFY"

        private val STATE_KEYS = listOf(
            "quaypass_client_install_id",
            "quaypass_credential_expires_at",
            "quaypass_enabled",
            "quaypass_avatar_configured",
            "quaypass_avatar_bytes",
            "social_username"
        )
    }
}
