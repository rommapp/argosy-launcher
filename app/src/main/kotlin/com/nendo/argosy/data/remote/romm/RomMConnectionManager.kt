package com.nendo.argosy.data.remote.romm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    data class Failed(val message: String) : DeviceAuthPoll()
}

@Singleton
class RomMConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val biosRepository: BiosRepository
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private var cachedDeviceId: String? = null
    private var deviceAuthApi: RomMApi? = null
    private var deviceAuthBaseUrl: String? = null
    private val detailAdapter by lazy { Moshi.Builder().build().adapter(RomMDetailResponse::class.java) }

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
        cachedDeviceId = prefs.rommDeviceId
        if (cachedDeviceId != null) {
            saveSyncRepository.get().setDeviceId(cachedDeviceId)
        }
        if (!prefs.rommBaseUrl.isNullOrBlank()) {
            val result = connect(prefs.rommBaseUrl, prefs.rommToken)
            Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
        }
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting

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
                    val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled)
                    _connectionState.value = ConnectionState.Connected(version, capabilities)
                    saveSyncRepository.get().setCapabilities(capabilities)
                    Logger.info(TAG, "connect: success at $normalizedUrl, version=$version, capabilities=$capabilities")
                    if (token != null && isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
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

        _connectionState.value = ConnectionState.Failed(lastError ?: "Connection failed")
        return RomMResult.Error(lastError ?: "Connection failed")
    }

    suspend fun login(username: String, password: String): RomMResult<String> {
        val currentApi = api ?: return RomMResult.Error("Not connected")

        return try {
            val scope = buildLoginScope()
            val response = currentApi.login(username, password, scope)
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                    ?: return RomMResult.Error("No token received")

                accessToken = token
                api = createApi(baseUrl, token)
                saveSyncRepository.get().setApi(api)
                biosRepository.setApi(api)

                userPreferencesRepository.setRomMCredentials(baseUrl, token, username)

                if (isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                    registerDeviceIfNeeded()
                }

                RomMResult.Success(token)
            } else {
                RomMResult.Error("Login failed", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Login failed")
        }
    }

    suspend fun connectWithToken(url: String, token: String): RomMResult<String> {
        val connectResult = connect(url, token)
        if (connectResult is RomMResult.Error) return connectResult

        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val userResponse = currentApi.getCurrentUser()
            val username = if (userResponse.isSuccessful) {
                userResponse.body()?.username
            } else null

            userPreferencesRepository.setRomMCredentials(baseUrl, token, username)

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
                            verificationUrl = absolutizeUrl(body.verificationUrl, normalizedUrl),
                            verificationUrlComplete = absolutizeUrl(body.verificationUrlComplete, normalizedUrl)
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

    suspend fun pollDeviceAuthOnce(deviceCode: String): DeviceAuthPoll {
        val authApi = deviceAuthApi ?: return DeviceAuthPoll.Failed("Pairing not started")
        val base = deviceAuthBaseUrl ?: return DeviceAuthPoll.Failed("Pairing not started")
        return try {
            val response = authApi.deviceAuthToken(RomMDeviceAuthTokenRequest(deviceCode))
            if (response.isSuccessful) {
                val body = response.body() ?: return DeviceAuthPoll.Failed("Empty token response")
                finalizeDeviceAuth(base, body)
                DeviceAuthPoll.Approved(body.accessToken)
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

    private suspend fun finalizeDeviceAuth(base: String, body: RomMDeviceAuthTokenResponse) {
        baseUrl = base
        accessToken = body.accessToken
        val newApi = createApi(base, body.accessToken)
        api = newApi
        saveSyncRepository.get().setApi(newApi)
        biosRepository.setApi(newApi)

        val heartbeat = try { newApi.heartbeat() } catch (_: Exception) { null }
        val version = heartbeat?.body()?.version ?: "unknown"
        val capabilities = RomMCapabilities.from(version, heartbeat?.body()?.libretroApiEnabled)
        _connectionState.value = ConnectionState.Connected(version, capabilities)
        saveSyncRepository.get().setCapabilities(capabilities)

        val username = try {
            val userResponse = newApi.getCurrentUser()
            if (userResponse.isSuccessful) userResponse.body()?.username else null
        } catch (_: Exception) {
            null
        }
        userPreferencesRepository.setRomMCredentials(base, body.accessToken, username)
        userPreferencesRepository.setRommDeviceId(body.deviceId, BuildConfig.VERSION_NAME)
        cachedDeviceId = body.deviceId
        saveSyncRepository.get().setDeviceId(body.deviceId)
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
        api = null
        biosRepository.setApi(null)
        saveSyncRepository.get().setCapabilities(RomMCapabilities.NONE)
        accessToken = null
        baseUrl = ""
        cachedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun checkConnection(retryCount: Int = 2) {
        if (api == null) {
            Logger.info(TAG, "checkConnection: api is null, initializing")
            initialize()
            return
        }

        val currentApi = api ?: return
        try {
            val response = currentApi.heartbeat()
            if (response.isSuccessful) {
                val body = response.body()
                val version = body?.version ?: "unknown"
                val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled)
                _connectionState.value = ConnectionState.Connected(version, capabilities)
                saveSyncRepository.get().setCapabilities(capabilities)
                Logger.info(TAG, "checkConnection: connected, version=$version")
            } else {
                Logger.info(TAG, "checkConnection: heartbeat failed with ${response.code()}, reinitializing")
                _connectionState.value = ConnectionState.Disconnected
                api = null
                initialize()
            }
        } catch (e: Exception) {
            Logger.info(TAG, "checkConnection: exception: ${e.message}, retries left=$retryCount")
            _connectionState.value = ConnectionState.Disconnected
            api = null
            if (retryCount > 0) {
                delay(1000)
                initialize()
                if (_connectionState.value !is ConnectionState.Connected && retryCount > 1) {
                    delay(2000)
                    checkConnection(retryCount - 1)
                }
            } else {
                initialize()
            }
        }
    }

    private fun buildLoginScope(): String {
        val baseScope = "me.read me.write platforms.read roms.read assets.read assets.write roms.user.read roms.user.write collections.read collections.write firmware.read"
        return if (isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
            "$baseScope devices.read devices.write"
        } else {
            baseScope
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

    fun createApi(baseUrl: String, token: String?): RomMApi {
        val moshi = Moshi.Builder().build()

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .dns(okhttp3.Dns.SYSTEM)
            .withUserCertTrust(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }
}
