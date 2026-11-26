package com.nendo.argosy.data.remote.romm

import kotlinx.coroutines.CancellationException
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TRACK_PAGE_SIZE = 500
private const val MAX_QUEUE_TRACKS = 5_000

@Singleton
class RomMMusicLibraryService @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val apiClient: RomMApiClient
) {
    fun isConnected(): Boolean = connectionManager.isConnected()

    fun capabilities(): RomMCapabilities = connectionManager.getCapabilities()

    suspend fun getPlaylists(): RomMResult<List<RomMMusicPlaylist>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return try {
            val response = api.getMusicPlaylists()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music playlists", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music playlists")
        }
    }

    suspend fun createPlaylist(name: String): RomMResult<RomMMusicPlaylist> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return try {
            val response = api.createMusicPlaylist(RomMMusicPlaylistCreate(name))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to create music playlist", response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to create music playlist")
        }
    }

    suspend fun renamePlaylist(playlistId: Long, name: String): RomMResult<Unit> =
        playlistWrite("Failed to rename music playlist") {
            it.renameMusicPlaylist(playlistId, RomMMusicPlaylistRename(name))
        }

    suspend fun deletePlaylist(playlistId: Long): RomMResult<Unit> =
        playlistWrite("Failed to delete music playlist") { it.deleteMusicPlaylist(playlistId) }

    suspend fun addPlaylistTracks(playlistId: Long, romFileIds: List<Long>): RomMResult<Unit> =
        playlistWrite("Failed to add music playlist tracks") {
            it.addMusicPlaylistTracks(playlistId, RomMMusicPlaylistTracks(romFileIds))
        }

    suspend fun removePlaylistTracks(playlistId: Long, romFileIds: List<Long>): RomMResult<Unit> =
        playlistWrite("Failed to remove music playlist tracks") {
            it.removeMusicPlaylistTracks(playlistId, RomMMusicPlaylistTracks(romFileIds))
        }

    private suspend fun playlistWrite(
        failure: String,
        call: suspend (RomMApi) -> retrofit2.Response<*>
    ): RomMResult<Unit> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return try {
            val response = call(api)
            if (response.isSuccessful) RomMResult.Success(Unit) else RomMResult.Error(failure, response.code())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: failure)
        }
    }

    suspend fun getGames(
        search: String?,
        minDurationSeconds: Double,
        limit: Int,
        offset: Int
    ): RomMResult<RomMMusicGamePage> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        val params = buildMap {
            search?.let { put("search", it) }
            put("min_duration", minDurationSeconds.toString())
            put("order_by", "value")
            put("order_dir", "asc")
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        return try {
            val response = api.getMusicGames(params)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music games", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music games")
        }
    }

    suspend fun getPlaylistTracks(playlistId: Long): RomMResult<List<RomMMusicTrack>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return collectPages { limit, offset ->
            api.getMusicPlaylistTracks(
                playlistId,
                mapOf(
                    "order_by" to "position",
                    "order_dir" to "asc",
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
        }
    }

    suspend fun getGameTracks(romId: Long, minDurationSeconds: Double): RomMResult<List<RomMMusicTrack>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return collectPages { limit, offset ->
            api.getMusicTracks(
                mapOf(
                    "rom_id" to romId.toString(),
                    "min_duration" to minDurationSeconds.toString(),
                    "order_by" to "title",
                    "order_dir" to "asc",
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
        }
    }

    /**
     * One page of every track the server lists, oldest file first, so a page walk is not
     * shifted by tracks added while it runs.
     */
    suspend fun getTrackPage(
        minDurationSeconds: Double,
        limit: Int,
        offset: Int
    ): RomMResult<RomMMusicTrackPage> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return try {
            val response = api.getMusicTracks(
                mapOf(
                    "min_duration" to minDurationSeconds.toString(),
                    "order_by" to "added",
                    "order_dir" to "asc",
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music tracks", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music tracks")
        }
    }

    private suspend fun collectPages(
        fetch: suspend (limit: Int, offset: Int) -> retrofit2.Response<RomMMusicTrackPage>
    ): RomMResult<List<RomMMusicTrack>> {
        val collected = mutableListOf<RomMMusicTrack>()
        return try {
            while (collected.size < MAX_QUEUE_TRACKS) {
                val response = fetch(TRACK_PAGE_SIZE, collected.size)
                val page = response.body()
                if (!response.isSuccessful || page == null) {
                    return RomMResult.Error("Failed to fetch music tracks", response.code())
                }
                collected += page.items
                if (page.items.size < TRACK_PAGE_SIZE || collected.size >= page.total) break
            }
            RomMResult.Success(collected.toList())
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music tracks")
        }
    }

    fun streamUrl(encodedPath: String?): String? =
        if (connectionManager.getBaseUrl().isBlank()) null else apiClient.buildMediaUrl(encodedPath)

    /**
     * Absolute URL for a raw resource path RomM reports unencoded, such as a music `cover_url`.
     * Each path segment is percent-encoded; a query string is kept as sent.
     */
    fun resourceUrl(rawPath: String?): String? {
        if (connectionManager.getBaseUrl().isBlank()) return null
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.startsWith("http")) return apiClient.buildMediaUrl(trimmed)
        return apiClient.buildMediaUrl(encodeResourcePath(trimmed))
    }

    fun authHeaders(url: String, token: String?): Map<String, String> =
        bearerHeaders(url, connectionManager.getBaseUrl(), token)

    companion object {
        /**
         * The RomM bearer header for [url], or none when [url] is not on [baseUrl]'s scheme, host
         * and port, so a stream or preview naming another host never receives the token.
         */
        fun bearerHeaders(url: String, baseUrl: String, token: String?): Map<String, String> =
            if (token != null && sameOrigin(url, baseUrl)) mapOf("Authorization" to "Bearer $token") else emptyMap()

        fun encodeResourcePath(rawPath: String): String {
            val pathPart = rawPath.substringBefore('?')
            val query = rawPath.substringAfter('?', "")
            val encoded = pathPart.trimStart('/').split('/').joinToString("/") { encodeSegment(it) }
            return "/" + encoded + if (query.isNotEmpty()) "?$query" else ""
        }

        fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

        fun sameOrigin(url: String, baseUrl: String): Boolean {
            if (baseUrl.isBlank()) return false
            val target = runCatching { URI(url) }.getOrNull() ?: return false
            val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
            return target.scheme.equals(base.scheme, ignoreCase = true) &&
                target.host.equals(base.host, ignoreCase = true) &&
                effectivePort(target) == effectivePort(base)
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("http", ignoreCase = true) -> 80
            else -> -1
        }
    }
}
