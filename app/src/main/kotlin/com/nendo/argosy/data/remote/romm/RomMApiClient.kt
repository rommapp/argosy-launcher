package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMApiClient"

@Singleton
class RomMApiClient @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val platformDao: PlatformDao
) {
    internal val api: RomMApi? get() = connectionManager.getApi()
    internal val baseUrl: String get() = connectionManager.getBaseUrl()

    fun buildMediaUrl(path: String): String {
        return if (path.startsWith("http")) path else "$baseUrl$path"
    }

    /** For paths relative to RomM's resources mount (ss_metadata media); cover paths arrive pre-prefixed. */
    fun buildResourceUrl(path: String): String {
        if (path.startsWith("http")) return path
        return "$baseUrl/assets/romm/resources/${path.trimStart('/')}"
    }

    fun isVersionAtLeast(minVersion: String): Boolean =
        connectionManager.isVersionAtLeast(minVersion)

    fun getCapabilities(): RomMCapabilities = connectionManager.getCapabilities()

    fun buildRomsQueryParams(
        platformId: Long? = null,
        searchTerm: String? = null,
        orderBy: String = "id",
        orderDir: String = "asc",
        limit: Int = 100,
        offset: Int = 0,
        includeFiles: Boolean = false
    ): Map<String, String> {
        return buildMap {
            platformId?.let {
                put("platform_ids", it.toString())
                put("platform_id", it.toString())
            }
            searchTerm?.let { put("search_term", it) }
            put("order_by", orderBy)
            put("order_dir", orderDir)
            put("limit", limit.toString())
            put("offset", offset.toString())
            put("with_char_index", "false")
            put("with_filter_values", "false")
            if (includeFiles) {
                put("with_files", "true")
            }
        }
    }

    fun buildMusicQueryParams(
        search: String? = null,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        platformId: Long? = null,
        minDuration: Double? = null,
        maxDuration: Double? = null,
        orderBy: String = "title",
        orderDir: String = "asc",
        limit: Int = 50,
        offset: Int = 0
    ): Map<String, String> {
        return buildMap {
            search?.let { put("search", it) }
            artist?.let { put("artist", it) }
            album?.let { put("album", it) }
            genre?.let { put("genre", it) }
            platformId?.let { put("platform_ids", it.toString()) }
            minDuration?.let { put("min_duration", it.toString()) }
            maxDuration?.let { put("max_duration", it.toString()) }
            put("order_by", orderBy)
            put("order_dir", orderDir)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
    }

    suspend fun getMusicTracks(params: Map<String, String>): RomMResult<RomMMusicTrackPage> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getMusicTracks(params)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music tracks", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music tracks")
        }
    }

    suspend fun getMusicFacet(
        facet: RomMMusicFacet,
        params: Map<String, String>
    ): RomMResult<RomMMusicFacetPage> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getMusicFacet(facet.path, params)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music ${facet.path}", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music ${facet.path}")
        }
    }

    suspend fun getRom(romId: Long): RomMResult<RomMRom> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getRom(romId)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch ROM", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch ROM")
        }
    }

    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        rangeHeader: String? = null,
        fileIds: String? = null
    ): RomMResult<DownloadResponse> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.downloadRom(romId, fileName, rangeHeader, fileIds)
            interpretDownloadResponse(response, "ROM")
        } catch (e: Exception) {
            RomMResult.Error(downloadErrorMessage(e))
        }
    }

    suspend fun downloadRomFile(
        fileId: Long,
        fileName: String,
        rangeHeader: String? = null
    ): RomMResult<DownloadResponse> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.downloadRomFile(fileId, fileName, rangeHeader)
            interpretDownloadResponse(response, "File")
        } catch (e: Exception) {
            RomMResult.Error(downloadErrorMessage(e))
        }
    }

    private fun downloadErrorMessage(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "Download timed out - check your connection"
        is java.net.UnknownHostException -> "Can't reach server - check your connection"
        is java.io.IOException -> "Network error during download"
        else -> e.message ?: "Download failed"
    }

    private fun interpretDownloadResponse(
        response: retrofit2.Response<okhttp3.ResponseBody>,
        kind: String
    ): RomMResult<DownloadResponse> {
        if (response.isSuccessful) {
            val body = response.body()
                ?: return RomMResult.Error("Empty response body")
            val isPartial = response.code() == 206
            return RomMResult.Success(DownloadResponse(body, isPartial))
        }
        val code = response.code()
        val message = when (code) {
            400 -> "Bad request - try resyncing (HTTP 400)"
            401, 403 -> "Authentication failed (HTTP $code)"
            404 -> "$kind not found on server - try resyncing"
            500, 502, 503 -> "Server error (HTTP $code)"
            else -> "Download failed (HTTP $code)"
        }
        return RomMResult.Error(message, code)
    }

    suspend fun getCurrentUser(): RomMResult<RomMUser> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getCurrentUser()
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch user", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch user")
        }
    }

    suspend fun getLibrarySummary(): RomMResult<Pair<Int, Int>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getPlatforms()
            if (response.isSuccessful) {
                val platforms = response.body() ?: emptyList()
                RomMResult.Success(platforms.size to platforms.sumOf { it.romCount })
            } else {
                RomMResult.Error("Failed to fetch library", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch library")
        }
    }

    suspend fun searchCovers(searchTerm: String): RomMResult<List<RomMCoverResource>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.searchCovers(searchTerm)
            if (response.isSuccessful) {
                val covers = response.body()
                    ?.flatMap { it.resources ?: emptyList() }
                    ?.filter { it.fullResUrl != null }
                    ?: emptyList()
                RomMResult.Success(covers)
            } else {
                RomMResult.Error("Cover search failed", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Cover search failed")
        }
    }

    suspend fun getPlatformCount(): RomMResult<Int> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getPlatformIdentifiers()
            if (response.isSuccessful) {
                RomMResult.Success(response.body()?.size ?: 0)
            } else {
                cachedPlatformCount() ?: RomMResult.Error("Failed to fetch platform count", response.code())
            }
        } catch (e: Exception) {
            cachedPlatformCount() ?: RomMResult.Error(e.message ?: "Failed to fetch platform count")
        }
    }

    private suspend fun cachedPlatformCount(): RomMResult<Int>? {
        val cached = platformDao.getTotalPlatformCount()
        return if (cached > 0) RomMResult.Success(cached) else null
    }

    suspend fun fetchAndStorePlatforms(
        defaultSyncEnabled: Boolean = true
    ): RomMResult<List<PlatformEntity>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getPlatforms()
            if (response.isSuccessful) {
                val platforms = response.body() ?: emptyList()
                val entities = platforms.map { remote ->
                    val effectiveSlug = PlatformDefinitions.resolveImportSlug(remote.slug, remote.displayName ?: remote.name, remote.fsSlug)
                    val isSubPlatform = !effectiveSlug.equals(remote.slug, ignoreCase = true)
                    val existing = platformDao.getById(remote.id)
                        ?: platformDao.getBySlugAndFsSlug(remote.slug, remote.fsSlug)
                        ?: platformDao.getBySlug(remote.slug)
                    val platformDef = PlatformDefinitions.getBySlug(effectiveSlug)
                    val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
                    val derivedNames = if (isSubPlatform) {
                        PlatformDefinitions.getAliasDisplayName(effectiveSlug)
                            ?: PlatformDefinitions.deriveDisplayName(effectiveSlug)
                    } else {
                        PlatformDefinitions.getAliasDisplayName(remote.slug)
                            ?: PlatformDefinitions.deriveDisplayName(remote.slug)
                            ?: PlatformDefinitions.deriveDisplayName(remote.fsSlug)
                    }
                    val normalizedName = if (isSubPlatform) {
                        remote.customName?.takeIf { it.isNotBlank() }
                            ?: derivedNames?.first ?: platformDef?.name ?: remote.name
                    } else {
                        remote.customName?.takeIf { it.isNotBlank() }
                            ?: remote.displayName ?: derivedNames?.first ?: remote.name
                    }
                    val resolvedShortName = derivedNames?.second ?: platformDef?.shortName ?: normalizedName
                    PlatformEntity(
                        id = remote.id,
                        slug = effectiveSlug,
                        fsSlug = remote.fsSlug,
                        name = normalizedName,
                        shortName = resolvedShortName,
                        romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
                        gameCount = remote.romCount,
                        isVisible = existing?.isVisible ?: true,
                        logoPath = logoUrl ?: existing?.logoPath,
                        sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 999,
                        lastScanned = existing?.lastScanned,
                        syncEnabled = existing?.syncEnabled ?: defaultSyncEnabled,
                        customRomPath = existing?.customRomPath
                    )
                }
                entities.forEach { entity ->
                    if (platformDao.getById(entity.id) == null) {
                        platformDao.insert(entity)
                    } else {
                        platformDao.update(entity)
                    }
                }
                RomMResult.Success(entities.sortedBy { it.sortOrder })
            } else {
                RomMResult.Error("Failed to fetch platforms", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch platforms")
        }
    }

    suspend fun updateRomUserProps(
        rommId: Long,
        userRating: Int? = null,
        userDifficulty: Int? = null,
        userStatus: String? = null
    ): Boolean {
        val currentApi = api ?: return false
        return try {
            val props = RomMUserPropsUpdate(
                data = RomMUserPropsUpdateData(
                    rating = userRating,
                    difficulty = userDifficulty,
                    status = userStatus
                )
            )
            val response = currentApi.updateRomUserProps(rommId, props)
            response.isSuccessful
        } catch (e: Exception) {
            Logger.error(TAG, "updateRomUserProps failed: ${e.message}")
            false
        }
    }
}
