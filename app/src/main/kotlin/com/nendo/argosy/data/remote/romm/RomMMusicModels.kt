package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMMusicTrack(
    @Json(name = "rom_file_id") val romFileId: Long,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "title") val title: String? = null,
    @Json(name = "artist") val artist: String? = null,
    @Json(name = "album") val album: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "track") val track: Int? = null,
    @Json(name = "disc") val disc: Int? = null,
    @Json(name = "duration_seconds") val durationSeconds: Double? = null,
    @Json(name = "has_embedded_cover") val hasEmbeddedCover: Boolean = false,
    @Json(name = "game_name") val gameName: String? = null,
    @Json(name = "platform_id") val platformId: Long,
    @Json(name = "platform_slug") val platformSlug: String,
    @Json(name = "platform_name") val platformName: String,
    @Json(name = "stream_url") val streamUrl: String,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "game_cover_url") val gameCoverUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMMusicPlaylist(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "user_id") val userId: Long,
    @Json(name = "owner_username") val ownerUsername: String? = null,
    @Json(name = "track_count") val trackCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class RomMMusicPlaylistCreate(
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class RomMMusicPlaylistRename(
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class RomMMusicPlaylistTracks(
    @Json(name = "rom_file_ids") val romFileIds: List<Long>
)

@JsonClass(generateAdapter = true)
data class RomMMusicGame(
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "name") val name: String,
    @Json(name = "platform_id") val platformId: Long,
    @Json(name = "platform_slug") val platformSlug: String,
    @Json(name = "platform_name") val platformName: String,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "count") val count: Int = 0
)

@JsonClass(generateAdapter = true)
data class RomMMusicGamePage(
    @Json(name = "items") val items: List<RomMMusicGame>,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "offset") val offset: Int? = null
)

@JsonClass(generateAdapter = true)
data class RomMMusicFacetValue(
    @Json(name = "value") val value: Any,
    @Json(name = "count") val count: Int
) {
    val label: String
        get() = when (value) {
            is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
}

@JsonClass(generateAdapter = true)
data class RomMMusicTrackPage(
    @Json(name = "items") val items: List<RomMMusicTrack>,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "offset") val offset: Int? = null
)

@JsonClass(generateAdapter = true)
data class RomMMusicFacetPage(
    @Json(name = "items") val items: List<RomMMusicFacetValue>,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "offset") val offset: Int? = null
)

enum class RomMMusicFacet(val path: String) {
    ARTISTS("artists"),
    ALBUMS("albums"),
    GENRES("genres")
}
