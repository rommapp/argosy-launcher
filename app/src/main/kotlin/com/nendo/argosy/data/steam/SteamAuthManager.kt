package com.nendo.argosy.data.steam

import android.util.Log
import com.nendo.argosy.data.local.dao.SteamAccountDao
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.data.local.entity.SteamAccountEntity
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.types.SteamID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamAuthManager"

sealed class QrAuthState {
    data object Idle : QrAuthState()
    data object Starting : QrAuthState()
    data class WaitingForScan(val challengeUrl: String) : QrAuthState()
    data object Polling : QrAuthState()
    data class Success(val username: String, val steamId: Long) : QrAuthState()
    data class Error(val message: String) : QrAuthState()
}

sealed class SteamAuthEvent {
    data class LoggedIn(val steamId: Long, val username: String) : SteamAuthEvent()
    data object LoggedOut : SteamAuthEvent()
    data class LoginFailed(val reason: String) : SteamAuthEvent()
}

@Singleton
class SteamAuthManager @Inject constructor(
    private val steamAccountDao: SteamAccountDao,
    private val notificationManager: NotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var steamClient: SteamClient? = null
    private var steamUser: SteamUser? = null
    private var qrAuthSession: QrAuthSession? = null
    private var authPollJob: Job? = null
    private var qrCancelled = false
    private var lastClientId: Long? = null
    @Volatile var sessionDead = false
        private set

    private val _qrAuthState = MutableStateFlow<QrAuthState>(QrAuthState.Idle)
    val qrAuthState: StateFlow<QrAuthState> = _qrAuthState.asStateFlow()

    private val _authEvents = MutableSharedFlow<SteamAuthEvent>()
    val authEvents: SharedFlow<SteamAuthEvent> = _authEvents.asSharedFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var pendingAuthResult: AuthPollResult? = null

    fun onConnected(client: SteamClient, user: SteamUser) {
        steamClient = client
        steamUser = user
        Log.d(TAG, "Steam client connected, ready for auth")

        val qrActive = _qrAuthState.value is QrAuthState.WaitingForScan ||
            _qrAuthState.value is QrAuthState.Polling ||
            _qrAuthState.value is QrAuthState.Starting
        if (qrActive) {
            Log.d(TAG, "QR auth in progress, skipping auto-login")
            return
        }

        scope.launch {
            val savedAccount = steamAccountDao.getActiveAccount()
            if (savedAccount != null) {
                Log.d(TAG, "Found saved account: ${savedAccount.username}, attempting auto-login")
                var attempts = 0
                while (attempts < 5 && !client.isConnected) {
                    delay(500)
                    attempts++
                }
                if (client.isConnected) {
                    loginWithRefreshToken(savedAccount)
                } else {
                    Log.e(TAG, "Client not connected after ${attempts * 500}ms, cannot auto-login")
                }
            } else {
                Log.d(TAG, "No saved account found")
            }
        }
    }

    fun onDisconnected() {
        _isLoggedIn.value = false
        cancelQrAuth()
    }

    fun onLoggedOn(callback: LoggedOnCallback) {
        val steamId = callback.clientSteamID ?: return
        val result = if (qrCancelled) null else pendingAuthResult
        pendingAuthResult = null
        sessionDead = false
        qrCancelled = false

        _isLoggedIn.value = true
        Log.d(TAG, "Set isLoggedIn = true")

        scope.launch {
            if (result != null) {
                saveAccount(steamId, result)
                _authEvents.emit(
                    SteamAuthEvent.LoggedIn(
                        steamId.convertToUInt64(),
                        result.accountName
                    )
                )
            } else {
                // Auto-login succeeded -- update lastLoginAt to track token health
                val account = steamAccountDao.getBySteamId(steamId.convertToUInt64())
                if (account != null) {
                    steamAccountDao.update(account.copy(lastLoginAt = Instant.now()))
                    Log.d(TAG, "Refreshed lastLoginAt for ${account.username}")
                }
            }
        }

        _qrAuthState.value = QrAuthState.Success(
            username = result?.accountName ?: "Unknown",
            steamId = steamId.convertToUInt64()
        )
    }

    private val AUTH_FATAL_RESULTS = setOf(
        EResult.InvalidPassword, EResult.Expired,
        EResult.InvalidLoginAuthCode, EResult.AccountLogonDenied,
        EResult.AccountLogonDeniedNoMail, EResult.AccountLoginDeniedNeedTwoFactor,
        EResult.ExpiredLoginAuthCode, EResult.ParentalControlRestricted
    )

    fun onLoginFailed(result: EResult) {
        if (qrCancelled) {
            qrCancelled = false
            Log.d(TAG, "Ignoring login failure after QR cancel: $result")
            return
        }
        scope.launch {
            _authEvents.emit(SteamAuthEvent.LoginFailed(result.name))
            if (result in AUTH_FATAL_RESULTS) {
                Log.w(TAG, "Auth permanently failed ($result), clearing saved account")
                sessionDead = true
                steamAccountDao.deactivateAll()
                notificationManager.show(
                    title = "Steam session expired",
                    subtitle = "Sign in again from Settings > Steam",
                    type = NotificationType.WARNING,
                    key = "steam_auth_expired"
                )
            } else {
                Log.w(TAG, "Login failed ($result), keeping account for retry")
            }
        }
        // Only show error in UI for fatal auth failures, not transient reconnect issues
        if (result in AUTH_FATAL_RESULTS) {
            _qrAuthState.value = QrAuthState.Error("Login failed: ${result.name}")
        }
    }

    fun startQrAuth() {
        sessionDead = false
        qrCancelled = false
        val client = steamClient ?: run {
            _qrAuthState.value = QrAuthState.Error("Not connected to Steam")
            return
        }

        cancelQrAuth()
        _qrAuthState.value = QrAuthState.Starting

        scope.launch {
            try {
                Log.d(TAG, "Starting QR auth session")
                val authDetails = AuthSessionDetails()
                authDetails.deviceFriendlyName = "Argosy Launcher"

                val session = client.authentication.beginAuthSessionViaQR(authDetails).await()
                qrAuthSession = session
                lastClientId = session.clientID

                _qrAuthState.value = QrAuthState.WaitingForScan(session.challengeUrl)
                Log.d(TAG, "QR challenge URL: ${session.challengeUrl}")

                startAuthPolling(session)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start QR auth", e)
                _qrAuthState.value = QrAuthState.Error(e.message ?: "Failed to start QR auth")
            }
        }
    }

    private fun startAuthPolling(session: QrAuthSession) {
        authPollJob?.cancel()
        authPollJob = scope.launch {
            Log.d(TAG, "Starting auth poll loop")
            while (isActive) {
                try {
                    val result = session.pollAuthSessionStatus().await()
                    if (result != null) {
                        Log.d(TAG, "Auth poll success: ${result.accountName}")
                        pendingAuthResult = result
                        loginWithAuthResult(result)
                        break
                    }

                    val currentState = _qrAuthState.value
                    if (currentState is QrAuthState.WaitingForScan &&
                        session.challengeUrl != currentState.challengeUrl
                    ) {
                        Log.d(TAG, "QR challenge URL refreshed")
                        _qrAuthState.value = QrAuthState.WaitingForScan(session.challengeUrl)
                    }

                    delay(session.pollingInterval.toLong() * 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Auth poll error", e)
                    _qrAuthState.value = QrAuthState.Error(e.message ?: "Polling failed")
                    break
                }
            }
        }
    }

    private fun loginWithAuthResult(result: AuthPollResult) {
        val user = steamUser
        val client = steamClient
        if (user == null || client == null) {
            _qrAuthState.value = QrAuthState.Error("Steam not connected")
            return
        }

        Log.d(TAG, "Logging in with auth result for ${result.accountName} (client connected: ${client.isConnected})")
        val logonDetails = LogOnDetails()
        logonDetails.username = result.accountName
        logonDetails.accessToken = result.refreshToken
        logonDetails.shouldRememberPassword = true

        user.logOn(logonDetails)
    }

    private fun loginWithRefreshToken(account: SteamAccountEntity) {
        val user = steamUser
        if (user == null) {
            Log.e(TAG, "Steam user handler not available for auto-login")
            return
        }

        Log.d(TAG, "Auto-login with saved token for ${account.username}")
        val logonDetails = LogOnDetails()
        logonDetails.username = account.username
        logonDetails.accessToken = account.refreshToken
        logonDetails.shouldRememberPassword = true

        user.logOn(logonDetails)
    }

    private suspend fun saveAccount(steamId: SteamID, result: AuthPollResult) {
        steamAccountDao.deactivateAll()

        val clientId = lastClientId
        val existing = steamAccountDao.getBySteamId(steamId.convertToUInt64())
        if (existing != null) {
            steamAccountDao.update(
                existing.copy(
                    username = result.accountName,
                    refreshToken = result.refreshToken,
                    accessToken = result.accessToken,
                    clientId = clientId,
                    isActive = true,
                    lastLoginAt = Instant.now()
                )
            )
            Log.d(TAG, "Updated existing account: ${result.accountName}")
        } else {
            steamAccountDao.insert(
                SteamAccountEntity(
                    steamId = steamId.convertToUInt64(),
                    username = result.accountName,
                    refreshToken = result.refreshToken,
                    accessToken = result.accessToken,
                    clientId = clientId,
                    isActive = true,
                    lastLoginAt = Instant.now()
                )
            )
            Log.d(TAG, "Saved new account: ${result.accountName}")
        }
    }

    fun cancelQrAuth() {
        qrCancelled = true
        authPollJob?.cancel()
        authPollJob = null
        qrAuthSession = null
        pendingAuthResult = null
        _qrAuthState.value = QrAuthState.Idle
    }

    fun logout() {
        steamUser?.logOff()
        scope.launch {
            steamAccountDao.deactivateAll()
            _authEvents.emit(SteamAuthEvent.LoggedOut)
        }
        _qrAuthState.value = QrAuthState.Idle
    }

    suspend fun getActiveAccount(): SteamAccountEntity? {
        return steamAccountDao.getActiveAccount()
    }

    suspend fun deleteAccount(accountId: Long) {
        steamAccountDao.delete(accountId)
    }
}
