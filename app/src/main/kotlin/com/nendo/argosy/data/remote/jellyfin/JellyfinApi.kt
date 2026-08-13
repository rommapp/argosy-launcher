package com.nendo.argosy.data.remote.jellyfin

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

/**
 * Jellyfin 10.11 wire surface.
 *
 * Paths are the 10.11 ones: item listing moved from `/Users/{userId}/Items` to `/Items?userId=`,
 * resume moved to `/UserItems/Resume`, and played/favourite writes moved to `/UserPlayedItems` and
 * `/UserFavoriteItems`. The pre-10.11 spellings still exist on some servers but are not what this
 * client speaks.
 */
@Suppress("TooManyFunctions")
interface JellyfinApi {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<JellyfinPublicSystemInfo>

    @GET("Users/Me")
    suspend fun getCurrentUser(): Response<JellyfinUser>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body body: JellyfinAuthenticateByNameRequest
    ): Response<JellyfinAuthenticationResult>

    @GET("QuickConnect/Enabled")
    suspend fun isQuickConnectEnabled(): Response<Boolean>

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(): Response<JellyfinQuickConnectResult>

    @GET("QuickConnect/Connect")
    suspend fun pollQuickConnect(
        @Query("secret") secret: String
    ): Response<JellyfinQuickConnectResult>

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(
        @Body body: JellyfinAuthenticateWithQuickConnectRequest
    ): Response<JellyfinAuthenticationResult>

    @GET("UserViews")
    suspend fun getUserViews(
        @Query("userId") userId: String
    ): Response<JellyfinItemsResponse>

    @GET("Items")
    suspend fun getItems(
        @QueryMap params: Map<String, String>
    ): Response<JellyfinItemsResponse>

    @GET("Items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String
    ): Response<JellyfinItem>

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @QueryMap params: Map<String, String>
    ): Response<JellyfinItemsResponse>

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @QueryMap params: Map<String, String>
    ): Response<JellyfinItemsResponse>

    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @QueryMap params: Map<String, String>
    ): Response<JellyfinItemsResponse>

    @GET("UserItems/Resume")
    suspend fun getResumeItems(
        @QueryMap params: Map<String, String>
    ): Response<JellyfinItemsResponse>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Body body: JellyfinPlaybackInfoRequest
    ): Response<JellyfinPlaybackInfoResponse>

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body body: JellyfinPlaybackStartInfo
    ): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body body: JellyfinPlaybackProgressInfo
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body body: JellyfinPlaybackStopInfo
    ): Response<Unit>

    /**
     * The hard kill for a transcode the server is still feeding. A missed stop report leaks an
     * ffmpeg process until the server's own timeout, so this is the recovery path when a session
     * ended without one.
     */
    @DELETE("Videos/ActiveEncodings")
    suspend fun stopActiveEncoding(
        @Query("deviceId") deviceId: String,
        @Query("playSessionId") playSessionId: String
    ): Response<Unit>

    @POST("UserPlayedItems/{itemId}")
    suspend fun markPlayed(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Query("datePlayed") datePlayed: String? = null
    ): Response<Unit>

    @DELETE("UserPlayedItems/{itemId}")
    suspend fun markUnplayed(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String
    ): Response<Unit>

    @POST("UserFavoriteItems/{itemId}")
    suspend fun markFavorite(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String
    ): Response<Unit>

    @DELETE("UserFavoriteItems/{itemId}")
    suspend fun unmarkFavorite(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String
    ): Response<Unit>

    @GET("MediaSegments/{itemId}")
    suspend fun getMediaSegments(
        @Path("itemId") itemId: String
    ): Response<JellyfinMediaSegmentsResponse>

    @Streaming
    @GET("Videos/{itemId}/stream")
    suspend fun downloadVideo(
        @Path("itemId") itemId: String,
        @QueryMap params: Map<String, String>,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>
}
