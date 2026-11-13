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
    @Json(name = "md5_hash") val md5Hash: String? = null,
    @Json(name = "sha1_hash") val sha1Hash: String? = null,
    @Json(name = "missing_from_fs") val missingFromFs: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMPlatform(
    @Json(name = "id") val id: Long,
    @Json(name = "slug") val slug: String,
    @Json(name = "name") val name: String,
    @Json(name = "fs_slug") val fsSlug: String? = null,
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
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "fs_name") val fileName: String? = null,
    @Json(name = "fs_size_bytes") val fileSize: Long = 0,
    @Json(name = "full_path") val filePath: String? = null,

    @Json(name = "igdb_id") val igdbId: Long? = null,
    @Json(name = "moby_id") val mobyId: Long? = null,
    @Json(name = "ra_id") val raId: Long? = null,
    @Json(name = "sgdb_id") val sgdbId: Long? = null,
    @Json(name = "ss_id") val ssId: Long? = null,
    @Json(name = "launchbox_id") val launchboxId: Long? = null,
    @Json(name = "hasheous_id") val hasheousId: Long? = null,
    @Json(name = "tgdb_id") val tgdbId: Long? = null,
    @Json(name = "hltb_id") val hltbId: Long? = null,
    @Json(name = "flashpoint_id") val flashpointId: String? = null,
    @Json(name = "steam_id") val steamId: Long? = null,
    @Json(name = "gamelist_id") val gamelistId: String? = null,
    @Json(name = "libretro_id") val libretroId: String? = null,

    @Json(name = "crc_hash") val crcHash: String? = null,
    @Json(name = "md5_hash") val md5Hash: String? = null,
    @Json(name = "sha1_hash") val sha1Hash: String? = null,
    @Json(name = "ra_hash") val raHash: String? = null,

    @Json(name = "summary") val summary: String? = null,
    @Json(name = "metadatum") val metadatum: RomMMetadatum? = null,
    @Json(name = "hltb_metadata") val hltbMetadata: RomMHltbMetadata? = null,
    @Json(name = "launchbox_metadata") val launchboxMetadata: RomMLaunchboxMetadata? = null,
    @Json(name = "ss_metadata") val ssMetadata: RomMSsMetadata? = null,
    @Json(name = "gamelist_metadata") val gamelistMetadata: RomMGamelistMetadata? = null,
    @Json(name = "merged_ra_metadata") val raMetadata: RomMRAMetadata? = null,

    @Json(name = "path_cover_small") val coverSmall: String? = null,
    @Json(name = "path_cover_large") val coverLarge: String? = null,
    @Json(name = "url_cover") val coverUrl: String? = null,

    @Json(name = "regions") val regions: List<String>? = null,
    @Json(name = "languages") val languages: List<String>? = null,
    @Json(name = "revision") val revision: String? = null,

    @Json(name = "merged_screenshots") val screenshotPaths: List<String>? = null,
    @Json(name = "user_screenshots") val userScreenshots: List<RomMUserScreenshot>? = null,
    @Json(name = "all_user_screenshots") val allUserScreenshots: List<RomMUserScreenshot>? = null,
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
    @Json(name = "is_physical") val isPhysical: Boolean = false,
    @Json(name = "has_file_on_disk") val hasFileOnDisk: Boolean = true,
    @Json(name = "title_id") val titleId: String? = null,
    @Json(name = "save_target") val saveTarget: String? = null,
    @Json(name = "save_target_layout") val saveTargetLayout: String? = null,
    @Json(name = "is_identified") val isIdentified: Boolean = true
) {
    val effectiveSiblings: List<RomMSibling> get() = siblingRoms ?: siblings ?: emptyList()

    /**
     * Siblings that are the same game as this rom. The server's sibling list is the union
     * over every metadata source, and a source that files two games under one entry (Moby
     * keeps one page for Pokemon Red and Blue) cross-links roms whose primary metadata
     * disagrees. Grouping follows sibling links transitively, so one such link folds two
     * games into a single library entry. The display name is the primary metadata identity,
     * so a sibling carrying a different one is a different game; a sibling without a name is
     * kept, having nothing to disagree with.
     */
    val sameGameSiblings: List<RomMSibling>
        get() = effectiveSiblings.filter { it.name == null || it.name.equals(name, ignoreCase = true) }
    val genres: List<String>? get() = metadatum?.genres
    val companies: List<String>? get() = metadatum?.companies

    /**
     * RomM split companies into developers and publishers in 5.3. An older server, or a provider
     * that named neither role, still reports only the combined list.
     */
    val developerName: String?
        get() = metadatum?.developers?.firstOrNull() ?: metadatum?.companies?.firstOrNull()

    val publisherName: String? get() = metadatum?.publishers?.firstOrNull()

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

    val boxFrontUrls: List<String>
        get() = launchboxMetadata?.images
            ?.filter {
                it.type.startsWith("Box - Front", ignoreCase = true) ||
                    it.type.equals("Fanart - Box - Front", ignoreCase = true)
            }
            ?.map { it.url }
            ?.filter { it.startsWith("http://") || it.startsWith("https://") }
            ?: emptyList()

    val box3dUrls: List<String>
        get() = launchboxMetadata?.images
            ?.filter { it.type.equals("Box - 3D", ignoreCase = true) }
            ?.map { it.url }
            ?.filter { it.startsWith("http://") || it.startsWith("https://") }
            ?: emptyList()

    /**
     * LaunchBox "Clear Logo" images reachable over http(s). A local-LaunchBox server hands out
     * `launchbox-file://` urls the client cannot fetch, so those are left out.
     */
    val clearLogoUrls: List<String>
        get() = launchboxMetadata?.images
            ?.filter { it.type.equals("Clear Logo", ignoreCase = true) }
            ?.map { it.url }
            ?.filter { it.startsWith("http://") || it.startsWith("https://") }
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
        get() = isFolderMultiDisc || (isDiscVariant && sameGameSiblings.any { sibling ->
            sibling.fileNameNoExt.contains(DISC_TAG_REGEX)
        })

    val hasNonDiscSiblings: Boolean
        get() = sameGameSiblings.any { !it.isDiscVariant }

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
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "download_path") val downloadPath: String? = null,
    @Json(name = "is_gallery") val isGallery: Boolean = false,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "username") val username: String? = null
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
    @Json(name = "track_meta") val trackMeta: RomMTrackMeta? = null,
    @Json(name = "doc_meta") val docMeta: RomMDocMeta? = null
) {
    val discNumber: Int?
        get() = DISC_NUMBER_REGEX.find(
            DISC_TAG_REGEX.find(fileName)?.value ?: ""
        )?.value?.toIntOrNull()

    val isDiscVariant: Boolean
        get() = discNumber != null

    /**
     * Whether this row is part of the game rather than something the storage put beside it.
     *
     * A leading dot covers the unix convention, but a NAS names its sidecar directories in the
     * clear: Synology writes `@eaDir` thumbnail and index data inside the folder it indexes, and
     * the contents one level down look like ordinary files. So the whole path is checked, not
     * just the name, and every caller that lists or sizes a game's files goes through here. A
     * picker that hides these while the size calculation still counts them would be worse than
     * doing neither.
     */
    val isGameContent: Boolean
        get() = filePath.split('/', '\\').plus(fileName).none { segment ->
            segment.startsWith(".") ||
                EXCLUDED_PATH_SEGMENTS.any { segment.equals(it, ignoreCase = true) }
        }

    companion object {
        private val DISC_TAG_REGEX = Regex("\\(Disc \\d+\\)", RegexOption.IGNORE_CASE)
        private val DISC_NUMBER_REGEX = Regex("\\d+")

        /**
         * Directories the storage writes inside a game's own folder. Never game content, and
         * named in the clear rather than hidden, so a dot check alone does not catch them.
         */
        private val EXCLUDED_PATH_SEGMENTS = listOf("@eaDir")
    }
}

@JsonClass(generateAdapter = true)
data class RomMDocumentProgress(
    @Json(name = "rom_file_id") val romFileId: Long,
    @Json(name = "progress") val progress: Float = 0f,
    @Json(name = "last_page") val lastPage: Int? = null,
    @Json(name = "finished") val finished: Boolean = false,
    @Json(name = "last_read_at") val lastReadAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDocumentProgressUpdate(
    @Json(name = "progress") val progress: Float,
    @Json(name = "last_page") val lastPage: Int? = null,
    @Json(name = "finished") val finished: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RomMDocMeta(
    @Json(name = "source") val source: String? = null,
    @Json(name = "source_url") val sourceUrl: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "title") val title: String? = null
)

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
    @Json(name = "developers") val developers: List<String>? = null,
    @Json(name = "publishers") val publishers: List<String>? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "franchises") val franchises: List<String>? = null,
    @Json(name = "collections") val collections: List<String>? = null,
    @Json(name = "game_modes") val gameModes: List<String>? = null,
    @Json(name = "average_rating") val averageRating: Float? = null,
    @Json(name = "player_count") val playerCount: String? = null,
    @Json(name = "age_ratings") val ageRatings: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class RomMHltbMetadata(
    @Json(name = "main_story") val mainStorySec: Int? = null,
    @Json(name = "main_plus_extra") val mainPlusExtraSec: Int? = null,
    @Json(name = "completionist") val completionistSec: Int? = null
)

@JsonClass(generateAdapter = true)
data class RomMLaunchboxMetadata(
    @Json(name = "images") val images: List<RomMLaunchboxImage>? = null,
    @Json(name = "box3d_path") val box3dPath: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMGamelistMetadata(
    @Json(name = "box3d_path") val box3dPath: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMSsMetadata(
    @Json(name = "box2d_path") val box2dPath: String? = null,
    @Json(name = "box3d_path") val box3dPath: String? = null,
    @Json(name = "box2d_back_path") val box2dBackPath: String? = null,
    @Json(name = "box2d_side_path") val box2dSidePath: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "fanart_path") val fanartPath: String? = null,
    @Json(name = "fanart_url") val fanartUrl: String? = null
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
    @Json(name = "avatar_path") val avatarPath: String? = null,
    @Json(name = "ra_username") val raUsername: String? = null,
    @Json(name = "ra_progression") val raProgression: RomMRAProgression? = null
)

/**
 * The platform and rom ids the server withholds from the caller. Field names follow RomM's
 * `HiddenEntitiesSchema`, which nests them under `hidden` rather than exposing them at the
 * top level.
 */
@JsonClass(generateAdapter = true)
data class RomMHiddenEntities(
    @Json(name = "platforms") val platforms: List<Long> = emptyList(),
    @Json(name = "roms") val roms: List<Long> = emptyList()
)

/**
 * `GET /api/permissions/me`. The grants list is deliberately not modelled: the client only
 * needs the hidden sets, which are what tell a missing rom apart from a deleted one.
 */
@JsonClass(generateAdapter = true)
data class RomMPermissionsResponse(
    @Json(name = "is_admin") val isAdmin: Boolean = false,
    @Json(name = "hidden") val hidden: RomMHiddenEntities = RomMHiddenEntities()
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
    @Json(name = "highest_award_kind") val highestAwardKind: String? = null,
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
    @Json(name = "METADATA_SOURCES") val metadataSources: RomMMetadataSources? = null,
    @Json(name = "SAVE_SYNC") val saveSync: RomMSaveSyncFeatures? = null
) {
    val version: String?
        get() = system?.version

    val snapshotsEnabled: Boolean
        get() = saveSync?.snapshots == true

    val libretroApiEnabled: Boolean?
        get() = metadataSources?.libretroApiEnabled

    val steamGridDbEnabled: Boolean?
        get() = metadataSources?.steamGridDbEnabled
}

@JsonClass(generateAdapter = true)
data class RomMCoverSearchResult(
    @Json(name = "name") val name: String? = null,
    @Json(name = "art_type") val artType: String? = null,
    @Json(name = "resources") val resources: List<RomMCoverResource>? = null
)

/**
 * One SteamGridDB image: a grid, hero or logo. Fields beyond thumb/url/type only exist on newer
 * servers.
 */
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
)

@JsonClass(generateAdapter = true)
data class RomMSystem(
    @Json(name = "VERSION") val version: String? = null,
    @Json(name = "SHOW_SETUP_WIZARD") val showSetupWizard: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMSaveSyncFeatures(
    @Json(name = "SNAPSHOTS") val snapshots: Boolean? = null
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
    @Json(name = "total") val total: Int? = null,
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
    @Json(name = "hidden") val hidden: Boolean = false,
    @Json(name = "last_played") val lastPlayed: String? = null,
    @Json(name = "is_main_sibling") val isMainSibling: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMUserPropsUpdateData(
    @Json(name = "rating") val rating: Int? = null,
    @Json(name = "difficulty") val difficulty: Int? = null,
    @Json(name = "completion") val completion: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "backlogged") val backlogged: Boolean? = null,
    @Json(name = "now_playing") val nowPlaying: Boolean? = null,
    @Json(name = "hidden") val hidden: Boolean? = null
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

/**
 * Why a request failed, where the reason changes what the user can do about it.
 *
 * [UNTRUSTED_CERTIFICATE] is separated because its remedy is an action the app can offer rather
 * than a message to read: the server presented a chain this device does not validate, and the
 * underlying exception text names neither the cause nor the fix.
 */
enum class RomMErrorKind {
    UNTRUSTED_CERTIFICATE,
    CLIENT_CERTIFICATE_REQUIRED
}

sealed class RomMResult<out T> {
    data class Success<T>(val data: T) : RomMResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val kind: RomMErrorKind? = null
    ) : RomMResult<Nothing>()
}

fun <T> RomMResult<T>.toResult(): Result<T> = when (this) {
    is RomMResult.Success -> Result.success(data)
    is RomMResult.Error -> Result.failure(Exception(message))
}

enum class PlatformSyncState { IDLE, QUEUED, SYNCING, DONE, ALREADY_SYNCED, FAILED }

/**
 * One platform's place in a library pass. [ALREADY_SYNCED] is a platform a resumed pass skipped
 * because an earlier run finished it, which is not the same as one that ran and changed nothing.
 */
data class PlatformSyncRow(
    val platformId: Long,
    val name: String,
    val slug: String,
    val state: PlatformSyncState = PlatformSyncState.QUEUED,
    val gamesDone: Int = 0,
    val gamesTotal: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val error: String? = null
)

data class SyncProgress(
    val isSyncing: Boolean = false,
    val currentPlatform: String = "",
    val currentPlatformSlug: String = "",
    val platformsTotal: Int = 0,
    val platformsDone: Int = 0,
    val gamesTotal: Int = 0,
    val gamesDone: Int = 0,
    val platforms: List<PlatformSyncRow> = emptyList()
)

data class SyncResult(
    val platformsSynced: Int,
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesDeleted: Int,
    val errors: List<String>,
    /**
     * A second sync was asked for while one was running, so this one did nothing. Callers back
     * off quietly rather than surfacing it. It is a flag rather than an error string because
     * the string was previously matched to drive that decision, which stops working the moment
     * the text changes for any reason.
     */
    val alreadyInProgress: Boolean = false
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
}
