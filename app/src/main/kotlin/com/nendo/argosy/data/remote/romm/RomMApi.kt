package com.nendo.argosy.data.remote.romm

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

@Suppress("TooManyFunctions")
interface RomMApi {

    @GET("api/heartbeat")
    suspend fun heartbeat(): Response<RomMHeartbeatResponse>

    @POST("api/client-tokens/exchange")
    suspend fun exchangePairingCode(
        @Body body: RomMPairingExchangeRequest
    ): Response<RomMPairingExchangeResponse>

    @POST("api/auth/device/init")
    suspend fun deviceAuthInit(
        @Body body: RomMDeviceAuthInitRequest
    ): Response<RomMDeviceAuthInitResponse>

    @POST("api/auth/device/token")
    suspend fun deviceAuthToken(
        @Body body: RomMDeviceAuthTokenRequest
    ): Response<RomMDeviceAuthTokenResponse>

    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<RomMUser>

    @GET("api/permissions/me")
    suspend fun getMyPermissions(): Response<RomMPermissionsResponse>

    @POST("api/users/{id}/ra/refresh")
    suspend fun refreshRAProgression(
        @Path("id") userId: Long,
        @Body body: RomMRARefreshRequest = RomMRARefreshRequest()
    ): Response<Unit>

    @GET("api/search/cover")
    suspend fun searchCovers(
        @Query("search_term") searchTerm: String,
        @Query("art_type") artType: String
    ): Response<List<RomMCoverSearchResult>>

    @GET("api/platforms")
    suspend fun getPlatforms(): Response<List<RomMPlatform>>

    @GET("api/platforms/identifiers")
    suspend fun getPlatformIdentifiers(): Response<List<Long>>

    @GET("api/platforms/{id}")
    suspend fun getPlatform(
        @Path("id") platformId: Long
    ): Response<RomMPlatform>

    @GET("api/roms")
    suspend fun getRoms(
        @QueryMap params: Map<String, String>
    ): Response<RomMRomPage>

    @GET("api/roms/identifiers")
    suspend fun getRomIdentifiers(): Response<List<Long>>

    @GET("api/roms/{id}")
    suspend fun getRom(
        @Path("id") romId: Long
    ): Response<RomMRom>

    @Streaming
    @GET("api/roms/{id}/content/{fileName}")
    suspend fun downloadRom(
        @Path("id") romId: Long,
        @Path("fileName", encoded = true) fileName: String,
        @Header("Range") range: String? = null,
        @Query("file_ids") fileIds: String? = null
    ): Response<ResponseBody>

    @Streaming
    @GET("api/roms/{fileId}/files/content/{fileName}")
    suspend fun downloadRomFile(
        @Path("fileId") fileId: Long,
        @Path("fileName", encoded = true) fileName: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @Streaming
    @Headers("Accept-Encoding: identity")
    @GET("api/roms/{id}/content/{fileName}")
    fun downloadRomCall(
        @Path("id") romId: Long,
        @Path("fileName", encoded = true) fileName: String,
        @Header("Range") range: String? = null,
        @Query("file_ids") fileIds: String? = null,
        @Header("If-Range") ifRange: String? = null
    ): Call<ResponseBody>

    @Streaming
    @Headers("Accept-Encoding: identity")
    @GET("api/roms/{fileId}/files/content/{fileName}")
    fun downloadRomFileCall(
        @Path("fileId") fileId: Long,
        @Path("fileName", encoded = true) fileName: String,
        @Header("Range") range: String? = null,
        @Header("If-Range") ifRange: String? = null
    ): Call<ResponseBody>

    @GET("api/music/tracks")
    suspend fun getMusicTracks(
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicTrackPage>

    @GET("api/music/{facet}")
    suspend fun getMusicFacet(
        @Path("facet") facet: String,
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicFacetPage>

    @GET("api/music/games")
    suspend fun getMusicGames(
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicGamePage>

    @GET("api/music/playlists")
    suspend fun getMusicPlaylists(): Response<List<RomMMusicPlaylist>>

    @GET("api/music/playlists/{id}/tracks")
    suspend fun getMusicPlaylistTracks(
        @Path("id") playlistId: Long,
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicTrackPage>

    @PUT("api/roms/{id}/props")
    suspend fun updateRomUserProps(
        @Path("id") romId: Long,
        @Body props: RomMUserPropsUpdateData
    ): Response<Unit>

    @GET("api/collections")
    suspend fun getCollections(
        @Query("is_favorite") isFavorite: Boolean? = null
    ): Response<List<RomMCollection>>

    @GET("api/collections/virtual")
    suspend fun getVirtualCollections(
        @Query("type") type: String
    ): Response<List<RomMAutoCollection>>

    @GET("api/collections/smart")
    suspend fun getSmartCollections(): Response<List<RomMAutoCollection>>

    @Multipart
    @POST("api/collections")
    suspend fun createCollection(
        @Query("is_favorite") isFavorite: Boolean = false,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody? = null
    ): Response<RomMCollection>

    @Multipart
    @PUT("api/collections/{id}")
    suspend fun updateCollection(
        @Path("id") collectionId: Long,
        @Part("rom_ids") romIds: RequestBody
    ): Response<RomMCollection>

    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(
        @Path("id") collectionId: Long
    ): Response<Unit>

    @GET("api/saves")
    suspend fun getAllSaves(): Response<List<RomMSave>>

    @GET("api/saves")
    suspend fun getSavesByRom(
        @Query("rom_id") romId: Long
    ): Response<List<RomMSave>>

    @GET("api/saves")
    suspend fun getSavesByPlatform(
        @Query("platform_id") platformId: Long
    ): Response<List<RomMSave>>

    @GET("api/saves/{id}")
    suspend fun getSave(
        @Path("id") saveId: Long
    ): Response<RomMSave>

    @Multipart
    @POST("api/saves")
    suspend fun uploadSave(
        @Query("rom_id") romId: Long,
        @Query("emulator") emulator: String?,
        @Query("slot") slot: String? = null,
        @Query("autocleanup") autocleanup: Boolean = false,
        @Query("autocleanup_limit") autocleanupLimit: Int? = null,
        @Part saveFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMSave>

    @Multipart
    @PUT("api/saves/{id}")
    suspend fun updateSave(
        @Path("id") saveId: Long,
        @Query("slot") slot: String? = null,
        @Part saveFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMSave>

    @POST("api/saves/delete")
    suspend fun deleteSaves(
        @Body body: RomMDeleteSavesRequest
    ): Response<List<Long>>

    // State endpoints

    @GET("api/states")
    suspend fun getStatesByRom(
        @Query("rom_id") romId: Long
    ): Response<List<RomMState>>

    @GET("api/states/{id}")
    suspend fun getState(
        @Path("id") stateId: Long
    ): Response<RomMState>

    @Multipart
    @POST("api/states")
    suspend fun uploadState(
        @Query("rom_id") romId: Long,
        @Query("emulator") emulator: String?,
        @Part stateFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMState>

    @Multipart
    @PUT("api/states/{id}")
    suspend fun updateState(
        @Path("id") stateId: Long,
        @Part stateFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMState>

    @POST("api/states/delete")
    suspend fun deleteStates(
        @Body body: RomMDeleteStatesRequest
    ): Response<List<Long>>

    @Streaming
    @GET("api/saves/{id}/content")
    suspend fun downloadSaveContent(
        @Path("id") saveId: Long
    ): Response<ResponseBody>

    @GET("api/channels")
    suspend fun listChannels(
        @Query("rom_file_id") romFileIds: List<Long>
    ): Response<List<RomMChannel>>

    @POST("api/channels")
    suspend fun postChannel(@Body body: RomMChannelCreate): Response<RomMChannel>

    @retrofit2.http.PATCH("api/channels/{id}")
    suspend fun updateChannel(@Path("id") channelId: String, @Body body: RomMChannelUpdate): Response<RomMChannel>

    @DELETE("api/channels/{id}")
    suspend fun deleteChannel(@Path("id") channelId: String): Response<Unit>

    @GET("api/snapshots")
    suspend fun listSnapshotHistory(
        @Query("channel_id") channelId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String? = null
    ): Response<List<RomMSnapshot>>

    @retrofit2.http.PATCH("api/snapshots/{id}")
    suspend fun updateSnapshot(@Path("id") snapshotId: Long, @Body body: RomMSnapshotUpdate): Response<RomMSnapshot>

    @GET("api/snapshots/{id}")
    suspend fun getSnapshot(
        @Path("id") snapshotId: Long,
        @Query("device_id") deviceId: String? = null,
        @Query("hold") hold: Boolean? = null
    ): Response<RomMSnapshot>

    @Multipart
    @POST("api/snapshots")
    suspend fun pushSnapshot(
        @Query("device_id") deviceId: String,
        @Part parts: List<MultipartBody.Part>
    ): Response<ResponseBody>

    @PUT("api/snapshots/{id}/devices/{deviceId}")
    suspend fun reportSnapshotHeld(
        @Path("id") snapshotId: Long,
        @Path("deviceId") deviceId: String
    ): Response<Unit>

    @Multipart
    @POST("api/screenshots")
    suspend fun uploadScreenshot(
        @Query("rom_id") romId: Long,
        @Part screenshotFile: MultipartBody.Part
    ): Response<RomMUserScreenshot>

    @Streaming
    @GET("api/screenshots/{id}/content")
    suspend fun downloadScreenshotContent(
        @Path("id") screenshotId: Long
    ): Response<ResponseBody>

    // Device endpoints (RomM 4.7.0+)

    @POST("api/devices")
    suspend fun registerDevice(
        @Body device: RomMDeviceRegistration
    ): Response<RomMDeviceRegistrationResponse>

    @GET("api/devices")
    suspend fun getDevices(): Response<List<RomMDevice>>

    @PUT("api/devices/{id}")
    suspend fun updateDevice(
        @Path("id") deviceId: String,
        @Body device: RomMDeviceRegistration
    ): Response<RomMDevice>

    @PUT("api/devices/{id}")
    suspend fun updateDeviceCapabilities(
        @Path("id") deviceId: String,
        @Body update: RomMDeviceCapabilitiesUpdate
    ): Response<RomMDevice>

    @POST("api/devices/{id}/installs/claim")
    suspend fun claimInstallRequests(
        @Path("id") deviceId: String
    ): Response<List<RomMInstallRequest>>

    @PUT("api/devices/{id}/installs/{requestId}")
    suspend fun reportInstallRequest(
        @Path("id") deviceId: String,
        @Path("requestId") requestId: String,
        @Body report: RomMInstallReport
    ): Response<RomMInstallRequest>

    // Device-aware save endpoints (RomM 4.7.0+)

    @Multipart
    @POST("api/saves")
    suspend fun uploadSaveWithDevice(
        @Query("rom_id") romId: Long,
        @Query("emulator") emulator: String?,
        @Query("device_id") deviceId: String,
        @Query("overwrite") overwrite: Boolean = false,
        @Query("slot") slot: String? = null,
        @Query("autocleanup") autocleanup: Boolean = false,
        @Query("autocleanup_limit") autocleanupLimit: Int? = null,
        @Part saveFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMSave>

    @Multipart
    @PUT("api/saves/{id}")
    suspend fun updateSaveWithDevice(
        @Path("id") saveId: Long,
        @Query("device_id") deviceId: String,
        @Query("slot") slot: String? = null,
        @Part saveFile: MultipartBody.Part,
        @Part screenshotFile: MultipartBody.Part? = null
    ): Response<RomMSave>

    @Streaming
    @GET("api/saves/{id}/content")
    suspend fun downloadSaveContentWithDevice(
        @Path("id") saveId: Long,
        @Query("device_id") deviceId: String,
        @Query("optimistic") optimistic: Boolean = true
    ): Response<ResponseBody>

    @GET("api/saves")
    suspend fun getSavesByRomWithDevice(
        @Query("rom_id") romId: Long,
        @Query("device_id") deviceId: String
    ): Response<List<RomMSave>>

    @GET("api/saves/{id}")
    suspend fun getSaveWithDevice(
        @Path("id") saveId: Long,
        @Query("device_id") deviceId: String
    ): Response<RomMSave>

    @POST("api/saves/{id}/downloaded")
    suspend fun confirmSaveDownloaded(
        @Path("id") saveId: Long,
        @Body body: RomMDeviceIdRequest
    ): Response<RomMSave>

    @GET("api/roms/{romId}/files/{fileId}/progress")
    suspend fun getDocumentProgress(
        @Path("romId") romId: Long,
        @Path("fileId") fileId: Long
    ): Response<RomMDocumentProgress>

    @PUT("api/roms/{romId}/files/{fileId}/progress")
    suspend fun updateDocumentProgress(
        @Path("romId") romId: Long,
        @Path("fileId") fileId: Long,
        @Body body: RomMDocumentProgressUpdate
    ): Response<RomMDocumentProgress>

    @Streaming
    @GET
    suspend fun downloadRaw(
        @retrofit2.http.Url url: String
    ): Response<ResponseBody>

    @Streaming
    @GET("api/firmware/{id}/content/{fileName}")
    suspend fun downloadFirmware(
        @Path("id") firmwareId: Long,
        @Path("fileName", encoded = true) fileName: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @POST("api/play-sessions")
    suspend fun ingestPlaySessions(
        @Body body: RomMPlaySessionIngestPayload
    ): Response<RomMPlaySessionIngestResponse>

    /**
     * Sessions the server holds, scoped to the calling device unless [deviceId] names another
     * of the account's devices. Pages by [offset]; the server default limit is 50.
     */
    @GET("api/play-sessions")
    suspend fun getPlaySessions(
        @Query("rom_id") romId: Long? = null,
        @Query("device_id") deviceId: String? = null,
        @Query("start_after") startAfter: String? = null,
        @Query("end_before") endBefore: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<RomMPlaySession>>

    /**
     * Says this device is playing [RomMActivityHeartbeatPayload.romId] right now. The server holds
     * it for 90 seconds, so it has to be repeated while play continues; the reply is discarded
     * because everything it carries is already known here.
     */
    @POST("api/activity/heartbeat")
    suspend fun sendActivityHeartbeat(
        @Body body: RomMActivityHeartbeatPayload
    ): Response<ResponseBody>

    @DELETE("api/activity/heartbeat")
    suspend fun clearActivity(
        @Query("device_id") deviceId: String
    ): Response<ResponseBody>

    @POST("api/sync/negotiate")
    suspend fun negotiateSync(
        @Body body: RomMSyncNegotiatePayload
    ): Response<RomMSyncNegotiateResponse>

    @POST("api/sync/sessions/{id}/complete")
    suspend fun completeSyncSession(
        @Path("id") sessionId: Long,
        @Body body: RomMSyncCompletePayload
    ): Response<RomMSyncCompleteResponse>

    @GET("api/sync/sessions")
    suspend fun listSyncSessions(
        @Query("device_id") deviceId: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<RomMSyncSession>>

    @GET("api/sync/sessions/{id}")
    suspend fun getSyncSession(
        @Path("id") sessionId: Long
    ): Response<RomMSyncSession>

    @GET("api/streaming/config")
    suspend fun getStreamingConfig(): Response<RomMStreamingConfig>

    @GET("api/roms/{id}")
    suspend fun getStreamRomAssets(
        @Path("id") romId: Long
    ): Response<RomMStreamRomAssets>

    @GET("api/streaming/states/{romId}")
    suspend fun getStreamStatePicks(
        @Path("romId") romId: Long
    ): Response<RomMStreamStatePicks>

    @POST("api/streaming/sessions")
    suspend fun claimStreamSession(
        @Body body: RomMStreamClaimRequest
    ): Response<RomMStreamLaunching>

    @GET("api/streaming/sessions/{platform}/status")
    suspend fun getStreamSessionStatus(
        @Path("platform") platform: String,
        @Query("claimed_at") claimedAt: String?
    ): Response<RomMStreamSessionStatus>

    @POST("api/streaming/sessions/{platform}/heartbeat")
    suspend fun heartbeatStreamSession(
        @Path("platform") platform: String,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<RomMStreamSessionStatus>

    @GET("api/streaming/sessions/joinable")
    suspend fun getJoinableStreamSessions(
        @Query("rom_id") romId: Long
    ): Response<RomMStreamJoinableSessions>

    @POST("api/streaming/sessions/{platform}/join")
    suspend fun joinStreamSession(
        @Path("platform") platform: String,
        @Query("container") container: String?
    ): Response<RomMStreamJoined>

    @POST("api/streaming/sessions/{platform}/save-state")
    suspend fun saveStreamState(
        @Path("platform") platform: String,
        @Body body: RomMStreamSlotRequest,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<ResponseBody>

    @POST("api/streaming/sessions/{platform}/load-state")
    suspend fun loadStreamState(
        @Path("platform") platform: String,
        @Body body: RomMStreamSlotRequest,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<ResponseBody>

    @POST("api/streaming/sessions/{platform}/swap-disc")
    suspend fun swapStreamDisc(
        @Path("platform") platform: String,
        @Body body: RomMStreamSwapDiscRequest,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<ResponseBody>

    @POST("api/streaming/sessions/{platform}/volume")
    suspend fun setStreamVolume(
        @Path("platform") platform: String,
        @Body body: RomMStreamVolumeRequest,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<ResponseBody>

    @POST("api/streaming/sessions/{platform}/save-and-exit")
    suspend fun saveAndExitStreamSession(
        @Path("platform") platform: String,
        @Body body: RomMStreamSaveAndExitRequest,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?
    ): Response<RomMStreamSaveAndExitResponse>

    @DELETE("api/streaming/sessions/{platform}")
    suspend fun releaseStreamSession(
        @Path("platform") platform: String,
        @Query("container") container: String?,
        @Query("claimed_at") claimedAt: String?,
        @Query("save") save: Boolean
    ): Response<RomMStreamReleaseResponse>
}
