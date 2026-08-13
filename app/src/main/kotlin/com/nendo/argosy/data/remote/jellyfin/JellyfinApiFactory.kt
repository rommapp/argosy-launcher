package com.nendo.argosy.data.remote.jellyfin

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DOWNLOAD_STALL_TIMEOUT_SECONDS = 300
private const val CLIENT_NAME = "Argosy"

/**
 * Builds a Jellyfin client bound to one base URL, one device identity and one token.
 *
 * The identity is not optional decoration: the server keys Quick Connect approvals, transcode
 * sessions and the active-encoding kill switch on the device id sent in the authorization header,
 * so a client built with a different id cannot redeem or stop what another one started. That is why
 * the id is a constructor input rather than something the factory invents per call.
 */
@Singleton
class JellyfinApiFactory @Inject constructor(
    private val moshi: Moshi
) {

    fun create(baseUrl: String, deviceId: String, deviceName: String, token: String?): JellyfinApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        val authValue = buildAuthorizationHeader(deviceId, deviceName, token)

        val authInterceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Authorization", authValue)
                    .build()
            )
        }

        val downloadTimeoutInterceptor = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            if (path.contains("/stream") || path.contains("/Download")) {
                chain.withReadTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .withWriteTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .proceed(chain.request())
            } else {
                chain.proceed(chain.request())
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(downloadTimeoutInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .dns(okhttp3.Dns.SYSTEM)
            .withUserCertTrust(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JellyfinApi::class.java)
    }

    companion object {
        /**
         * Retrofit resolves relative paths against the base URL only when it ends in a slash;
         * without one the last path segment of a subfolder install is silently dropped.
         */
        fun normalizeBaseUrl(baseUrl: String): String =
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        fun buildAuthorizationHeader(deviceId: String, deviceName: String, token: String?): String {
            val parts = mutableListOf(
                "Client=\"${sanitize(CLIENT_NAME)}\"",
                "Device=\"${sanitize(deviceName)}\"",
                "DeviceId=\"${sanitize(deviceId)}\"",
                "Version=\"${sanitize(BuildConfig.VERSION_NAME)}\""
            )
            if (!token.isNullOrBlank()) parts += "Token=\"${sanitize(token)}\""
            return "MediaBrowser " + parts.joinToString(", ")
        }

        /**
         * The header is a quoted, comma-separated list, so a device name carrying a quote or a
         * non-ASCII character produces a header the server parses into the wrong fields or rejects
         * outright. Manufacturer-supplied model names routinely contain both.
         */
        private fun sanitize(value: String): String =
            value.filter { it.code in 32..126 && it != '"' && it != ',' }.trim().ifBlank { "Argosy" }
    }
}
