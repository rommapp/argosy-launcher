package com.nendo.argosy.data.remote.jellyfin

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every request the app makes against Jellyfin, wrapped so a caller reasons about a result rather
 * than about Retrofit responses and thrown transport exceptions.
 *
 * It also owns the query-parameter and URL construction. Image, trickplay and subtitle addresses are
 * built here rather than at each call site because they are content-addressed by tag - the same
 * address always returns the same bytes and can cache forever - and a hand-built variant that omits
 * the tag silently loses that.
 */
@Suppress("TooManyFunctions")
@Singleton
class JellyfinApiClient @Inject constructor(
    private val connectionManager: JellyfinConnectionManager
) {
    internal val api: JellyfinApi? get() = connectionManager.getApi()
    internal val baseUrl: String get() = connectionManager.getBaseUrl()

    fun getCapabilities(): JellyfinCapabilities = connectionManager.getCapabilities()

    fun currentUserId(): String? = connectionManager.getUserId()

    fun buildItemQueryParams(
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean = true,
        startIndex: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
        sortBy: String = SORT_BY_SORT_NAME,
        sortOrder: String = SORT_ORDER_ASCENDING,
        fields: String = ITEM_FIELDS
    ): Map<String, String> = buildMap {
        put("userId", userId)
        parentId?.let { put("parentId", it) }
        includeItemTypes?.let { put("includeItemTypes", it) }
        put("recursive", recursive.toString())
        put("startIndex", startIndex.toString())
        put("limit", limit.toString())
        put("sortBy", sortBy)
        put("sortOrder", sortOrder)
        put("fields", fields)
    }

    fun buildSeasonQueryParams(userId: String, fields: String = ITEM_FIELDS): Map<String, String> =
        mapOf("userId" to userId, "fields" to fields)

    /**
     * Episodes for one season. The season is passed as `seasonId` rather than as the paging parent
     * because the endpoint answers for a whole series otherwise, and a series here runs to hundreds
     * of episodes.
     */
    fun buildEpisodeQueryParams(
        userId: String,
        seasonId: String? = null,
        startIndex: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
        fields: String = ITEM_FIELDS
    ): Map<String, String> = buildMap {
        put("userId", userId)
        seasonId?.let { put("seasonId", it) }
        put("startIndex", startIndex.toString())
        put("limit", limit.toString())
        put("fields", fields)
    }

    fun buildNextUpQueryParams(
        userId: String,
        limit: Int = DEFAULT_RAIL_SIZE,
        fields: String = RAIL_FIELDS
    ): Map<String, String> = mapOf(
        "userId" to userId,
        "limit" to limit.toString(),
        "fields" to fields
    )

    fun buildResumeQueryParams(
        userId: String,
        limit: Int = DEFAULT_RAIL_SIZE,
        fields: String = RAIL_FIELDS
    ): Map<String, String> = mapOf(
        "userId" to userId,
        "limit" to limit.toString(),
        "fields" to fields,
        "mediaTypes" to "Video"
    )

    suspend fun getPublicSystemInfo(): JellyfinResult<JellyfinPublicSystemInfo> =
        call("Failed to read server info") { it.getPublicSystemInfo() }

    suspend fun getCurrentUser(): JellyfinResult<JellyfinUser> =
        call("Failed to read the signed-in user") { it.getCurrentUser() }

    suspend fun getUserViews(userId: String): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch libraries") { it.getUserViews(userId) }

    suspend fun getItems(params: Map<String, String>): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch items") { it.getItems(params) }

    suspend fun getItem(itemId: String, userId: String): JellyfinResult<JellyfinItem> =
        call("Failed to fetch item") { it.getItem(itemId, userId) }

    suspend fun getSeasons(
        seriesId: String,
        params: Map<String, String>
    ): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch seasons") { it.getSeasons(seriesId, params) }

    suspend fun getEpisodes(
        seriesId: String,
        params: Map<String, String>
    ): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch episodes") { it.getEpisodes(seriesId, params) }

    suspend fun getNextUp(params: Map<String, String>): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch next up") { it.getNextUp(params) }

    suspend fun getResumeItems(params: Map<String, String>): JellyfinResult<JellyfinItemsResponse> =
        call("Failed to fetch continue watching") { it.getResumeItems(params) }

    /**
     * Negotiates one playback. The answer depends on the current network, the profile and the
     * server's own load, and the addresses in it expire with the transcode session, so it is called
     * per playback and never cached.
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        request: JellyfinPlaybackInfoRequest
    ): JellyfinResult<JellyfinPlaybackInfoResponse> =
        call("Failed to negotiate playback") { it.getPlaybackInfo(itemId, request) }

    suspend fun reportPlaybackStart(body: JellyfinPlaybackStartInfo): JellyfinResult<Unit> =
        callUnit("Failed to report playback start") { it.reportPlaybackStart(body) }

    suspend fun reportPlaybackProgress(body: JellyfinPlaybackProgressInfo): JellyfinResult<Unit> =
        callUnit("Failed to report playback progress") { it.reportPlaybackProgress(body) }

    suspend fun reportPlaybackStopped(body: JellyfinPlaybackStopInfo): JellyfinResult<Unit> =
        callUnit("Failed to report playback stop") { it.reportPlaybackStopped(body) }

    suspend fun stopActiveEncoding(playSessionId: String): JellyfinResult<Unit> {
        val device = connectionManager.getDeviceId()
            ?: return JellyfinResult.Error("No device identity")
        return callUnit("Failed to stop the transcode") { it.stopActiveEncoding(device, playSessionId) }
    }

    suspend fun setPlayed(itemId: String, userId: String, played: Boolean): JellyfinResult<Unit> =
        callUnit("Failed to update watched state") {
            if (played) it.markPlayed(itemId, userId) else it.markUnplayed(itemId, userId)
        }

    suspend fun setFavorite(itemId: String, userId: String, favorite: Boolean): JellyfinResult<Unit> =
        callUnit("Failed to update favourite") {
            if (favorite) it.markFavorite(itemId, userId) else it.unmarkFavorite(itemId, userId)
        }

    suspend fun getMediaSegments(itemId: String): JellyfinResult<JellyfinMediaSegmentsResponse> {
        if (!getCapabilities().supportsMediaSegments) {
            return JellyfinResult.Error("Server does not serve media segments")
        }
        return call("Failed to fetch media segments") { it.getMediaSegments(itemId) }
    }

    /**
     * Image addresses carry the tag the server gave for that image, which makes them
     * content-addressed: the bytes behind one never change, so a cache can hold them indefinitely
     * and a changed artwork arrives as a different address rather than as a stale hit.
     */
    fun buildImageUrl(
        itemId: String,
        imageType: String = IMAGE_TYPE_PRIMARY,
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null
    ): String {
        val query = buildList {
            tag?.let { add("tag=$it") }
            maxWidth?.let { add("maxWidth=$it") }
            maxHeight?.let { add("maxHeight=$it") }
            quality?.let { add("quality=$it") }
        }
        val suffix = if (query.isEmpty()) "" else "?" + query.joinToString("&")
        return "$baseUrl/Items/$itemId/Images/$imageType$suffix"
    }

    fun buildTrickplayTileUrl(itemId: String, width: Int, index: Int): String =
        "$baseUrl/Videos/$itemId/Trickplay/$width/$index.jpg"

    /**
     * The address of one external subtitle track. The start offset is part of the path rather than a
     * query parameter, so a track fetched mid-playback is already aligned and does not need shifting
     * client-side. It has no default: the offset a caller wants is the offset the picture starts at,
     * and a silent zero puts the whole track ahead of the film by the resume position.
     */
    fun buildSubtitleUrl(
        itemId: String,
        mediaSourceId: String,
        streamIndex: Int,
        format: String,
        startPositionTicks: Long
    ): String = "$baseUrl/Videos/$itemId/$mediaSourceId/Subtitles/$streamIndex/" +
        "$startPositionTicks/Stream.$format"

    fun buildStreamUrl(itemId: String, transcodingUrl: String?): String? {
        if (transcodingUrl.isNullOrBlank()) return null
        return if (transcodingUrl.startsWith("http")) transcodingUrl
               else "$baseUrl${transcodingUrl.replace("&amp;", "&")}"
    }

    /**
     * Parameters for fetching the source file itself, used by the download path. `static=true` is
     * what tells the server to hand over the file rather than open a transcode.
     */
    fun buildOriginalFileParams(mediaSourceId: String): Map<String, String> = mapOf(
        "static" to "true",
        "mediaSourceId" to mediaSourceId
    )

    private suspend fun <T> call(
        failureMessage: String,
        block: suspend (JellyfinApi) -> retrofit2.Response<T>
    ): JellyfinResult<T> {
        val currentApi = api ?: return JellyfinResult.Error("Not connected")
        return try {
            val response = block(currentApi)
            if (response.isSuccessful) {
                val body = response.body() ?: return JellyfinResult.Error("Empty response from server")
                JellyfinResult.Success(body)
            } else {
                JellyfinResult.Error(failureMessage, response.code())
            }
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: failureMessage)
        }
    }

    /**
     * The reporting endpoints answer 204 with no body, so an empty body is the success case here
     * rather than the failure it is for [call].
     */
    private suspend fun callUnit(
        failureMessage: String,
        block: suspend (JellyfinApi) -> retrofit2.Response<Unit>
    ): JellyfinResult<Unit> {
        val currentApi = api ?: return JellyfinResult.Error("Not connected")
        return try {
            val response = block(currentApi)
            if (response.isSuccessful) JellyfinResult.Success(Unit)
            else JellyfinResult.Error(failureMessage, response.code())
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: failureMessage)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 200
        const val DEFAULT_RAIL_SIZE = 40

        const val SORT_BY_SORT_NAME = "SortName"
        const val SORT_ORDER_ASCENDING = "Ascending"

        /**
         * Everything the library rows need and nothing more. Media sources and streams are
         * deliberately absent: they are per-playback answers, they multiply the response size of a
         * page by an order of magnitude, and a cached copy of them would go stale.
         */
        const val ITEM_FIELDS = "Overview,Genres,Studios,DateCreated,SortName,ChildCount,ParentId"

        /**
         * A home rail draws a tile and starts playback from it, so it needs the hierarchy an
         * episode sits in but none of the descriptive metadata.
         */
        const val RAIL_FIELDS = "SeriesId,ParentId,SortName"
    }
}
