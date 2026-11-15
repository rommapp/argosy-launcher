package com.nendo.argosy.data.cache

import com.nendo.argosy.data.remote.ssl.ClientCertStore
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ArtworkResponse(
    val status: Int?,
    val contentType: String?,
    private val openBody: () -> InputStream,
    private val onClose: () -> Unit
) : Closeable {
    val isSuccess: Boolean get() = status == null || status in 200..299
    val isGone: Boolean get() = status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE

    fun body(): InputStream = openBody()

    override fun close() = onClose()
}

sealed interface ArtworkHead {
    class Present(val lastModifiedMillis: Long) : ArtworkHead
    data object Gone : ArtworkHead
    data object Unknown : ArtworkHead
}

/**
 * Artwork downloads over the app's TLS setup: imported server certificates are trusted and the
 * client certificate is presented to the hosts it is enabled for.
 */
@Singleton
class ArtworkFetcher @Inject constructor(
    private val userCertStore: UserCertStore,
    private val clientCertStore: ClientCertStore
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withUserCertTrust(userCertStore, clientCertStore)
            .build()
    }

    private val headClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(HEAD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    fun open(url: String): ArtworkResponse {
        if (url.toHttpUrlOrNull() == null) return openViaUrlConnection(url)
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        return ArtworkResponse(
            status = response.code,
            contentType = response.header("Content-Type"),
            openBody = { response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)) },
            onClose = response::close
        )
    }

    fun head(url: String): ArtworkHead {
        if (url.toHttpUrlOrNull() == null) return ArtworkHead.Unknown
        return headClient.newCall(Request.Builder().url(url).head().build()).execute().use { it.toHead() }
    }

    private fun Response.toHead(): ArtworkHead = when (code) {
        in 200..299 -> ArtworkHead.Present(headers.getDate("Last-Modified")?.time ?: 0L)
        HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> ArtworkHead.Gone
        else -> ArtworkHead.Unknown
    }

    private fun openViaUrlConnection(url: String): ArtworkResponse {
        val connection: URLConnection = URL(url).openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS.toInt()
        connection.readTimeout = READ_TIMEOUT_MS.toInt()
        return ArtworkResponse(
            status = (connection as? HttpURLConnection)?.responseCode,
            contentType = connection.contentType,
            openBody = connection::getInputStream,
            onClose = { (connection as? HttpURLConnection)?.disconnect() }
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 30_000L
        const val HEAD_READ_TIMEOUT_MS = 10_000L
    }
}
