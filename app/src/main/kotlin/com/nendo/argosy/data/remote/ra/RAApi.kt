package com.nendo.argosy.data.remote.ra

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RAApi {

    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun login(
        @Field("r") request: String = "login2",
        @Field("u") username: String,
        @Field("p") password: String
    ): Response<RALoginResponse>

    @GET("dorequest.php")
    suspend fun awardAchievement(
        @Query("r") request: String = "awardachievement",
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("a") achievementId: Long,
        @Query("h") hardcore: Int,
        @Query("v") validation: String
    ): Response<RAAwardResponse>

    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun startSession(
        @Field("r") request: String = "startsession",
        @Field("u") username: String,
        @Field("t") token: String,
        @Field("g") gameId: Long,
        @Field("l") gameHash: String? = null,
        @Field("h") hardcore: Int = 0
    ): Response<RAStartSessionResponse>

    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun ping(
        @Field("r") request: String = "ping",
        @Field("u") username: String,
        @Field("t") token: String,
        @Field("g") gameId: Long,
        @Field("m") richPresence: String? = null
    ): Response<RABaseResponse>

    @GET("dorequest.php")
    suspend fun getGameInfo(
        @Query("r") request: String = "patch",
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("g") gameId: Long,
        @Query("f") flags: Int = 3
    ): Response<RAGameInfoResponse>

    companion object {
        const val BASE_URL = "https://retroachievements.org/"
    }
}
