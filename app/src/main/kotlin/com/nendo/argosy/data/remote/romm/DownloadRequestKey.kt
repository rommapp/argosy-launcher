package com.nendo.argosy.data.remote.romm

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Identity of a RomM download request that survives a change of server address.
 *
 * The key is the encoded path from its `/api/` segment onward plus the encoded query, so scheme,
 * host, port, credentials and any base-path prefix in front of `/api/` never take part.
 */
object DownloadRequestKey {
    private const val API_SEGMENT = "/api/"

    fun of(url: HttpUrl): String {
        val path = url.encodedPath
        val start = path.indexOf(API_SEGMENT).coerceAtLeast(0)
        val query = url.encodedQuery?.let { "?$it" }.orEmpty()
        return path.substring(start) + query
    }

    /**
     * Reduces a stored key to the [of] form. A full http(s) URL becomes its key; any other value
     * is returned unchanged.
     */
    fun normalize(key: String): String {
        val isUrl = key.startsWith("http://", ignoreCase = true) || key.startsWith("https://", ignoreCase = true)
        if (!isUrl) return key
        return key.toHttpUrlOrNull()?.let(::of) ?: key
    }
}
