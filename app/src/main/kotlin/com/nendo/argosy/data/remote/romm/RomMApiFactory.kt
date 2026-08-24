package com.nendo.argosy.data.remote.romm

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

/**
 * Builds a RomM client bound to one base URL and token.
 *
 * Extracted so a client can be built for an account that is not the live one, which is what
 * lets queued work upload under the identity that created it rather than whoever is signed in.
 */
@Singleton
class RomMApiFactory @Inject constructor() {

    fun create(baseUrl: String, token: String?): RomMApi {
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

        val downloadTimeoutInterceptor = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            if (path.contains("/content") || path.endsWith("/api/roms")) {
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
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }
}
