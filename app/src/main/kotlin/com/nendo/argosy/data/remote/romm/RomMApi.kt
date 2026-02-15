package com.nendo.argosy.data.remote.romm

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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

    @FormUrlEncoded
    @POST("api/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("scope") scope: String = "me.read me.write platforms.read roms.read assets.read assets.write roms.user.read roms.user.write collections.read collections.write"
    ): Response<RomMTokenResponse>

    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<RomMUser>

    @POST("api/users/{id}/ra/refresh")
    suspend fun refreshRAProgression(
        @Path("id") userId: Long,
        @Body body: RomMRARefreshRequest = RomMRARefreshRequest()
    ): Response<Unit>

    @GET("api/platforms")
    suspend fun getPlatforms(): Response<List<RomMPlatform>>

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
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @PUT("api/roms/{id}/props")
    suspend fun updateRomUserProps(
        @Path("id") romId: Long,
        @Body props: RomMUserPropsUpdate
    ): Response<Unit>

    @GET("api/collections")
    suspend fun getCollections(
        @Query("is_favorite") isFavorite: Boolean? = null
    ): Response<List<RomMCollection>>

    @POST("api/collections")
    suspend fun createCollection(
        @Query("is_favorite") isFavorite: Boolean = false,
        @Body collection: RomMCollectionCreate
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
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @Multipart
    @PUT("api/saves/{id}")
    suspend fun updateSave(
        @Path("id") saveId: Long,
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @DELETE("api/saves/{id}")
    suspend fun deleteSave(
        @Path("id") saveId: Long
    ): Response<Unit>

    @Streaming
    @GET("api/saves/{id}/content/{fileName}")
    suspend fun downloadSaveContent(
        @Path("id") saveId: Long,
        @Path("fileName", encoded = true) fileName: String
    ): Response<ResponseBody>

    // Device endpoints (RomM 4.7.0+)

    @POST("api/devices")
    suspend fun registerDevice(
        @Body device: RomMDeviceRegistration
    ): Response<RomMDevice>

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
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @Multipart
    @PUT("api/saves/{id}")
    suspend fun updateSaveWithDevice(
        @Path("id") saveId: Long,
        @Query("device_id") deviceId: String,
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @Streaming
    @GET("api/saves/{id}/content/{fileName}")
    suspend fun downloadSaveContentWithDevice(
        @Path("id") saveId: Long,
        @Path("fileName", encoded = true) fileName: String,
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
}
