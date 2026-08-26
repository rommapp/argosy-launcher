package com.nendo.argosy.data.remote.jellyfin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * The server's time unit: 100ns intervals, ten million to the second. Every position, runtime and
 * seek target on the wire is expressed in these, never in milliseconds.
 */
const val TICKS_PER_SECOND = 10_000_000L
const val TICKS_PER_MILLISECOND = 10_000L

sealed class JellyfinResult<out T> {
    data class Success<T>(val data: T) : JellyfinResult<T>()
    data class Error(val message: String, val code: Int? = null) : JellyfinResult<Nothing>()
}

fun <T> JellyfinResult<T>.toResult(): Result<T> = when (this) {
    is JellyfinResult.Success -> Result.success(data)
    is JellyfinResult.Error -> Result.failure(Exception(message))
}

/**
 * Parses one of the server's timestamps, which carry seven fractional digits rather than the three
 * an ISO instant usually has. A value that does not parse is dropped rather than defaulted: a wrong
 * date on a row reads as fact everywhere downstream, an absent one reads as unknown.
 */
fun parseJellyfinInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
}

@JsonClass(generateAdapter = true)
data class JellyfinPublicSystemInfo(
    @Json(name = "LocalAddress") val localAddress: String? = null,
    @Json(name = "ServerName") val serverName: String? = null,
    @Json(name = "Version") val version: String? = null,
    @Json(name = "ProductName") val productName: String? = null,
    @Json(name = "OperatingSystem") val operatingSystem: String? = null,
    @Json(name = "Id") val id: String? = null,
    @Json(name = "StartupWizardCompleted") val startupWizardCompleted: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinUser(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "ServerId") val serverId: String? = null,
    @Json(name = "HasPassword") val hasPassword: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinAuthenticateByNameRequest(
    @Json(name = "Username") val username: String,
    @Json(name = "Pw") val pw: String
)

@JsonClass(generateAdapter = true)
data class JellyfinAuthenticateWithQuickConnectRequest(
    @Json(name = "Secret") val secret: String
)

@JsonClass(generateAdapter = true)
data class JellyfinAuthenticationResult(
    @Json(name = "AccessToken") val accessToken: String? = null,
    @Json(name = "ServerId") val serverId: String? = null,
    @Json(name = "User") val user: JellyfinUser? = null
)

/**
 * The record a Quick Connect attempt is identified by. [code] is what the user types into a client
 * that is already signed in; [secret] is what this device polls and later redeems, and never leaves
 * the device.
 */
@JsonClass(generateAdapter = true)
data class JellyfinQuickConnectResult(
    @Json(name = "Authenticated") val authenticated: Boolean = false,
    @Json(name = "Secret") val secret: String,
    @Json(name = "Code") val code: String,
    @Json(name = "DeviceId") val deviceId: String? = null,
    @Json(name = "DeviceName") val deviceName: String? = null,
    @Json(name = "AppName") val appName: String? = null,
    @Json(name = "AppVersion") val appVersion: String? = null,
    @Json(name = "DateAdded") val dateAdded: String? = null
)

/**
 * A page of items.
 *
 * [totalRecordCount] is null when the server did not send one, which is not the same as zero: a
 * server with the total record count disabled answers every page without it, and reading that as
 * "the library holds nothing beyond this page" truncates the enumeration at one page.
 */
@JsonClass(generateAdapter = true)
data class JellyfinItemsResponse(
    @Json(name = "Items") val items: List<JellyfinItem> = emptyList(),
    @Json(name = "TotalRecordCount") val totalRecordCount: Int? = null,
    @Json(name = "StartIndex") val startIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class JellyfinNameId(
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Id") val id: String? = null
)

/**
 * Someone credited on a title.
 *
 * [role] is the character for an actor and is absent for everyone else, so it is what separates a
 * cast entry from a crew one in the same list. [primaryImageTag] addresses a portrait held against
 * the PERSON's id, not the title's.
 */
@JsonClass(generateAdapter = true)
data class JellyfinPerson(
    @Json(name = "Id") val id: String? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Role") val role: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "PrimaryImageTag") val primaryImageTag: String? = null
)

const val PERSON_TYPE_ACTOR = "Actor"
const val PERSON_TYPE_DIRECTOR = "Director"

/**
 * One item at any level of the hierarchy: a library view, a movie, a series, a season or an episode.
 *
 * The server answers with one shape for all of them and fills only the fields that apply, so this
 * is deliberately one wide model rather than a sealed family. [type] is the discriminator and maps
 * to `MediaItemType`.
 *
 * [container] is not authoritative for a playback decision. On a list item it can arrive as an
 * ffprobe-style comma list (`mov,mp4,m4a,3gp,3g2,mj2`); the single resolved container appears only
 * on the media source returned by PlaybackInfo.
 */
@JsonClass(generateAdapter = true)
data class JellyfinItem(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "ServerId") val serverId: String? = null,
    @Json(name = "SortName") val sortName: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "CollectionType") val collectionType: String? = null,
    @Json(name = "Overview") val overview: String? = null,
    @Json(name = "Container") val container: String? = null,
    @Json(name = "PremiereDate") val premiereDate: String? = null,
    @Json(name = "DateCreated") val dateCreated: String? = null,
    @Json(name = "EndDate") val endDate: String? = null,
    @Json(name = "ProductionYear") val productionYear: Int? = null,
    @Json(name = "CommunityRating") val communityRating: Float? = null,
    @Json(name = "CriticRating") val criticRating: Float? = null,
    @Json(name = "OfficialRating") val officialRating: String? = null,
    @Json(name = "RunTimeTicks") val runTimeTicks: Long? = null,
    @Json(name = "IndexNumber") val indexNumber: Int? = null,
    @Json(name = "ParentIndexNumber") val parentIndexNumber: Int? = null,
    @Json(name = "ParentId") val parentId: String? = null,
    @Json(name = "SeriesId") val seriesId: String? = null,
    @Json(name = "SeriesName") val seriesName: String? = null,
    @Json(name = "SeasonId") val seasonId: String? = null,
    @Json(name = "SeasonName") val seasonName: String? = null,
    @Json(name = "ChildCount") val childCount: Int? = null,
    @Json(name = "IsFolder") val isFolder: Boolean? = null,
    @Json(name = "MediaType") val mediaType: String? = null,
    @Json(name = "LocationType") val locationType: String? = null,
    @Json(name = "VideoType") val videoType: String? = null,
    @Json(name = "Status") val status: String? = null,
    @Json(name = "Genres") val genres: List<String>? = null,
    @Json(name = "Studios") val studios: List<JellyfinNameId>? = null,
    @Json(name = "People") val people: List<JellyfinPerson>? = null,
    @Json(name = "ImageTags") val imageTags: Map<String, String>? = null,
    @Json(name = "BackdropImageTags") val backdropImageTags: List<String>? = null,
    @Json(name = "ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
    @Json(name = "ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @Json(name = "SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @Json(name = "UserData") val userData: JellyfinUserData? = null,
    @Json(name = "MediaSources") val mediaSources: List<JellyfinMediaSource>? = null,
    @Json(name = "MediaStreams") val mediaStreams: List<JellyfinMediaStream>? = null,
    @Json(name = "Chapters") val chapters: List<JellyfinChapter>? = null,
    @Json(name = "Trickplay") val trickplay: Map<String, Map<String, JellyfinTrickplayInfo>>? = null,
    @Json(name = "ProviderIds") val providerIds: Map<String, String>? = null
) {
    val primaryImageTag: String? get() = imageTags?.get(IMAGE_TYPE_PRIMARY)
    val thumbImageTag: String? get() = imageTags?.get(IMAGE_TYPE_THUMB)
    val logoImageTag: String? get() = imageTags?.get(IMAGE_TYPE_LOGO)

    /**
     * This item's own first backdrop, and never a parent's.
     *
     * [parentBackdropImageTags] names images belonging to the parent item, so a tag taken from it
     * only ever addresses a parent's id. Storing one against the child conflates the two and every
     * request built from it answers 404. A caller that wants the parent's artwork asks for the
     * parent by id.
     */
    val ownBackdropImageTag: String? get() = backdropImageTags?.firstOrNull()

    val tmdbId: String? get() = providerId(PROVIDER_TMDB)
    val imdbId: String? get() = providerId(PROVIDER_IMDB)
    val tvdbId: String? get() = providerId(PROVIDER_TVDB)

    /**
     * Reads one external id by provider name, ignoring case.
     *
     * The casing of a key in [providerIds] is decided by whichever metadata plugin wrote it, not by
     * the server, so the same provider reaches us as `Tmdb` from one library and `TMDB` from
     * another. An exact-match read finds one and silently misses the other, which looks like a
     * title that was never matched.
     */
    private fun providerId(provider: String): String? = providerIds
        ?.entries
        ?.firstOrNull { it.key.equals(provider, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
}

const val IMAGE_TYPE_PRIMARY = "Primary"
const val IMAGE_TYPE_BACKDROP = "Backdrop"
const val IMAGE_TYPE_THUMB = "Thumb"
const val IMAGE_TYPE_LOGO = "Logo"

const val PROVIDER_TMDB = "Tmdb"
const val PROVIDER_IMDB = "Imdb"
const val PROVIDER_TVDB = "Tvdb"

/**
 * [playedPercentage] is present only while an item is partially watched, so its absence means
 * either untouched or finished and [played] is what separates the two.
 */
@JsonClass(generateAdapter = true)
data class JellyfinUserData(
    @Json(name = "PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @Json(name = "PlayCount") val playCount: Int = 0,
    @Json(name = "IsFavorite") val isFavorite: Boolean = false,
    @Json(name = "Played") val played: Boolean = false,
    @Json(name = "PlayedPercentage") val playedPercentage: Double? = null,
    @Json(name = "UnplayedItemCount") val unplayedItemCount: Int? = null,
    @Json(name = "LastPlayedDate") val lastPlayedDate: String? = null,
    @Json(name = "Key") val key: String? = null,
    @Json(name = "ItemId") val itemId: String? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinChapter(
    @Json(name = "StartPositionTicks") val startPositionTicks: Long = 0,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "ImageTag") val imageTag: String? = null
)

/**
 * The geometry of one set of scrub thumbnails, exactly as the server generated it.
 *
 * It arrives on the item as `Trickplay`, keyed by media source and then by thumbnail width, and an
 * item the server has generated nothing for carries an empty map rather than a null one. That map is
 * the only proof thumbnails exist: the server version says the endpoint is there, never that this
 * title has anything behind it.
 *
 * [tileWidth] and [tileHeight] are counts, not pixels - thumbnails per row and per column of one
 * sheet - and [interval] is milliseconds between thumbnails. A library whose administrator changed
 * either would preview at the wrong offsets if these were assumed instead of read.
 */
@JsonClass(generateAdapter = true)
data class JellyfinTrickplayInfo(
    @Json(name = "Width") val width: Int = 0,
    @Json(name = "Height") val height: Int = 0,
    @Json(name = "TileWidth") val tileWidth: Int = 0,
    @Json(name = "TileHeight") val tileHeight: Int = 0,
    @Json(name = "ThumbnailCount") val thumbnailCount: Int = 0,
    @Json(name = "Interval") val interval: Int = 0,
    @Json(name = "Bandwidth") val bandwidth: Int = 0
)

@JsonClass(generateAdapter = true)
data class JellyfinPlaybackInfoRequest(
    @Json(name = "UserId") val userId: String,
    @Json(name = "MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @Json(name = "StartTimeTicks") val startTimeTicks: Long = 0,
    @Json(name = "AudioStreamIndex") val audioStreamIndex: Int? = null,
    @Json(name = "SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @Json(name = "MediaSourceId") val mediaSourceId: String? = null,
    @Json(name = "EnableDirectPlay") val enableDirectPlay: Boolean = true,
    @Json(name = "EnableDirectStream") val enableDirectStream: Boolean = true,
    @Json(name = "EnableTranscoding") val enableTranscoding: Boolean = true,
    @Json(name = "AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean = true,
    @Json(name = "AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean = true,
    @Json(name = "AutoOpenLiveStream") val autoOpenLiveStream: Boolean = false,
    @Json(name = "DeviceProfile") val deviceProfile: JellyfinDeviceProfile
)

@JsonClass(generateAdapter = true)
data class JellyfinPlaybackInfoResponse(
    @Json(name = "MediaSources") val mediaSources: List<JellyfinMediaSource> = emptyList(),
    @Json(name = "PlaySessionId") val playSessionId: String? = null
)

/**
 * One playable version of an item.
 *
 * [transcodingUrl] is absent whenever the server answered with direct play, and is a server-relative
 * path when present; it already carries its own api key and play session id. It expires with the
 * transcode session, so it is negotiated per playback and never stored.
 */
@JsonClass(generateAdapter = true)
data class JellyfinMediaSource(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Container") val container: String? = null,
    @Json(name = "Protocol") val protocol: String? = null,
    @Json(name = "Path") val path: String? = null,
    @Json(name = "Size") val size: Long? = null,
    @Json(name = "Bitrate") val bitrate: Int? = null,
    @Json(name = "RunTimeTicks") val runTimeTicks: Long? = null,
    @Json(name = "ETag") val eTag: String? = null,
    @Json(name = "SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @Json(name = "SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @Json(name = "SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @Json(name = "TranscodingUrl") val transcodingUrl: String? = null,
    @Json(name = "TranscodingContainer") val transcodingContainer: String? = null,
    @Json(name = "TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @Json(name = "DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @Json(name = "DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @Json(name = "MediaStreams") val mediaStreams: List<JellyfinMediaStream> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JellyfinMediaStream(
    @Json(name = "Index") val index: Int,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "Codec") val codec: String? = null,
    @Json(name = "Language") val language: String? = null,
    @Json(name = "DisplayTitle") val displayTitle: String? = null,
    @Json(name = "Title") val title: String? = null,
    @Json(name = "Profile") val profile: String? = null,
    @Json(name = "Level") val level: Double? = null,
    @Json(name = "Width") val width: Int? = null,
    @Json(name = "Height") val height: Int? = null,
    @Json(name = "RealFrameRate") val realFrameRate: Double? = null,
    @Json(name = "BitRate") val bitRate: Int? = null,
    @Json(name = "Channels") val channels: Int? = null,
    @Json(name = "SampleRate") val sampleRate: Int? = null,
    @Json(name = "IsDefault") val isDefault: Boolean = false,
    @Json(name = "IsForced") val isForced: Boolean = false,
    @Json(name = "IsExternal") val isExternal: Boolean = false,
    @Json(name = "IsTextSubtitleStream") val isTextSubtitleStream: Boolean = false,
    @Json(name = "DeliveryUrl") val deliveryUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinPlaybackStartInfo(
    @Json(name = "ItemId") val itemId: String,
    @Json(name = "MediaSourceId") val mediaSourceId: String? = null,
    @Json(name = "PlaySessionId") val playSessionId: String? = null,
    @Json(name = "PositionTicks") val positionTicks: Long = 0,
    @Json(name = "IsPaused") val isPaused: Boolean = false,
    @Json(name = "IsMuted") val isMuted: Boolean = false,
    @Json(name = "PlayMethod") val playMethod: String = PLAY_METHOD_DIRECT_PLAY,
    @Json(name = "CanSeek") val canSeek: Boolean = true,
    @Json(name = "AudioStreamIndex") val audioStreamIndex: Int? = null,
    @Json(name = "SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @Json(name = "RepeatMode") val repeatMode: String = "RepeatNone",
    @Json(name = "PlaybackOrder") val playbackOrder: String = "Default"
)

@JsonClass(generateAdapter = true)
data class JellyfinPlaybackProgressInfo(
    @Json(name = "ItemId") val itemId: String,
    @Json(name = "MediaSourceId") val mediaSourceId: String? = null,
    @Json(name = "PlaySessionId") val playSessionId: String? = null,
    @Json(name = "PositionTicks") val positionTicks: Long = 0,
    @Json(name = "IsPaused") val isPaused: Boolean = false,
    @Json(name = "IsMuted") val isMuted: Boolean = false,
    @Json(name = "PlayMethod") val playMethod: String = PLAY_METHOD_DIRECT_PLAY,
    @Json(name = "CanSeek") val canSeek: Boolean = true,
    @Json(name = "AudioStreamIndex") val audioStreamIndex: Int? = null,
    @Json(name = "SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @Json(name = "EventName") val eventName: String? = null,
    @Json(name = "RepeatMode") val repeatMode: String = "RepeatNone",
    @Json(name = "PlaybackOrder") val playbackOrder: String = "Default"
)

@JsonClass(generateAdapter = true)
data class JellyfinPlaybackStopInfo(
    @Json(name = "ItemId") val itemId: String,
    @Json(name = "MediaSourceId") val mediaSourceId: String? = null,
    @Json(name = "PlaySessionId") val playSessionId: String? = null,
    @Json(name = "PositionTicks") val positionTicks: Long = 0,
    @Json(name = "Failed") val failed: Boolean = false
)

const val PLAY_METHOD_DIRECT_PLAY = "DirectPlay"
const val PLAY_METHOD_DIRECT_STREAM = "DirectStream"
const val PLAY_METHOD_TRANSCODE = "Transcode"

const val PROGRESS_EVENT_TIME_UPDATE = "timeupdate"
const val PROGRESS_EVENT_PAUSE = "pause"
const val PROGRESS_EVENT_UNPAUSE = "unpause"

@JsonClass(generateAdapter = true)
data class JellyfinMediaSegmentsResponse(
    @Json(name = "Items") val items: List<JellyfinMediaSegment> = emptyList(),
    @Json(name = "TotalRecordCount") val totalRecordCount: Int = 0,
    @Json(name = "StartIndex") val startIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class JellyfinMediaSegment(
    @Json(name = "Id") val id: String? = null,
    @Json(name = "ItemId") val itemId: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "StartTicks") val startTicks: Long = 0,
    @Json(name = "EndTicks") val endTicks: Long = 0
)

data class JellyfinSyncProgress(
    val isSyncing: Boolean = false,
    val currentLibrary: String = "",
    val librariesTotal: Int = 0,
    val librariesDone: Int = 0,
    val itemsTotal: Int = 0,
    val itemsDone: Int = 0
)

data class JellyfinSyncResult(
    val librariesSynced: Int,
    val itemsAdded: Int,
    val itemsRemoved: Int,
    val errors: List<String>
)

data class JellyfinDiscoveredServer(
    val address: String,
    val name: String?,
    val id: String?,
    val endpointAddress: String?
)

object JellyfinUtils {
    private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

    /**
     * What the server calls a season when it could not read a number for one. Reused rather than
     * reworded so a container this side had to rename reads the same as one the server named itself.
     */
    private const val UNNUMBERED_SEASON_NAME = "Season Unknown"

    /**
     * The sort key used when the server did not send one. Matches the server's own convention of
     * dropping a leading article, so a locally derived key orders alongside server-sent keys instead
     * of forming a second alphabet.
     */
    fun createSortName(name: String): String {
        val lower = name.lowercase()
        val article = LEADING_ARTICLES.firstOrNull { lower.startsWith(it) }
        return if (article != null) lower.drop(article.length) else lower
    }

    /**
     * What one season is called.
     *
     * A season named after its own series is a release directory the scanner adopted rather than a
     * name for the season - "Freakazoid! (1995) - 1080P AI Upscale - LeWcID" is one - and it describes
     * the whole show, so it says nothing about which season it is. Its number still identifies it, so
     * it is named from that; one that has no number is named the way the server names the same thing.
     *
     * A name that does not repeat the series title is left exactly as it came, because a season may
     * legitimately be called something other than its number.
     */
    fun seasonName(rawName: String, seriesName: String?, seasonNumber: Int?): String {
        val isDirectoryName = !seriesName.isNullOrBlank() &&
            rawName.startsWith(seriesName, ignoreCase = true)
        if (!isDirectoryName) return rawName
        return seasonNumber?.let { "Season $it" } ?: UNNUMBERED_SEASON_NAME
    }
}
