package com.nendo.argosy.data.remote.ra

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Read-only RetroAchievements web API, authenticated by the user's web API key in `y`. None of
 * these calls registers a play session.
 */
interface RAWebApi {

    @GET("API_GetUserSummary.php")
    suspend fun getUserSummary(
        @Query("y") apiKey: String,
        @Query("u") username: String,
        @Query("g") recentGames: Int = 0,
        @Query("a") recentAchievements: Int = 0
    ): Response<RAWebUserSummary>

    @GET("API_GetGameInfoAndUserProgress.php")
    suspend fun getGameInfoAndUserProgress(
        @Query("y") apiKey: String,
        @Query("u") username: String,
        @Query("g") gameId: Long,
        @Query("a") includeAwardMetadata: Int = 1
    ): Response<RAWebGameProgress>

    @GET("API_GetUserCompletionProgress.php")
    suspend fun getUserCompletionProgress(
        @Query("y") apiKey: String,
        @Query("u") username: String,
        @Query("c") count: Int,
        @Query("o") offset: Int
    ): Response<RAWebCompletionPage>

    @GET("API_GetAchievementsEarnedBetween.php")
    suspend fun getAchievementsEarnedBetween(
        @Query("y") apiKey: String,
        @Query("u") username: String,
        @Query("f") fromEpochSeconds: Long,
        @Query("t") toEpochSeconds: Long
    ): Response<List<RAWebEarnedAchievement>>

    @GET("API_GetComments.php")
    suspend fun getComments(
        @Query("y") apiKey: String,
        @Query("i") targetId: Long,
        @Query("t") kind: Int,
        @Query("c") count: Int,
        @Query("sort") sort: String
    ): Response<RAWebCommentsPage>

    companion object {
        const val BASE_URL = "https://retroachievements.org/API/"
        const val COMMENT_KIND_ACHIEVEMENT = 2
        const val COMMENT_SORT_NEWEST_FIRST = "-submitted"
    }
}
