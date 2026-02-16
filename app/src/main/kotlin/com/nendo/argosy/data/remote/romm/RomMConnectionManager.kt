package com.nendo.argosy.data.remote.romm

import android.content.Context
import android.os.Build
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

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val version: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun getApi(): RomMApi? = api

    fun getBaseUrl(): String = baseUrl

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun getDeviceId(): String? = cachedDeviceId

    fun getConnectedVersion(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.version
    }

    fun isVersionAtLeast(minVersion: String): Boolean {
        val current = getConnectedVersion() ?: return false
        return compareVersions(current, minVersion) >= 0
    }

    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
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
                    val version = response.body()?.version ?: "unknown"
                    _connectionState.value = ConnectionState.Connected(version)
                    Logger.info(TAG, "connect: success at $normalizedUrl, version=$version")
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

    fun disconnect() {
        api = null
        biosRepository.setApi(null)
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
                val version = response.body()?.version ?: "unknown"
                _connectionState.value = ConnectionState.Connected(version)
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
            val registration = RomMDeviceRegistration(
                name = deviceName,
                clientVersion = clientVersion
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
            level = HttpLoggingInterceptor.Level.HEADERS
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
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }
}
