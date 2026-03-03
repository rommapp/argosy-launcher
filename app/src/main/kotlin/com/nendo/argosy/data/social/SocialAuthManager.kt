package com.nendo.argosy.data.social

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialAuthManager @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder().build()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var pendingWebSocket: WebSocket? = null
    private var currentDeviceKey: DeviceKeyResponse? = null

    private val authResultChannel = Channel<AuthResult>(Channel.CONFLATED)

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    private val api: SocialApi by lazy {
        Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SocialApi::class.java)
    }

    sealed class AuthState {
        data object Idle : AuthState()
        data object RequestingKey : AuthState()
        data class AwaitingLogin(
            val qrUrl: String,
            val loginCode: String
        ) : AuthState()
        data object Authenticating : AuthState()
        data class Success(val user: SocialUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    sealed class AuthResult {
        data class Success(val token: String, val user: SocialUser) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun startAuth(): AuthResult {
        _authState.value = AuthState.RequestingKey

        val keyResponse = try {
            val response = api.generateDeviceKey()
            if (response.isSuccessful) {
                response.body()
            } else {
                _authState.value = AuthState.Error("Failed to get device key: ${response.code()}")
                return AuthResult.Error("Failed to get device key: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device key", e)
            _authState.value = AuthState.Error("Network error: ${e.message}")
            return AuthResult.Error("Network error: ${e.message}")
        }

        if (keyResponse == null) {
            _authState.value = AuthState.Error("Empty response from server")
            return AuthResult.Error("Empty response from server")
        }

        currentDeviceKey = keyResponse
        _authState.value = AuthState.AwaitingLogin(
            qrUrl = keyResponse.qrUrl,
            loginCode = keyResponse.key
        )

        connectPendingWebSocket(keyResponse.pendingWs)

        return authResultChannel.receive()
    }

    private fun connectPendingWebSocket(wsUrl: String) {
        Log.d(TAG, "Connecting to pending auth WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        pendingWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Pending auth WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Pending auth message: $text")
                handlePendingAuthMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Pending auth WebSocket failure", t)
                if (_authState.value is AuthState.AwaitingLogin || _authState.value is AuthState.RequestingKey) {
                    scope.launch {
                        _authState.value = AuthState.Error("Connection failed: ${t.message}")
                        authResultChannel.send(AuthResult.Error("Connection failed: ${t.message}"))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Pending auth WebSocket closed: $code $reason")
                if (_authState.value is AuthState.AwaitingLogin) {
                    scope.launch {
                        _authState.value = AuthState.Error("Connection closed: $reason")
                        authResultChannel.send(AuthResult.Error("Connection closed: $reason"))
                    }
                }
            }
        })
    }

    private fun handlePendingAuthMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")

            when (type) {
                MessageTypes.AUTH_SUCCESS -> {
                    val token = json.getString("token")
                    val userJson = json.getJSONObject("user")

                    val user = SocialUser(
                        id = userJson.getString("id"),
                        username = userJson.getString("username"),
                        displayName = userJson.getString("display_name"),
                        avatarColor = userJson.optString("avatar_color", "#6366f1")
                    )

                    _authState.value = AuthState.Authenticating
                    scope.launch {
                        userPreferencesRepository.setSocialCredentials(
                            sessionToken = token,
                            userId = user.id,
                            username = user.username,
                            displayName = user.displayName,
                            avatarColor = user.avatarColor
                        )
                        _authState.value = AuthState.Success(user)
                        closePendingWebSocket()
                        authResultChannel.send(AuthResult.Success(token, user))
                    }
                }

                MessageTypes.ERROR -> {
                    val payloadJson = json.getJSONObject("payload")
                    val message = payloadJson.optString("message", "Unknown error")

                    scope.launch {
                        _authState.value = AuthState.Error(message)
                        authResultChannel.send(AuthResult.Error(message))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse auth message", e)
        }
    }

    fun cancelAuth() {
        closePendingWebSocket()
        currentDeviceKey = null
        _authState.value = AuthState.Idle
    }

    private fun closePendingWebSocket() {
        pendingWebSocket?.close(1000, "Auth complete or cancelled")
        pendingWebSocket = null
    }

    suspend fun logout() {
        closePendingWebSocket()
        currentDeviceKey = null
        userPreferencesRepository.clearSocialCredentials()
        _authState.value = AuthState.Idle
    }

    fun reset() {
        closePendingWebSocket()
        currentDeviceKey = null
        _authState.value = AuthState.Idle
    }

    companion object {
        private const val TAG = "SocialAuthManager"
        const val SERVER_URL = "https://api.argosy.dev/"
    }
}
