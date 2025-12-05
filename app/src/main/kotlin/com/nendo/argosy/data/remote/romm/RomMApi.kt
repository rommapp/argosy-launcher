package com.nendo.argosy.data.remote.romm

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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
        @Field("scope") scope: String = "me.read platforms.read roms.read assets.read roms.user.read"
    ): Response<RomMTokenResponse>

    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<RomMUser>

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
        @Path("fileName", encoded = true) fileName: String
    ): Response<ResponseBody>
}
