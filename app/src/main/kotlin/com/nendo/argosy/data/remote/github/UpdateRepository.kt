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

data class ReleasePage(val releases: List<GitHubRelease>, val hasMore: Boolean)

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
            prerelease == null -> if (isRealPrerelease(other.prerelease)) 1 else 0
            other.prerelease == null -> if (isRealPrerelease(prerelease)) -1 else 0
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
        private val PRERELEASE_QUALIFIERS = setOf(
            "alpha", "beta", "rc", "pre", "preview", "dev", "nightly", "snapshot", "eap", "canary"
        )

        private fun isRealPrerelease(prerelease: String?): Boolean {
            if (prerelease == null) return false
            val token = prerelease.lowercase().takeWhile { it.isLetter() }
            return token in PRERELEASE_QUALIFIERS
        }

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
        private const val RELEASES_PER_PAGE = 10

        /**
         * Release asset suffix per version code prefix, as `app/build.gradle.kts` assigns them.
         * Assets are matched on the whole `-<suffix>.apk` ending rather than by substring, so an
         * x86 build cannot take the x86_64 asset whose name contains it. A prefix that is absent
         * here, the universal build's included, takes the asset carrying no suffix at all.
         */
        private val ABI_SUFFIX_BY_VERSION_CODE_PREFIX = mapOf(
            1 to "arm32",
            2 to "arm64",
            4 to "x86",
            5 to "x86_64"
        )

        /**
         * The release asset matching this build's abi, else the one carrying no abi suffix, else
         * whatever apk the release has. Taking a suffixed asset as the universal fallback would
         * install a foreign abi, so the fallback excludes every suffix rather than a listed few.
         */
        fun selectApkAsset(assets: List<GitHubAsset>, versionCode: Int): GitHubAsset? {
            val apks = assets.filter { it.name.endsWith(".apk") }
            val suffix = ABI_SUFFIX_BY_VERSION_CODE_PREFIX[versionCode / 1_000_000]
            val exact = suffix?.let { s -> apks.find { it.name.endsWith("-$s.apk") } }
            return exact
                ?: apks.find { asset ->
                    ABI_SUFFIX_BY_VERSION_CODE_PREFIX.values.none { asset.name.endsWith("-$it.apk") }
                }
                ?: apks.firstOrNull()
        }

        /**
         * The abi a release asset was built for, read from its `-<suffix>.apk` ending, or
         * [UNIVERSAL_ABI] for an asset carrying no known suffix.
         */
        fun abiLabelOf(assetName: String): String =
            ABI_SUFFIX_BY_VERSION_CODE_PREFIX.values
                .firstOrNull { assetName.endsWith("-$it.apk") }
                ?: UNIVERSAL_ABI

        const val UNIVERSAL_ABI = "universal"
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

    /**
     * Finds the newest release allowed by [betaEnabled]. With [newerOnly] false that release is
     * returned as [UpdateState.UpdateAvailable] even when it is not newer than this build.
     */
    suspend fun checkForUpdates(betaEnabled: Boolean, newerOnly: Boolean = true): UpdateState {
        _updateState.value = UpdateState.Checking
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

            if (!newerOnly || latestVersion > currentVersionInfo) {
                val apkAsset = selectApkAsset(release.assets, BuildConfig.VERSION_CODE)
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

    suspend fun listReleases(page: Int): Result<ReleasePage> {
        return try {
            val betaEnabled = userPreferencesRepository.userPreferences.first().betaUpdatesEnabled
            val response = api.getReleases(perPage = RELEASES_PER_PAGE, page = page)
            if (!response.isSuccessful) {
                return Result.failure(Exception("GitHub API returned ${response.code()}"))
            }
            val releases = response.body().orEmpty()
            Result.success(
                ReleasePage(
                    releases = releases.filter { !it.draft && (betaEnabled || !it.prerelease) },
                    hasMore = releases.size == RELEASES_PER_PAGE
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list releases (page $page)", e)
            Result.failure(e)
        }
    }

    fun clearState() {
        _updateState.value = UpdateState.Idle
    }
}
