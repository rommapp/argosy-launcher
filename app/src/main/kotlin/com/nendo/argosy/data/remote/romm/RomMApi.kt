package com.nendo.argosy.data.remote.romm

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
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

    @POST("api/users/{id}/ra/refresh")
    suspend fun refreshRAProgression(
        @Path("id") userId: Long,
        @Body body: RomMRARefreshRequest = RomMRARefreshRequest()
    ): Response<Unit>

    @GET("api/search/cover")
    suspend fun searchCovers(
        @Query("search_term") searchTerm: String
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

    @GET("api/music/tracks")
    suspend fun getMusicTracks(
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicTrackPage>

    @GET("api/music/{facet}")
    suspend fun getMusicFacet(
        @Path("facet") facet: String,
        @QueryMap params: Map<String, String>
    ): Response<RomMMusicFacetPage>

    @PUT("api/roms/{id}/props")
    suspend fun updateRomUserProps(
        @Path("id") romId: Long,
        @Body props: RomMUserPropsUpdate
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

    @Streaming
    @GET
    suspend fun downloadRaw(
        @retrofit2.http.Url url: String
    ): Response<ResponseBody>

    @Streaming
    @GET("api/firmware/{id}/content/{fileName}")
    suspend fun downloadFirmware(
        @Path("id") firmwareId: Long,
        @Path("fileName", encoded = true) fileName: String
    ): Response<ResponseBody>

    @POST("api/play-sessions")
    suspend fun ingestPlaySessions(
        @Body body: RomMPlaySessionIngestPayload
    ): Response<RomMPlaySessionIngestResponse>

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
}
