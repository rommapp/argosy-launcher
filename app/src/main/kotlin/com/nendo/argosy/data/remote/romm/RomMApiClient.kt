package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtProvider
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.ServerArt
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.FileNames
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
    internal val baseUrl: String get() = connectionManager.getBaseUrl().trimEnd('/')

    /**
     * Absolute URL for a media path RomM already prefixed. Null for an absent path, which
     * RomM reports as an empty string.
     */
    fun buildMediaUrl(path: String?): String? {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("http")) trimmed else "$baseUrl/${trimmed.trimStart('/')}"
    }

    /**
     * Absolute URL for a path relative to RomM's resources mount, the form ss_metadata
     * reports. Pre-prefixed cover paths belong in [buildMediaUrl].
     */
    fun buildResourceUrl(path: String?): String? {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http")) return trimmed
        return "$baseUrl/assets/romm/resources/${trimmed.trimStart('/')}"
    }

    /**
     * Every cover url RomM can offer for a rom, best first. Order: the covers RomM stores
     * itself, the provider url it recorded, then the ScreenScraper box scans. Empty when
     * the rom has no art at all.
     */
    fun buildCoverUrls(rom: RomMRom): List<String> = listOfNotNull(
        buildMediaUrl(rom.coverLarge),
        buildMediaUrl(rom.coverSmall),
        buildMediaUrl(rom.coverUrl),
        buildResourceUrl(rom.ssMetadata?.box2dPath),
        buildResourceUrl(rom.ssMetadata?.box2dBackPath)
    ).distinct()

    /**
     * The game's clear logo candidates in preference order: the copy RomM stored from
     * ScreenScraper, then LaunchBox's.
     */
    fun buildLogoUrls(rom: RomMRom): List<String> =
        (listOfNotNull(buildResourceUrl(rom.ssMetadata?.logoPath)) + rom.clearLogoUrls).distinct()

    fun buildBox3dUrls(rom: RomMRom): List<String> = box3dArt(rom).map { it.url }

    fun buildBoxSpineUrls(rom: RomMRom): List<String> = boxSpineArt(rom).map { it.url }

    fun buildBoxBackUrls(rom: RomMRom): List<String> = boxBackArt(rom).map { it.url }

    private fun boxSpineArt(rom: RomMRom): List<ServerArt> = listOfNotNull(
        buildResourceUrl(rom.ssMetadata?.box2dSidePath)?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) }
    )

    private fun boxBackArt(rom: RomMRom): List<ServerArt> = listOfNotNull(
        buildResourceUrl(rom.ssMetadata?.box2dBackPath)?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) }
    )

    private fun box3dArt(rom: RomMRom): List<ServerArt> = (
        listOfNotNull(
            buildResourceUrl(rom.ssMetadata?.box3dPath)?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) },
            buildResourceUrl(rom.launchboxMetadata?.box3dPath)?.let { ServerArt(it, ArtProvider.LAUNCHBOX) },
            buildResourceUrl(rom.gamelistMetadata?.box3dPath)?.let { ServerArt(it, ArtProvider.ROMM) }
        ) + rom.box3dUrls.map { ServerArt(it, ArtProvider.LAUNCHBOX) }
    ).distinctBy { it.url }

    /**
     * The game's background candidates for library sync, in preference order: the ScreenScraper
     * fanart RomM stored, then LaunchBox's "Fanart - Background" images, then screenshots.
     * ScreenScraper's own fanart url is left out, so a whole-library sync never fetches from
     * ScreenScraper under RomM's developer credentials.
     */
    fun buildBackgroundUrls(rom: RomMRom): List<String> =
        backgroundArt(rom, includeProviderUrls = false).map { it.url }

    /**
     * Every piece of art the server knows of for [slot], best first, each tagged with the
     * provider it came from. The artwork picker lists these ahead of an online search, one game
     * at a time, so ScreenScraper's fanart url stands in when RomM did not store the file.
     */
    fun serverArt(rom: RomMRom, slot: ArtSlot): List<ServerArt> {
        val tagged = when (slot) {
            ArtSlot.COVER -> listOfNotNull(
                buildMediaUrl(rom.coverLarge)?.let { ServerArt(it, ArtProvider.ROMM) },
                buildResourceUrl(rom.ssMetadata?.box2dPath)?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) }
            ) + rom.boxFrontUrls.map { ServerArt(it, ArtProvider.LAUNCHBOX) }
            ArtSlot.BACKGROUND -> backgroundArt(rom, includeProviderUrls = true)
            ArtSlot.LOGO -> listOfNotNull(
                buildResourceUrl(rom.ssMetadata?.logoPath)?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) }
            ) + rom.clearLogoUrls.map { ServerArt(it, ArtProvider.LAUNCHBOX) }
            ArtSlot.BOX_3D -> box3dArt(rom)
            ArtSlot.BOX_SPINE -> boxSpineArt(rom)
            ArtSlot.BOX_BACK -> boxBackArt(rom)
        }
        return tagged.distinctBy { it.url }
    }

    private fun backgroundArt(rom: RomMRom, includeProviderUrls: Boolean): List<ServerArt> {
        val allScreenshots = rom.screenshotUrls.ifEmpty {
            rom.screenshotPaths?.mapNotNull { buildMediaUrl(it) }.orEmpty()
        }
        val screenshots = listOfNotNull(allScreenshots.getOrNull(1)) + allScreenshots
        val ssFanart = buildResourceUrl(rom.ssMetadata?.fanartPath)
            ?: rom.ssMetadata?.fanartUrl?.takeIf { includeProviderUrls && it.startsWith("http") }
        return (
            listOfNotNull(ssFanart?.let { ServerArt(it, ArtProvider.SCREENSCRAPER) }) +
                rom.backgroundUrls.map { ServerArt(it, ArtProvider.LAUNCHBOX) } +
                screenshots.map { ServerArt(it, ArtProvider.ROMM) }
            ).distinctBy { it.url }
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
        includeFiles: Boolean = false,
        updatedAfter: java.time.Instant? = null
    ): Map<String, String> {
        return buildMap {
            platformId?.let { put("platform_ids", it.toString()) }
            updatedAfter?.let { put("updated_after", it.toString()) }
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

    fun newDownloadCall(
        romId: Long,
        fileName: String,
        rangeHeader: String? = null,
        fileIds: String? = null,
        ifRange: String? = null,
        isGameFile: Boolean = false
    ): retrofit2.Call<okhttp3.ResponseBody> {
        val currentApi = api ?: throw java.io.IOException("Not connected")
        return if (isGameFile) {
            currentApi.downloadRomFileCall(romId, fileName, rangeHeader, ifRange)
        } else {
            currentApi.downloadRomCall(romId, fileName, rangeHeader, fileIds, ifRange)
        }
    }

    fun downloadRequestKey(
        romId: Long,
        fileName: String,
        fileIds: String? = null,
        isGameFile: Boolean = false
    ): String = newDownloadCall(romId, fileName, fileIds = fileIds, isGameFile = isGameFile)
        .request().url.newBuilder().username("").password("").fragment(null).build().toString()

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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
            401, 403 -> if (isAppRefusal(response)) {
                "Authentication failed (HTTP $code)"
            } else {
                "Server refused the file (HTTP $code)"
            }
            404 -> "$kind not found on server - try resyncing"
            500, 502, 503 -> "Server error (HTTP $code)"
            else -> "Download failed (HTTP $code)"
        }
        response.errorBody()?.close()
        return RomMResult.Error(message, code)
    }

    /**
     * RomM itself answers a refused download with a JSON `detail` body. Nginx and reverse
     * proxies answer with their own HTML page, and those refusals have nothing to do with the
     * token that just served the metadata calls.
     */
    private fun isAppRefusal(response: retrofit2.Response<okhttp3.ResponseBody>): Boolean {
        val text = try { response.errorBody()?.string() } catch (_: Exception) { null } ?: return false
        return try {
            org.json.JSONObject(text).has("detail")
        } catch (_: org.json.JSONException) {
            false
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
                RomMResult.Success(platforms.size to platforms.sumOf { it.romCount })
            } else {
                RomMResult.Error("Failed to fetch library", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch library")
        }
    }

    suspend fun searchCovers(
        searchTerm: String,
        artType: RomMCoverArtType
    ): RomMResult<List<RomMCoverResource>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.searchCovers(searchTerm, artType.wireName)
            if (response.isSuccessful) {
                RomMResult.Success(response.body().orEmpty().usableResources(artType))
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

    /**
     * Every rom id the connected account can see, scoped by the server exactly as `GET /api/roms`
     * is - a hidden rom is absent from both. Unlike [getPlatformCount] this has no cached fallback:
     * the set is used as evidence that a rom is gone, and a stale or truncated set is a deletion
     * of everything it omits. Any failure returns [RomMResult.Error] so the caller withholds.
     */
    suspend fun getRomIdentifiers(): RomMResult<Set<Long>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getRomIdentifiers()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body.toSet())
            } else {
                RomMResult.Error("Failed to fetch rom identifiers", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch rom identifiers")
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
                    val storedRemoteSlug = FileNames.sanitizeSlug(remote.slug)
                    val storedFsSlug = remote.fsSlug?.let(FileNames::sanitizeSlug)
                    val existing = platformDao.getById(remote.id)
                        ?: platformDao.getBySlugAndFsSlug(storedRemoteSlug, storedFsSlug)
                        ?: platformDao.getBySlug(storedRemoteSlug)
                    val platformDef = PlatformDefinitions.getBySlug(effectiveSlug)
                    val romRules = PlatformDefinitions.definitionFor(effectiveSlug)
                    val logoUrl = buildMediaUrl(remote.logoUrl)
                    val derivedNames = PlatformDefinitions.importDisplayNames(remote.slug, remote.fsSlug, effectiveSlug)
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
                        slug = FileNames.sanitizeSlug(effectiveSlug),
                        fsSlug = storedFsSlug,
                        name = normalizedName,
                        shortName = resolvedShortName,
                        romExtensions = romRules?.extensions?.joinToString(",") ?: "",
                        gameCount = remote.romCount,
                        isVisible = existing?.isVisible ?: true,
                        logoPath = logoUrl ?: existing?.logoPath,
                        sortOrder = romRules?.sortOrder ?: existing?.sortOrder ?: 999,
                        lastScanned = existing?.lastScanned,
                        syncEnabled = existing?.syncEnabled ?: defaultSyncEnabled,
                        customRomPath = existing?.customRomPath,
                        combineContent = existing?.combineContent ?: false,
                        skipFullSync = existing?.skipFullSync ?: false
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
        completion: Int? = null,
        userStatus: String? = null,
        hidden: Boolean? = null
    ): Boolean {
        val currentApi = api ?: return false
        return try {
            val props = RomMUserPropsUpdateData(
                rating = userRating,
                difficulty = userDifficulty,
                completion = completion,
                status = userStatus,
                hidden = hidden
            )
            val response = currentApi.updateRomUserProps(rommId, props)
            response.isSuccessful
        } catch (e: Exception) {
            Logger.error(TAG, "updateRomUserProps failed: ${e.message}")
            false
        }
    }
}
