package com.nendo.argosy.integration

import com.nendo.argosy.data.remote.romm.RomMApi
import com.squareup.moshi.Moshi
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.junit.Assume
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Live-server test base. Password login was removed from the app's auth flows, so these
 * tests authenticate with HTTP Basic (RomM's hybrid auth accepts it) using the same
 * romm-test.properties credentials.
 */
abstract class RomMIntegrationTest {

    protected lateinit var api: RomMApi
    private var basicAuthHeader: String? = null

    @Before
    fun setupApi() {
        Assume.assumeTrue(
            "RomM test credentials not available - skipping integration tests",
            RomMTestConfig.isAvailable
        )
        buildApi()
    }

    protected fun authenticate() {
        basicAuthHeader = Credentials.basic(RomMTestConfig.username, RomMTestConfig.password)
        buildApi()
    }

    private fun buildApi() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val header = basicAuthHeader
                val request = if (header != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", header)
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(RomMTestConfig.url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(RomMApi::class.java)
    }
}
