package com.nendo.argosy.data.remote.github

import android.util.Log
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val patch: Int,
    val prerelease: String? = null
) : Comparable<VersionInfo> {
    override fun compareTo(other: VersionInfo): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> comparePrereleases(prerelease, other.prerelease)
        }
    }

    private fun comparePrereleases(a: String, b: String): Int {
        val partsA = a.split(".")
        val partsB = b.split(".")
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val partA = partsA.getOrNull(i)
            val partB = partsB.getOrNull(i)
            when {
                partA == null -> return -1
                partB == null -> return 1
                else -> {
                    val numA = partA.toIntOrNull()
                    val numB = partB.toIntOrNull()
                    val cmp = when {
                        numA != null && numB != null -> numA.compareTo(numB)
                        numA != null -> -1
                        numB != null -> 1
                        else -> partA.compareTo(partB)
                    }
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }

    companion object {
        fun parse(version: String): VersionInfo? {
            val cleaned = version.removePrefix("v").trim()
            val (versionPart, prereleasePart) = if (cleaned.contains("-")) {
                cleaned.substringBefore("-") to cleaned.substringAfter("-")
            } else {
                cleaned to null
            }
            val parts = versionPart.split(".")
            if (parts.size < 2) return null
            return try {
                VersionInfo(
                    major = parts[0].toInt(),
                    minor = parts[1].toInt(),
                    patch = parts.getOrNull(2)?.toInt() ?: 0,
                    prerelease = prereleasePart
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

@Singleton
class UpdateRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

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
        val betaEnabled = userPreferencesRepository.userPreferences.first().betaUpdatesEnabled
        Log.d(TAG, "Checking for updates, current version: $currentVersion, beta enabled: $betaEnabled")

        return try {
            val response = api.getReleases()

            if (!response.isSuccessful) {
                val error = UpdateState.Error("GitHub API returned ${response.code()}")
                _updateState.value = error
                return error
            }

            val releases = response.body()
            if (releases.isNullOrEmpty()) {
                val error = UpdateState.Error("No releases found")
                _updateState.value = error
                return error
            }

            val candidates = releases.filter { release ->
                !release.draft && (betaEnabled || !release.prerelease)
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "No suitable releases found")
                _updateState.value = UpdateState.UpToDate
                return UpdateState.UpToDate
            }

            val currentVersionInfo = VersionInfo.parse(currentVersion)
            if (currentVersionInfo == null) {
                Log.e(TAG, "Failed to parse current version: $currentVersion")
                val error = UpdateState.Error("Invalid current version format")
                _updateState.value = error
                return error
            }

            val latestCandidate = candidates
                .mapNotNull { release ->
                    VersionInfo.parse(release.tagName)?.let { version -> release to version }
                }
                .maxByOrNull { it.second }

            if (latestCandidate == null) {
                Log.e(TAG, "Failed to parse any release versions")
                val error = UpdateState.Error("Invalid version format in releases")
                _updateState.value = error
                return error
            }

            val (release, latestVersion) = latestCandidate
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
