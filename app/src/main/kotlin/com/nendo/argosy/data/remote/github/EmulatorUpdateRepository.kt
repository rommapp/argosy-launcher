package com.nendo.argosy.data.remote.github

import android.util.Log
import com.nendo.argosy.data.emulator.ApkAssetMatcher
import com.nendo.argosy.data.emulator.ApkMatchResult
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.local.dao.EmulatorUpdateDao
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class EmulatorUpdateCheckResult {
    data class UpdateAvailable(
        val emulatorId: String,
        val currentVersion: String?,
        val latestVersion: String,
        val release: GitHubRelease,
        val matchResult: ApkMatchResult
    ) : EmulatorUpdateCheckResult()

    data class UpToDate(
        val emulatorId: String,
        val version: String
    ) : EmulatorUpdateCheckResult()

    data class Error(
        val emulatorId: String,
        val message: String
    ) : EmulatorUpdateCheckResult()
}

@Singleton
class EmulatorUpdateRepository @Inject constructor(
    private val emulatorUpdateDao: EmulatorUpdateDao
) {
    companion object {
        private const val TAG = "EmulatorUpdateRepo"
        private const val GITHUB_API_BASE = "https://api.github.com/"
    }

    private val gitHubApi: GitHubApi by lazy { createApi() }

    private fun createApi(): GitHubApi {
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApi::class.java)
    }

    suspend fun checkForUpdate(
        emulator: EmulatorDef,
        installedVersion: String?,
        storedVariant: String? = null
    ): EmulatorUpdateCheckResult {
        val githubRepo = emulator.githubRepo ?: return EmulatorUpdateCheckResult.Error(
            emulator.id,
            "No GitHub repo configured"
        )

        val (owner, repo) = githubRepo.split("/").let {
            if (it.size != 2) return EmulatorUpdateCheckResult.Error(
                emulator.id,
                "Invalid repo format: $githubRepo"
            )
            it[0] to it[1]
        }

        return try {
            val response = gitHubApi.getRepoLatestRelease(owner, repo)

            if (!response.isSuccessful) {
                Log.w(TAG, "API error for ${emulator.id}: ${response.code()}")
                return EmulatorUpdateCheckResult.Error(
                    emulator.id,
                    "GitHub API returned ${response.code()}"
                )
            }

            val release = response.body() ?: return EmulatorUpdateCheckResult.Error(
                emulator.id,
                "Empty response"
            )

            val latestVersion = VersionInfo.parse(release.tagName)
            val currentVersionInfo = installedVersion?.let { VersionInfo.parse(it) }

            val matchResult = ApkAssetMatcher.matchApk(
                assets = release.assets,
                storedVariant = storedVariant
            )

            if (matchResult is ApkMatchResult.NoMatch) {
                return EmulatorUpdateCheckResult.Error(
                    emulator.id,
                    "No APK found in release"
                )
            }

            val hasUpdate = when {
                currentVersionInfo == null -> true
                latestVersion == null -> false
                else -> latestVersion > currentVersionInfo
            }

            if (hasUpdate) {
                Log.d(TAG, "Update available for ${emulator.id}: $installedVersion -> ${release.tagName}")

                when (matchResult) {
                    is ApkMatchResult.SingleMatch -> {
                        emulatorUpdateDao.upsert(
                            EmulatorUpdateEntity(
                                emulatorId = emulator.id,
                                latestVersion = release.tagName,
                                installedVersion = installedVersion,
                                downloadUrl = matchResult.asset.downloadUrl,
                                assetName = matchResult.asset.name,
                                assetSize = matchResult.asset.size,
                                checkedAt = Instant.now(),
                                installedVariant = storedVariant ?: matchResult.variant,
                                hasUpdate = true
                            )
                        )
                    }
                    is ApkMatchResult.MultipleMatches -> {
                        val firstAsset = matchResult.assets.first()
                        emulatorUpdateDao.upsert(
                            EmulatorUpdateEntity(
                                emulatorId = emulator.id,
                                latestVersion = release.tagName,
                                installedVersion = installedVersion,
                                downloadUrl = firstAsset.downloadUrl,
                                assetName = firstAsset.name,
                                assetSize = firstAsset.size,
                                checkedAt = Instant.now(),
                                installedVariant = null,
                                hasUpdate = true
                            )
                        )
                    }
                    ApkMatchResult.NoMatch -> {}
                }

                EmulatorUpdateCheckResult.UpdateAvailable(
                    emulatorId = emulator.id,
                    currentVersion = installedVersion,
                    latestVersion = release.tagName,
                    release = release,
                    matchResult = matchResult
                )
            } else {
                Log.d(TAG, "Up to date: ${emulator.id} (${release.tagName})")

                emulatorUpdateDao.upsert(
                    EmulatorUpdateEntity(
                        emulatorId = emulator.id,
                        latestVersion = release.tagName,
                        installedVersion = installedVersion,
                        downloadUrl = when (matchResult) {
                            is ApkMatchResult.SingleMatch -> matchResult.asset.downloadUrl
                            is ApkMatchResult.MultipleMatches -> matchResult.assets.first().downloadUrl
                            ApkMatchResult.NoMatch -> ""
                        },
                        assetName = when (matchResult) {
                            is ApkMatchResult.SingleMatch -> matchResult.asset.name
                            is ApkMatchResult.MultipleMatches -> matchResult.assets.first().name
                            ApkMatchResult.NoMatch -> ""
                        },
                        assetSize = when (matchResult) {
                            is ApkMatchResult.SingleMatch -> matchResult.asset.size
                            is ApkMatchResult.MultipleMatches -> matchResult.assets.first().size
                            ApkMatchResult.NoMatch -> 0
                        },
                        checkedAt = Instant.now(),
                        installedVariant = storedVariant,
                        hasUpdate = false
                    )
                )

                EmulatorUpdateCheckResult.UpToDate(
                    emulatorId = emulator.id,
                    version = release.tagName
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check failed for ${emulator.id}", e)
            EmulatorUpdateCheckResult.Error(
                emulator.id,
                e.message ?: "Unknown error"
            )
        }
    }

    suspend fun getAvailableUpdates(): List<EmulatorUpdateEntity> {
        return emulatorUpdateDao.getAvailableUpdates()
    }

    suspend fun getCachedUpdate(emulatorId: String): EmulatorUpdateEntity? {
        return emulatorUpdateDao.getByEmulatorId(emulatorId)
    }

    suspend fun markAsInstalled(emulatorId: String, version: String, variant: String?) {
        emulatorUpdateDao.markAsInstalled(emulatorId, version)
        if (variant != null) {
            emulatorUpdateDao.updateInstalledVariant(emulatorId, variant)
        }
    }

    suspend fun clearVariant(emulatorId: String) {
        emulatorUpdateDao.clearInstalledVariant(emulatorId)
    }
}
