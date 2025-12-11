package com.nendo.argosy.data.remote.romm

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
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
import retrofit2.http.Streaming

interface RomMApi {

    @GET("api/heartbeat")
    suspend fun heartbeat(): Response<RomMHeartbeatResponse>

    @FormUrlEncoded
    @POST("api/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("scope") scope: String = "me.read me.write platforms.read roms.read assets.read assets.write roms.user.read roms.user.write"
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
        @Query("platform_id") platformId: Long? = null,
        @Query("search_term") searchTerm: String? = null,
        @Query("order_by") orderBy: String = "name",
        @Query("order_dir") orderDir: String = "asc",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
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
        @Part("rom_id") romId: RequestBody,
        @Part("emulator") emulator: RequestBody?,
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @Multipart
    @PUT("api/saves/{id}")
    suspend fun updateSave(
        @Path("id") saveId: Long,
        @Part saveFile: MultipartBody.Part
    ): Response<RomMSave>

    @Streaming
    @GET("api/saves/{id}/content/{fileName}")
    suspend fun downloadSaveContent(
        @Path("id") saveId: Long,
        @Path("fileName", encoded = true) fileName: String
    ): Response<ResponseBody>
}
