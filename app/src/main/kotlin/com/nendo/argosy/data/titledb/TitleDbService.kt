package com.nendo.argosy.data.titledb

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TitleDbService"

@Singleton
class TitleDbService @Inject constructor(
    private val requestSigner: RequestSigner,
    private val moshi: Moshi
) {
    private val baseUrl: String = BuildConfig.TITLEDB_API_URL

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val resultAdapter by lazy {
        moshi.adapter(TitleLookupResult::class.java)
    }

    private val variantsAdapter by lazy {
        moshi.adapter(TitleVariantsResult::class.java)
    }

    fun isConfigured(): Boolean = requestSigner.isConfigured() && baseUrl.isNotBlank()

    suspend fun lookupByName(
        name: String,
        platform: String,
        deviceToken: String
    ): TitleLookupResult? = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Logger.debug(TAG, "TitleDB not configured, skipping lookup")
            return@withContext null
        }

        val signed = requestSigner.sign(deviceToken, name)
        if (signed == null) {
            Logger.debug(TAG, "Failed to sign request")
            return@withContext null
        }

        val encodedName = URLEncoder.encode(name, "UTF-8")
        val url = "$baseUrl/api/titledb/lookup?name=$encodedName&platform=$platform"

        val request = Request.Builder()
            .url(url)
            .header("X-Device-Token", signed.deviceToken)
            .header("X-Timestamp", signed.timestamp.toString())
            .header("X-Signature", signed.signature)
            .header("X-FP-Hash", signed.fpHash)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Logger.debug(TAG, "Lookup failed: ${response.code} for name=$name, platform=$platform")
                return@withContext null
            }

            val body = response.body?.string()
            if (body == null) {
                Logger.debug(TAG, "Empty response body")
                return@withContext null
            }

            val result = resultAdapter.fromJson(body)
            Logger.debug(TAG, "Lookup result: titleId=${result?.titleId}, name=${result?.name}, score=${result?.score}")
            result
        } catch (e: Exception) {
            Logger.error(TAG, "Lookup error for name=$name: ${e.message}")
            null
        }
    }

    suspend fun lookupByTitleId(
        titleId: String,
        platform: String,
        deviceToken: String
    ): TitleLookupResult? = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext null
        }

        val signed = requestSigner.sign(deviceToken, titleId)
        if (signed == null) {
            return@withContext null
        }

        val url = "$baseUrl/api/titledb/lookup-by-id?title_id=$titleId&platform=$platform"

        val request = Request.Builder()
            .url(url)
            .header("X-Device-Token", signed.deviceToken)
            .header("X-Timestamp", signed.timestamp.toString())
            .header("X-Signature", signed.signature)
            .header("X-FP-Hash", signed.fpHash)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            response.body?.string()?.let { resultAdapter.fromJson(it) }
        } catch (e: Exception) {
            Logger.error(TAG, "Lookup by ID error: ${e.message}")
            null
        }
    }

    suspend fun lookupVariants(
        name: String,
        platform: String,
        deviceToken: String,
        limit: Int = 10
    ): TitleVariantsResult? = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Logger.debug(TAG, "TitleDB not configured, skipping variants lookup")
            return@withContext null
        }

        val signed = requestSigner.sign(deviceToken, name)
        if (signed == null) {
            Logger.debug(TAG, "Failed to sign variants request")
            return@withContext null
        }

        val encodedName = URLEncoder.encode(name, "UTF-8")
        val url = "$baseUrl/api/titledb/lookup-variants?name=$encodedName&platform=$platform&limit=$limit"

        val request = Request.Builder()
            .url(url)
            .header("X-Device-Token", signed.deviceToken)
            .header("X-Timestamp", signed.timestamp.toString())
            .header("X-Signature", signed.signature)
            .header("X-FP-Hash", signed.fpHash)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Logger.debug(TAG, "Variants lookup failed: ${response.code} for name=$name, platform=$platform")
                return@withContext null
            }

            val body = response.body?.string()
            if (body == null) {
                Logger.debug(TAG, "Empty variants response body")
                return@withContext null
            }

            val result = variantsAdapter.fromJson(body)
            Logger.debug(TAG, "Variants result: ${result?.candidates?.size} candidates, bestMatch=${result?.bestMatch?.titleId}")
            result
        } catch (e: Exception) {
            Logger.error(TAG, "Variants lookup error for name=$name: ${e.message}")
            null
        }
    }
}
