package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMFirmware(
    @Json(name = "id") val id: Long,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_path") val filePath: String,
    @Json(name = "full_path") val fullPath: String,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long,
    @Json(name = "md5_hash") val md5Hash: String?,
    @Json(name = "sha1_hash") val sha1Hash: String?,
    @Json(name = "missing_from_fs") val missingFromFs: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMPlatform(
    @Json(name = "id") val id: Long,
    @Json(name = "slug") val slug: String,
    @Json(name = "name") val name: String,
    @Json(name = "fs_slug") val fsSlug: String?,
    @Json(name = "rom_count") val romCount: Int,
    @Json(name = "custom_name") val customName: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "url_logo") val logoUrl: String? = null,
    @Json(name = "firmware") val firmware: List<RomMFirmware>? = null
)

@JsonClass(generateAdapter = true)
data class RomMRom(
    @Json(name = "id") val id: Long,
    @Json(name = "platform_id") val platformId: Long,
    @Json(name = "platform_slug") val platformSlug: String,
    @Json(name = "platform_display_name") val platformName: String? = null,

    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String?,
    @Json(name = "fs_name") val fileName: String? = null,
    @Json(name = "fs_size_bytes") val fileSize: Long = 0,
    @Json(name = "full_path") val filePath: String?,

    @Json(name = "igdb_id") val igdbId: Long?,
    @Json(name = "moby_id") val mobyId: Long?,
    @Json(name = "ra_id") val raId: Long? = null,
    @Json(name = "sgdb_id") val sgdbId: Long? = null,
    @Json(name = "ss_id") val ssId: Long? = null,
    @Json(name = "launchbox_id") val launchboxId: Long? = null,
    @Json(name = "hasheous_id") val hasheousId: Long? = null,
    @Json(name = "tgdb_id") val tgdbId: Long? = null,
    @Json(name = "hltb_id") val hltbId: Long? = null,
    @Json(name = "flashpoint_id") val flashpointId: String? = null,
    @Json(name = "gamelist_id") val gamelistId: String? = null,
    @Json(name = "libretro_id") val libretroId: String? = null,

    @Json(name = "crc_hash") val crcHash: String? = null,
    @Json(name = "md5_hash") val md5Hash: String? = null,
    @Json(name = "sha1_hash") val sha1Hash: String? = null,
    @Json(name = "ra_hash") val raHash: String? = null,

    @Json(name = "summary") val summary: String?,
    @Json(name = "metadatum") val metadatum: RomMMetadatum? = null,
    @Json(name = "launchbox_metadata") val launchboxMetadata: RomMLaunchboxMetadata? = null,
    @Json(name = "ss_metadata") val ssMetadata: RomMSsMetadata? = null,
    @Json(name = "merged_ra_metadata") val raMetadata: RomMRAMetadata? = null,

    @Json(name = "path_cover_small") val coverSmall: String?,
    @Json(name = "path_cover_large") val coverLarge: String?,
    @Json(name = "url_cover") val coverUrl: String? = null,

    @Json(name = "regions") val regions: List<String>?,
    @Json(name = "languages") val languages: List<String>?,
    @Json(name = "revision") val revision: String?,

    @Json(name = "merged_screenshots") val screenshotPaths: List<String>? = null,
    @Json(name = "user_screenshots") val userScreenshots: List<RomMUserScreenshot>? = null,
    @Json(name = "rom_user") val romUser: RomMRomUser? = null,

    @Json(name = "tags") val tags: List<String>? = null,
    @Json(name = "siblings") val siblings: List<RomMSibling>? = null,
    @Json(name = "sibling_roms") val siblingRoms: List<RomMSibling>? = null,
    @Json(name = "multi") val multi: Boolean = false,
    @Json(name = "has_multiple_files") val hasMultipleFiles: Boolean = false,
    @Json(name = "has_simple_single_file") val hasSimpleSingleFile: Boolean = true,
    @Json(name = "has_nested_single_file") val hasNestedSingleFile: Boolean = false,
    @Json(name = "files") val files: List<RomMRomFile>? = null,
    @Json(name = "youtube_video_id") val youtubeVideoId: String? = null,

    @Json(name = "alternative_names") val alternativeNames: List<String>? = null,
    @Json(name = "has_manual") val hasManual: Boolean = false,
    @Json(name = "path_manual") val manualPath: String? = null,
    @Json(name = "has_soundtrack") val hasSoundtrack: Boolean = false,
    @Json(name = "is_identified") val isIdentified: Boolean = true
) {
    val effectiveSiblings: List<RomMSibling> get() = siblingRoms ?: siblings ?: emptyList()
    val genres: List<String>? get() = metadatum?.genres
    val companies: List<String>? get() = metadatum?.companies
    val firstReleaseDateMillis: Long? get() = metadatum?.firstReleaseDate

    val backgroundUrls: List<String>
        get() = launchboxMetadata?.images
            ?.filter { it.type.contains("Fanart - Background", ignoreCase = true) }
            ?.map { it.url }
            ?: emptyList()

    val screenshotUrls: List<String>
        get() = launchboxMetadata?.images
            ?.filter { it.type.contains("Screenshot", ignoreCase = true) }
            ?.map { it.url }
            ?: emptyList()

    val discNumber: Int?
        get() = tags?.firstOrNull { DISC_TAG_REGEX.matches(it) }
            ?.let { DISC_NUMBER_REGEX.find(it)?.value?.toIntOrNull() }

    val isDiscVariant: Boolean
        get() = discNumber != null

    val isFolderRom: Boolean
        get() = multi || hasMultipleFiles || hasNestedSingleFile

    val needsServerBuiltZipExtraction: Boolean
        get() = (files?.size ?: 0) > 1

    val isFolderMultiDisc: Boolean
        get() = hasMultipleFiles && files?.any { it.isDiscVariant } == true

    val hasDiscSiblings: Boolean
        get() = isFolderMultiDisc || (isDiscVariant && effectiveSiblings.any { sibling ->
            sibling.fileNameNoExt.contains(DISC_TAG_REGEX)
        })

    val hasNonDiscSiblings: Boolean
        get() = effectiveSiblings.any { !it.isDiscVariant }

    companion object {
        private val DISC_TAG_REGEX = Regex("Disc \\d+", RegexOption.IGNORE_CASE)
        private val DISC_NUMBER_REGEX = Regex("\\d+")
    }
}

@JsonClass(generateAdapter = true)
data class RomMUserScreenshot(
    @Json(name = "id") val id: Long,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "download_path") val downloadPath: String? = null,
    @Json(name = "is_gallery") val isGallery: Boolean = false,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMSibling(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String? = null,
    @Json(name = "fs_name_no_tags") val fileNameNoTags: String,
    @Json(name = "fs_name_no_ext") val fileNameNoExt: String,
    @Json(name = "is_main_sibling") val isMainSibling: Boolean? = null
) {
    val discNumber: Int?
        get() = DISC_NUMBER_REGEX.find(
            DISC_TAG_REGEX.find(fileNameNoExt)?.value ?: ""
        )?.value?.toIntOrNull()

    val isDiscVariant: Boolean
        get() = discNumber != null

    companion object {
        private val DISC_TAG_REGEX = Regex("\\(Disc \\d+\\)", RegexOption.IGNORE_CASE)
        private val DISC_NUMBER_REGEX = Regex("\\d+")
    }
}

@JsonClass(generateAdapter = true)
data class RomMRomFile(
    @Json(name = "id") val id: Long,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_path") val filePath: String,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long,
    @Json(name = "full_path") val fullPath: String,
    @Json(name = "category") val category: String? = null,
    @Json(name = "track_meta") val trackMeta: RomMTrackMeta? = null
) {
    val discNumber: Int?
        get() = DISC_NUMBER_REGEX.find(
            DISC_TAG_REGEX.find(fileName)?.value ?: ""
        )?.value?.toIntOrNull()

    val isDiscVariant: Boolean
        get() = discNumber != null

    companion object {
        private val DISC_TAG_REGEX = Regex("\\(Disc \\d+\\)", RegexOption.IGNORE_CASE)
        private val DISC_NUMBER_REGEX = Regex("\\d+")
    }
}

@JsonClass(generateAdapter = true)
data class RomMTrackMeta(
    @Json(name = "title") val title: String? = null,
    @Json(name = "artist") val artist: String? = null,
    @Json(name = "album") val album: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "track") val track: Int? = null,
    @Json(name = "disc") val disc: Int? = null,
    @Json(name = "duration_seconds") val durationSeconds: Double? = null,
    @Json(name = "has_embedded_cover") val hasEmbeddedCover: Boolean = false,
    @Json(name = "cover_path") val coverPath: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMMetadatum(
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "companies") val companies: List<String>? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "franchises") val franchises: List<String>? = null,
    @Json(name = "collections") val collections: List<String>? = null,
    @Json(name = "game_modes") val gameModes: List<String>? = null,
    @Json(name = "average_rating") val averageRating: Float? = null,
    @Json(name = "player_count") val playerCount: String? = null,
    @Json(name = "age_ratings") val ageRatings: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class RomMLaunchboxMetadata(
    @Json(name = "images") val images: List<RomMLaunchboxImage>? = null
)

@JsonClass(generateAdapter = true)
data class RomMSsMetadata(
    @Json(name = "box2d_back_path") val box2dBackPath: String? = null,
    @Json(name = "box2d_side_path") val box2dSidePath: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMLaunchboxImage(
    @Json(name = "url") val url: String,
    @Json(name = "type") val type: String,
    @Json(name = "region") val region: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMUser(
    @Json(name = "id") val id: Long,
    @Json(name = "username") val username: String,
    @Json(name = "enabled") val enabled: Boolean,
    @Json(name = "role") val role: String,
    @Json(name = "ra_username") val raUsername: String? = null,
    @Json(name = "ra_progression") val raProgression: RomMRAProgression? = null
)

@JsonClass(generateAdapter = true)
data class RomMRAProgression(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "results") val results: List<RomMRAGameProgression> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMRAGameProgression(
    @Json(name = "rom_ra_id") val romRaId: Long? = null,
    @Json(name = "max_possible") val maxPossible: Int? = null,
    @Json(name = "num_awarded") val numAwarded: Int? = null,
    @Json(name = "num_awarded_hardcore") val numAwardedHardcore: Int? = null,
    @Json(name = "most_recent_awarded_date") val mostRecentAwardedDate: String? = null,
    @Json(name = "earned_achievements") val earnedAchievements: List<RomMEarnedAchievement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMEarnedAchievement(
    @Json(name = "id") val id: String,
    @Json(name = "date") val date: String? = null,
    @Json(name = "date_hardcore") val dateHardcore: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMRARefreshRequest(
    @Json(name = "incremental") val incremental: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RomMHeartbeatResponse(
    @Json(name = "SYSTEM") val system: RomMSystem? = null,
    @Json(name = "METADATA_SOURCES") val metadataSources: RomMMetadataSources? = null
) {
    val version: String?
        get() = system?.version

    val libretroApiEnabled: Boolean?
        get() = metadataSources?.libretroApiEnabled

    val steamGridDbEnabled: Boolean?
        get() = metadataSources?.steamGridDbEnabled
}

@JsonClass(generateAdapter = true)
data class RomMCoverSearchResult(
    @Json(name = "name") val name: String? = null,
    @Json(name = "resources") val resources: List<RomMCoverResource>? = null
)

/** SteamGridDB grid. Fields beyond thumb/url/type only exist on newer servers. */
@JsonClass(generateAdapter = true)
data class RomMCoverResource(
    @Json(name = "url") val url: String? = null,
    @Json(name = "thumb") val thumb: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null,
    @Json(name = "style") val style: String? = null,
    @Json(name = "nsfw") val nsfw: Boolean? = null,
    @Json(name = "humor") val humor: Boolean? = null,
    @Json(name = "epilepsy") val epilepsy: Boolean? = null
) {
    /** SteamGridDB serves full resolution under /grids/; thumbs are the same url under /thumb/. */
    val fullResUrl: String? get() = url ?: thumb?.replace("/thumb/", "/grid/")
}

@JsonClass(generateAdapter = true)
data class RomMSystem(
    @Json(name = "VERSION") val version: String? = null,
    @Json(name = "SHOW_SETUP_WIZARD") val showSetupWizard: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMMetadataSources(
    @Json(name = "LIBRETRO_API_ENABLED") val libretroApiEnabled: Boolean? = null,
    @Json(name = "STEAMGRIDDB_API_ENABLED") val steamGridDbEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RomMSearchRequest(
    @Json(name = "search_term") val searchTerm: String,
    @Json(name = "search_by") val searchBy: String = "name"
)

@JsonClass(generateAdapter = true)
data class RomMRomPage(
    @Json(name = "items") val items: List<RomMRom>,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page") val page: Int? = null,
    @Json(name = "size") val size: Int? = null,
    @Json(name = "pages") val pages: Int? = null
)

@JsonClass(generateAdapter = true)
data class RomMCollection(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "rom_ids") val romIds: List<Long> = emptyList(),
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMAutoCollection(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "rom_ids") val romIds: List<Long> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMCollectionCreate(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "rom_ids") val romIds: List<Long> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMRomUser(
    @Json(name = "rating") val rating: Int = 0,
    @Json(name = "difficulty") val difficulty: Int = 0,
    @Json(name = "completion") val completion: Int = 0,
    @Json(name = "status") val status: String? = null,
    @Json(name = "backlogged") val backlogged: Boolean = false,
    @Json(name = "now_playing") val nowPlaying: Boolean = false,
    @Json(name = "last_played") val lastPlayed: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMUserPropsUpdateData(
    @Json(name = "rating") val rating: Int? = null,
    @Json(name = "difficulty") val difficulty: Int? = null,
    @Json(name = "completion") val completion: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "backlogged") val backlogged: Boolean? = null,
    @Json(name = "now_playing") val nowPlaying: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RomMUserPropsUpdate(
    @Json(name = "data") val data: RomMUserPropsUpdateData
)

@JsonClass(generateAdapter = true)
data class RomMRAMetadata(
    @Json(name = "achievements") val achievements: List<RomMAchievement>? = null
)

@JsonClass(generateAdapter = true)
data class RomMAchievement(
    @Json(name = "ra_id") val raId: Long,
    @Json(name = "badge_id") val badgeId: String?,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "points") val points: Int,
    @Json(name = "type") val type: String?,
    @Json(name = "badge_url") val badgeUrl: String?,
    @Json(name = "badge_url_lock") val badgeUrlLock: String?
)

@JsonClass(generateAdapter = true)
data class RomMPairingExchangeRequest(
    @Json(name = "code") val code: String
)

@JsonClass(generateAdapter = true)
data class RomMPairingExchangeResponse(
    @Json(name = "raw_token") val rawToken: String,
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String? = null,
    @Json(name = "scopes") val scopes: List<String>? = null,
    @Json(name = "user_id") val userId: Long? = null
)

sealed class RomMResult<out T> {
    data class Success<T>(val data: T) : RomMResult<T>()
    data class Error(val message: String, val code: Int? = null) : RomMResult<Nothing>()
}

fun <T> RomMResult<T>.toResult(): Result<T> = when (this) {
    is RomMResult.Success -> Result.success(data)
    is RomMResult.Error -> Result.failure(Exception(message))
}

data class SyncProgress(
    val isSyncing: Boolean = false,
    val currentPlatform: String = "",
    val platformsTotal: Int = 0,
    val platformsDone: Int = 0,
    val gamesTotal: Int = 0,
    val gamesDone: Int = 0
)

data class SyncResult(
    val platformsSynced: Int,
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesDeleted: Int,
    val errors: List<String>
)

data class MultiDiscGroup(
    val primaryRommId: Long,
    val siblingRommIds: List<Long>,
    val platformSlug: String
)

data class DownloadResponse(
    val body: okhttp3.ResponseBody,
    val isPartialContent: Boolean
)

object RomMUtils {
    fun createSortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> title.drop(4)
            lower.startsWith("a ") -> title.drop(2)
            lower.startsWith("an ") -> title.drop(3)
            else -> title
        }.lowercase()
    }

    fun buildMediaUrl(baseUrl: String, path: String): String {
        return if (path.startsWith("http")) path else "$baseUrl$path"
    }

    fun getDedupKey(rom: RomMRom): String? {
        return when {
            rom.igdbId != null -> "igdb:${rom.igdbId}"
            rom.mobyId != null -> "moby:${rom.mobyId}"
            rom.raId != null -> "ra:${rom.raId}"
            else -> null
        }
    }
}
