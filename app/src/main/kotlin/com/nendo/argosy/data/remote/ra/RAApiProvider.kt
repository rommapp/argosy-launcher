package com.nendo.argosy.data.remote.ra

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.nendo.argosy.util.ServerAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RAApiProvider @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) {
    @Volatile private var cachedApi: RAApi? = null
    @Volatile private var cachedBaseUrl: String? = null

    suspend fun api(): RAApi {
        val baseUrl = resolveBaseUrl()
        cachedApi?.let { if (cachedBaseUrl == baseUrl) return it }
        return createApi(baseUrl).also {
            cachedApi = it
            cachedBaseUrl = baseUrl
        }
    }

    private suspend fun resolveBaseUrl(): String {
        val prefs = prefsRepository.userPreferences.first()
        if (!prefs.raProxyEnabled) return RAApi.BASE_URL
        return normalizeProxyBaseUrl(prefs.raProxyAddress) ?: RAApi.BASE_URL
    }

    private fun normalizeProxyBaseUrl(raw: String): String? {
        val withScheme = ServerAddress.withDefaultScheme(raw)
        if (withScheme.isEmpty()) return null
        return withScheme.toHttpUrlOrNull()?.toString()
    }

    private fun createApi(baseUrl: String): RAApi {
        val moshi = Moshi.Builder().build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Argosy/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE}) rcheevos/${RAApi.RCHEEVOS_VERSION}")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RAApi::class.java)
    }
}
