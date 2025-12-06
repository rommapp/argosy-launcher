package com.nendo.argosy.data.remote.github

import android.util.Log
import com.nendo.argosy.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(val release: GitHubRelease, val apkAsset: GitHubAsset) : UpdateState()
    data object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

data class VersionInfo(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<VersionInfo> {
    override fun compareTo(other: VersionInfo): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        fun parse(version: String): VersionInfo? {
            val cleaned = version.removePrefix("v").trim()
            val parts = cleaned.split(".")
            if (parts.size < 2) return null
            return try {
                VersionInfo(
                    major = parts[0].toInt(),
                    minor = parts[1].toInt(),
                    patch = parts.getOrNull(2)?.toInt() ?: 0
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

@Singleton
class UpdateRepository @Inject constructor() {

    companion object {
        private const val TAG = "UpdateRepository"
        private const val GITHUB_API_BASE = "https://api.github.com/"
    }

    private val api: GitHubApi by lazy { createApi() }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

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

    suspend fun checkForUpdates(): UpdateState {
        _updateState.value = UpdateState.Checking
        Log.d(TAG, "Checking for updates, current version: $currentVersion")

        return try {
            val response = api.getLatestRelease()

            if (!response.isSuccessful) {
                val error = UpdateState.Error("GitHub API returned ${response.code()}")
                _updateState.value = error
                return error
            }

            val release = response.body()
            if (release == null) {
                val error = UpdateState.Error("Empty response from GitHub")
                _updateState.value = error
                return error
            }

            if (release.prerelease || release.draft) {
                Log.d(TAG, "Latest release is prerelease/draft, skipping")
                _updateState.value = UpdateState.UpToDate
                return UpdateState.UpToDate
            }

            val latestVersion = VersionInfo.parse(release.tagName)
            val currentVersionInfo = VersionInfo.parse(currentVersion)

            if (latestVersion == null || currentVersionInfo == null) {
                Log.e(TAG, "Failed to parse versions: latest=${release.tagName}, current=$currentVersion")
                val error = UpdateState.Error("Invalid version format")
                _updateState.value = error
                return error
            }

            Log.d(TAG, "Version comparison: current=$currentVersionInfo, latest=$latestVersion")

            if (latestVersion > currentVersionInfo) {
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset == null) {
                    val error = UpdateState.Error("No APK found in release")
                    _updateState.value = error
                    return error
                }

                Log.d(TAG, "Update available: ${release.tagName}")
                val state = UpdateState.UpdateAvailable(release, apkAsset)
                _updateState.value = state
                return state
            }

            Log.d(TAG, "Already up to date")
            _updateState.value = UpdateState.UpToDate
            UpdateState.UpToDate
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            val error = UpdateState.Error(e.message ?: "Unknown error")
            _updateState.value = error
            error
        }
    }

    fun clearState() {
        _updateState.value = UpdateState.Idle
    }
}
