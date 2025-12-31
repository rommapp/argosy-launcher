package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMPlatform(
    @Json(name = "id") val id: Long,
    @Json(name = "slug") val slug: String,
    @Json(name = "name") val name: String,
    @Json(name = "fs_slug") val fsSlug: String?,
    @Json(name = "rom_count") val romCount: Int,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "url_logo") val logoUrl: String? = null
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

    @Json(name = "summary") val summary: String?,
    @Json(name = "metadatum") val metadatum: RomMMetadatum? = null,
    @Json(name = "launchbox_metadata") val launchboxMetadata: RomMLaunchboxMetadata? = null,
    @Json(name = "merged_ra_metadata") val raMetadata: RomMRAMetadata? = null,

    @Json(name = "path_cover_small") val coverSmall: String?,
    @Json(name = "path_cover_large") val coverLarge: String?,
    @Json(name = "url_cover") val coverUrl: String? = null,

    @Json(name = "regions") val regions: List<String>?,
    @Json(name = "languages") val languages: List<String>?,
    @Json(name = "revision") val revision: String?,

    @Json(name = "merged_screenshots") val screenshotPaths: List<String>? = null,
    @Json(name = "rom_user") val romUser: RomMRomUser? = null,

    @Json(name = "tags") val tags: List<String>? = null,
    @Json(name = "siblings") val siblings: List<RomMSibling>? = null,
    @Json(name = "multi") val multi: Boolean = false,
    @Json(name = "files") val files: List<RomMRomFile>? = null
) {
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

    val isFolderMultiDisc: Boolean
        get() = multi && files?.any { it.isDiscVariant } == true

    val hasDiscSiblings: Boolean
        get() = isFolderMultiDisc || (isDiscVariant && siblings?.any { sibling ->
            sibling.fileNameNoExt.contains(DISC_TAG_REGEX)
        } == true)

    companion object {
        private val DISC_TAG_REGEX = Regex("Disc \\d+", RegexOption.IGNORE_CASE)
        private val DISC_NUMBER_REGEX = Regex("\\d+")
    }
}

@JsonClass(generateAdapter = true)
data class RomMSibling(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "fs_name_no_tags") val fileNameNoTags: String,
    @Json(name = "fs_name_no_ext") val fileNameNoExt: String
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
    @Json(name = "category") val category: String? = null
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
data class RomMMetadatum(
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "companies") val companies: List<String>? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "franchises") val franchises: List<String>? = null,
    @Json(name = "game_modes") val gameModes: List<String>? = null,
    @Json(name = "average_rating") val averageRating: Float? = null
)

@JsonClass(generateAdapter = true)
data class RomMLaunchboxMetadata(
    @Json(name = "images") val images: List<RomMLaunchboxImage>? = null
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
data class RomMTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String
)

@JsonClass(generateAdapter = true)
data class RomMHeartbeatResponse(
    @Json(name = "SYSTEM") val system: RomMSystem? = null
) {
    val version: String?
        get() = system?.version
}

@JsonClass(generateAdapter = true)
data class RomMSystem(
    @Json(name = "VERSION") val version: String? = null,
    @Json(name = "SHOW_SETUP_WIZARD") val showSetupWizard: Boolean = false
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

sealed class RomMResult<out T> {
    data class Success<T>(val data: T) : RomMResult<T>()
    data class Error(val message: String, val code: Int? = null) : RomMResult<Nothing>()
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
