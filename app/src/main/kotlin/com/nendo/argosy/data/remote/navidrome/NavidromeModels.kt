package com.nendo.argosy.data.remote.navidrome

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class NavidromeCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    override fun toString(): String = "NavidromeCredentials(serverUrl=$serverUrl, username=$username)"
}

/**
 * A `failed` answer from the server. [code] is the Subsonic error code (40 is wrong credentials,
 * 70 is a missing item) and [serverMessage] is the server's own wording, null when it sent none.
 */
class NavidromeApiException(val code: Int?, val serverMessage: String?) :
    Exception(serverMessage ?: "Subsonic error $code")

data class NavidromeServerInfo(
    val apiVersion: String?,
    val type: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean
)

@JsonClass(generateAdapter = true)
data class NavidromeEnvelope(
    @Json(name = "subsonic-response") val response: NavidromeResponse? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeResponse(
    val status: String? = null,
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: NavidromeErrorDto? = null,
    val albumList2: NavidromeAlbumListDto? = null,
    val artists: NavidromeArtistsDto? = null,
    val artistInfo2: NavidromeArtistInfoDto? = null,
    val album: NavidromeAlbumDto? = null,
    val playlists: NavidromePlaylistsDto? = null,
    val playlist: NavidromePlaylistDto? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeErrorDto(
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeAlbumListDto(
    val album: List<NavidromeAlbumDto>? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeAlbumDto(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val playCount: Long? = null,
    val song: List<NavidromeSongDto>? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeSongDto(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val track: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val isrc: List<String>? = null,
    val musicBrainzId: String? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeArtistsDto(
    val index: List<NavidromeArtistIndexDto>? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeArtistIndexDto(
    val name: String? = null,
    val artist: List<NavidromeArtistDto>? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeArtistDto(
    val id: String,
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val artistImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class NavidromeArtistInfoDto(
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class NavidromePlaylistsDto(
    val playlist: List<NavidromePlaylistDto>? = null
)

@JsonClass(generateAdapter = true)
data class NavidromePlaylistDto(
    val id: String,
    val name: String? = null,
    val owner: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val readonly: Boolean? = null,
    val entry: List<NavidromeSongDto>? = null
)
