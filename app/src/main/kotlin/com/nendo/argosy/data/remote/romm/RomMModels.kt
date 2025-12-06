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

    @Json(name = "summary") val summary: String?,
    @Json(name = "metadatum") val metadatum: RomMMetadatum? = null,
    @Json(name = "launchbox_metadata") val launchboxMetadata: RomMLaunchboxMetadata? = null,

    @Json(name = "path_cover_small") val coverSmall: String?,
    @Json(name = "path_cover_large") val coverLarge: String?,
    @Json(name = "url_cover") val coverUrl: String? = null,

    @Json(name = "regions") val regions: List<String>?,
    @Json(name = "languages") val languages: List<String>?,
    @Json(name = "revision") val revision: String?,

    @Json(name = "merged_screenshots") val screenshotPaths: List<String>? = null,
    @Json(name = "rom_user") val romUser: RomMRomUser? = null
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
}

@JsonClass(generateAdapter = true)
data class RomMMetadatum(
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "companies") val companies: List<String>? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "franchises") val franchises: List<String>? = null,
    @Json(name = "game_modes") val gameModes: List<String>? = null
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
    @Json(name = "role") val role: String
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
data class RomMUserPropsUpdate(
    @Json(name = "rating") val rating: Int? = null,
    @Json(name = "difficulty") val difficulty: Int? = null
)
