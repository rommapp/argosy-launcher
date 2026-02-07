package com.nendo.argosy.data.remote.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("repos/nendotools/argosy-launcher/releases/latest")
    suspend fun getLatestRelease(): Response<GitHubRelease>

    @GET("repos/nendotools/argosy-launcher/releases")
    suspend fun getReleases(
        @Query("per_page") perPage: Int = 10
    ): Response<List<GitHubRelease>>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getRepoLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getRepoReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): Response<List<GitHubRelease>>
}

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String,
    @Json(name = "body") val body: String?,
    @Json(name = "prerelease") val prerelease: Boolean,
    @Json(name = "draft") val draft: Boolean,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "assets") val assets: List<GitHubAsset>
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name") val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    @Json(name = "size") val size: Long,
    @Json(name = "content_type") val contentType: String
)
