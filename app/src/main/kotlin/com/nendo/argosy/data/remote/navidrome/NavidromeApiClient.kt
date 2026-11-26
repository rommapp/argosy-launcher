package com.nendo.argosy.data.remote.navidrome

import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val STATUS_OK = "ok"
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 60L
private const val SCROBBLE_ENDPOINT = "scrobble"

enum class NavidromeAlbumListType(val token: String) {
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    RECENT("recent"),
    FREQUENT("frequent")
}

@Singleton
class NavidromeApiClient @Inject constructor(
    moshi: Moshi,
    private val userCertStore: UserCertStore
) {
    private val envelopeAdapter = moshi.adapter(NavidromeEnvelope::class.java)

    private val _reachable = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits after every successful call other than [scrobble].
     */
    val reachable: SharedFlow<Unit> = _reachable.asSharedFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(userCertStore)
            .build()
    }

    suspend fun ping(credentials: NavidromeCredentials): Result<NavidromeServerInfo> =
        call(credentials, "ping").map {
            NavidromeServerInfo(
                apiVersion = it.version,
                type = it.type,
                serverVersion = it.serverVersion,
                openSubsonic = it.openSubsonic == true
            )
        }

    suspend fun albumList(
        credentials: NavidromeCredentials,
        type: NavidromeAlbumListType,
        size: Int,
        offset: Int
    ): Result<List<NavidromeAlbumDto>> = call(
        credentials,
        "getAlbumList2",
        listOf("type" to type.token, "size" to size.toString(), "offset" to offset.toString())
    ).map { it.albumList2?.album.orEmpty() }

    suspend fun artists(credentials: NavidromeCredentials): Result<List<NavidromeArtistDto>> =
        call(credentials, "getArtists").map { response ->
            response.artists?.index.orEmpty().flatMap { it.artist.orEmpty() }
        }

    suspend fun artistInfo(credentials: NavidromeCredentials, artistId: String): Result<NavidromeArtistInfoDto?> =
        call(credentials, "getArtistInfo2", listOf("id" to artistId)).map { it.artistInfo2 }

    suspend fun album(credentials: NavidromeCredentials, albumId: String): Result<NavidromeAlbumDto> =
        call(credentials, "getAlbum", listOf("id" to albumId)).mapCatching {
            it.album ?: throw IOException("getAlbum returned no album")
        }

    suspend fun playlists(credentials: NavidromeCredentials): Result<List<NavidromePlaylistDto>> =
        call(credentials, "getPlaylists").map { it.playlists?.playlist.orEmpty() }

    suspend fun playlist(credentials: NavidromeCredentials, playlistId: String): Result<NavidromePlaylistDto> =
        call(credentials, "getPlaylist", listOf("id" to playlistId)).mapCatching {
            it.playlist ?: throw IOException("getPlaylist returned no playlist")
        }

    suspend fun addToPlaylist(
        credentials: NavidromeCredentials,
        playlistId: String,
        songIds: List<String>
    ): Result<Unit> = call(
        credentials,
        "updatePlaylist",
        listOf("playlistId" to playlistId) + songIds.map { "songIdToAdd" to it }
    ).map { }

    suspend fun createPlaylist(
        credentials: NavidromeCredentials,
        name: String,
        songIds: List<String>
    ): Result<NavidromePlaylistDto> = call(
        credentials,
        "createPlaylist",
        listOf("name" to name) + songIds.map { "songId" to it }
    ).mapCatching { it.playlist ?: throw IOException("createPlaylist returned no playlist") }

    suspend fun renamePlaylist(
        credentials: NavidromeCredentials,
        playlistId: String,
        name: String
    ): Result<Unit> = call(
        credentials,
        "updatePlaylist",
        listOf("playlistId" to playlistId, "name" to name)
    ).map { }

    /**
     * Removes the entry at [index], 0-based in the playlist's current song list.
     */
    suspend fun removeFromPlaylist(
        credentials: NavidromeCredentials,
        playlistId: String,
        index: Int
    ): Result<Unit> = call(
        credentials,
        "updatePlaylist",
        listOf("playlistId" to playlistId, "songIndexToRemove" to index.toString())
    ).map { }

    suspend fun deletePlaylist(credentials: NavidromeCredentials, playlistId: String): Result<Unit> =
        call(credentials, "deletePlaylist", listOf("id" to playlistId)).map { }

    /**
     * Reports a play of [songId]. `submission=false` is a now-playing notice; `submission=true`
     * records the play at [timeMs], the epoch millis the listen started.
     */
    suspend fun scrobble(
        credentials: NavidromeCredentials,
        songId: String,
        submission: Boolean,
        timeMs: Long?
    ): Result<Unit> = call(
        credentials,
        SCROBBLE_ENDPOINT,
        listOfNotNull(
            "id" to songId,
            "submission" to submission.toString(),
            timeMs?.let { "time" to it.toString() }
        )
    ).map { }

    fun coverArtUrl(credentials: NavidromeCredentials, coverArtId: String, size: Int): String? =
        endpointUrl(credentials, "getCoverArt", listOf("id" to coverArtId, "size" to size.toString()))
            ?.toString()

    fun streamUrl(credentials: NavidromeCredentials, songId: String): String? =
        endpointUrl(credentials, "stream", listOf("id" to songId))?.toString()

    private suspend fun call(
        credentials: NavidromeCredentials,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList()
    ): Result<NavidromeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = endpointUrl(credentials, endpoint, params)
                ?: throw IOException("Invalid server address")
            httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("$endpoint failed with HTTP ${response.code}")
                val body = response.body?.string() ?: throw IOException("$endpoint returned no body")
                parseResponse(body, envelopeAdapter::fromJson)
            }
        }.onSuccess { if (endpoint != SCROBBLE_ENDPOINT) _reachable.tryEmit(Unit) }
    }

    private fun endpointUrl(
        credentials: NavidromeCredentials,
        endpoint: String,
        params: List<Pair<String, String>>
    ): HttpUrl? {
        val base = credentials.serverUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder().addPathSegment("rest").addPathSegment(endpoint)
        val auth = NavidromeAuth.authParams(credentials.username, credentials.password, NavidromeAuth.newSalt())
        (auth + params).forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    companion object {
        /**
         * Unwraps the `subsonic-response` envelope. A `failed` status throws [NavidromeApiException]
         * carrying the server's code and message; a body with no envelope throws [IOException].
         */
        fun parseResponse(body: String, decode: (String) -> NavidromeEnvelope?): NavidromeResponse {
            val response = decode(body)?.response ?: throw IOException("Not a Subsonic response")
            if (response.status != STATUS_OK) {
                throw NavidromeApiException(
                    code = response.error?.code,
                    serverMessage = response.error?.message?.takeIf { it.isNotBlank() }
                )
            }
            return response
        }
    }
}
