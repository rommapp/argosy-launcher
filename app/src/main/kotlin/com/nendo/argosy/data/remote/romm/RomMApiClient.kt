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

    fun isVersionAtLeast(minVersion: String): Boolean =
        connectionManager.isVersionAtLeast(minVersion)

    fun buildRomsQueryParams(
        platformId: Long? = null,
        searchTerm: String? = null,
        orderBy: String = "name",
        orderDir: String = "asc",
        limit: Int = 100,
        offset: Int = 0
    ): Map<String, String> {
        val usePluralizedParams = isVersionAtLeast("4.6.0")
        val platformKey = if (usePluralizedParams) "platform_ids" else "platform_id"

        return buildMap {
            platformId?.let { put(platformKey, it.toString()) }
            searchTerm?.let { put("search_term", it) }
            put("order_by", orderBy)
            put("order_dir", orderDir)
            put("limit", limit.toString())
            put("offset", offset.toString())
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
        rangeHeader: String? = null
    ): RomMResult<DownloadResponse> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.downloadRom(romId, fileName, rangeHeader)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val isPartial = response.code() == 206
                    RomMResult.Success(DownloadResponse(body, isPartial))
                } else {
                    RomMResult.Error("Empty response body")
                }
            } else {
                val code = response.code()
                val message = when (code) {
                    400 -> "Bad request - try resyncing (HTTP 400)"
                    401, 403 -> "Authentication failed (HTTP $code)"
                    404 -> "ROM not found on server - try resyncing"
                    500, 502, 503 -> "Server error (HTTP $code)"
                    else -> "Download failed (HTTP $code)"
                }
                RomMResult.Error(message, code)
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Download failed")
        }
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
                val platformCount = platforms.size
                val totalRoms = platforms.sumOf { it.romCount }
                RomMResult.Success(platformCount to totalRoms)
            } else {
                RomMResult.Error("Failed to fetch library", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch library")
        }
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
                    val existing = platformDao.getById(remote.id)
                        ?: platformDao.getBySlugAndFsSlug(remote.slug, remote.fsSlug)
                        ?: platformDao.getBySlug(remote.slug)
                    val platformDef = PlatformDefinitions.getBySlug(remote.slug)
                    val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
                    val normalizedName = remote.displayName ?: remote.name
                    PlatformEntity(
                        id = remote.id,
                        slug = remote.slug,
                        fsSlug = remote.fsSlug,
                        name = normalizedName,
                        shortName = platformDef?.shortName ?: normalizedName,
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
                entities.forEach { platformDao.insert(it) }
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
