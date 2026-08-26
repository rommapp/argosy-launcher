package com.nendo.argosy.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.nendo.argosy.util.Logger
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiFactory
import com.nendo.argosy.data.remote.jellyfin.JellyfinConnectionManager
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "PlayerEngine"
private const val STREAM_READ_TIMEOUT_SECONDS = 60L
private const val STREAM_CONNECT_TIMEOUT_SECONDS = 30L

/**
 * Identifies a subtitle attached alongside the video rather than carried inside it. The player has
 * no other way to tell one of these apart from a track the container already had, and picking the
 * wrong one silently shows the wrong language.
 */
fun sideloadedSubtitleId(streamIndex: Int): String = "argosy-sub-$streamIndex"

/**
 * Builds the video player and the pipe that feeds it.
 *
 * Authorization rides in a request header rather than in the address. The server accepts a token in
 * either place, but an address carrying a credential ends up in logs, in crash reports and in any
 * cache keyed on the URL, and the trickplay and subtitle addresses are built to be cacheable. A
 * downloaded file needs none of it, which is why the header is optional here.
 */
@OptIn(UnstableApi::class)
class PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: JellyfinConnectionManager,
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository,
    private val displayAffinityHelper: DisplayAffinityHelper
) {

    suspend fun authorizationHeader(): String = withContext(Dispatchers.IO) {
        val prefs = jellyfinPreferencesRepository.preferences.first()
        JellyfinApiFactory.buildAuthorizationHeader(
            deviceId = connectionManager.getDeviceId() ?: prefs.deviceId.orEmpty(),
            deviceName = connectionManager.getDeviceName(),
            token = prefs.accessToken
        )
    }

    /**
     * Builds the player from a context tied to [displayId] when the device has a second display.
     * Firmwares that keep a volume per display bind a playback's audio when its track is created,
     * so the association has to exist before prepare runs - the application context belongs to no
     * display and would leave the binding to whichever screen happened to hold focus. A player
     * built this way is display-bound for its whole life; a window that changes display needs a
     * new player. Single-screen devices always get the application context, unchanged.
     */
    fun createPlayer(
        authorizationHeader: String?,
        listener: Player.Listener,
        displayId: Int?
    ): ExoPlayer {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory(authorizationHeader))
        return ExoPlayer.Builder(playbackContext(displayId))
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                setWakeMode(C.WAKE_MODE_NETWORK)
                addListener(listener)
            }
    }

    private fun playbackContext(displayId: Int?): Context {
        if (displayId == null) return context
        if (!displayAffinityHelper.hasSecondaryDisplay) return context
        return displayAffinityHelper.displayContext(displayId) ?: context
    }

    /**
     * Every text subtitle the item has is attached, not just the selected one. A sidecar is only
     * fetched once its track is selected, so attaching all of them costs nothing up front and turns
     * a subtitle change into a track selection instead of a reload - which on a transcoded stream
     * would mean tearing down the encoder and starting it again.
     */
    fun buildMediaItem(playback: NegotiatedPlayback): MediaItem {
        val subtitles = playback.sideloadedSubtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setId(sideloadedSubtitleId(subtitle.streamIndex))
                .build()
        }
        return MediaItem.Builder()
            .setUri(playback.streamUrl)
            .apply { if (playback.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    /**
     * Points the player at one audio track and at most one subtitle track.
     *
     * The audio track is addressed by its position among the audio tracks rather than by the
     * server's stream index, because the server numbers every stream in the container while the
     * player only ever sees the audio ones.
     *
     * Subtitles are addressed two ways because they arrive two ways. A negotiated stream carries its
     * text tracks as separate files, each attached under an id of ours, and that id survives whatever
     * order they come back in. A file played from disk has them inside the container instead, where
     * there is no id to match on and the position among the text tracks is the only handle - which is
     * exactly the handle [containerTracks] hands out.
     */
    fun applySelections(
        player: ExoPlayer,
        audioOrdinal: Int?,
        subtitleKey: Int?,
        subtitlesAreEmbedded: Boolean
    ) {
        val tracks = player.currentTracks
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        audioGroup(tracks, audioOrdinal)?.let {
            builder.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
        }

        val subtitleGroup = subtitleKey?.let { subtitleGroup(tracks, it, subtitlesAreEmbedded) }
        if (subtitleKey != null && subtitleGroup == null) {
            Logger.warn(
                TAG,
                "no text track matched key=$subtitleKey embedded=$subtitlesAreEmbedded; ids=" +
                    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.joinToString(",") { group ->
                        (0 until group.length).joinToString(",") { group.getTrackFormat(it).id.toString() }
                    }
            )
        }
        if (subtitleGroup != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(TrackSelectionOverride(subtitleGroup.mediaTrackGroup, 0))
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }

        player.trackSelectionParameters = builder.build()
    }

    /**
     * What the open container turned out to hold, with the player's own opening choice alongside it.
     *
     * This is how a downloaded file gets a track list at all: no negotiation described it, so the
     * extractor's findings are the description. Tracks are keyed by their position within their kind,
     * which is the same key [applySelections] selects an embedded track by.
     */
    fun containerTracks(player: ExoPlayer): ContainerTracks {
        val groups = player.currentTracks.groups
        val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        return ContainerTracks(
            audio = audio.mapIndexed { ordinal, group -> group.toPlayerTrack(ordinal, isSubtitle = false) },
            subtitles = text.mapIndexed { ordinal, group -> group.toPlayerTrack(ordinal, isSubtitle = true) },
            selectedAudioOrdinal = audio.indexOfFirst { it.isSelected }.takeIf { it >= 0 },
            selectedSubtitleOrdinal = text.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
        )
    }

    private fun Tracks.Group.toPlayerTrack(ordinal: Int, isSubtitle: Boolean): PlayerTrack {
        val format = mediaTrackGroup.getFormat(0)
        val fallback = listOfNotNull(format.language, format.sampleMimeType)
            .joinToString(" ")
            .ifBlank { "Track ${ordinal + 1}" }
        return PlayerTrack(
            streamIndex = ordinal,
            ordinal = ordinal,
            label = format.label?.takeIf { it.isNotBlank() } ?: fallback,
            language = format.language,
            isTextSubtitle = isSubtitle,
            isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
        )
    }

    private fun audioGroup(tracks: Tracks, ordinal: Int?): Tracks.Group? {
        if (ordinal == null) return null
        return tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(ordinal)
    }

    private fun subtitleGroup(tracks: Tracks, key: Int, embedded: Boolean): Tracks.Group? {
        if (embedded) {
            return tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.getOrNull(key)
        }
        val id = sideloadedSubtitleId(key)
        return tracks.groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_TEXT &&
                (0 until group.length).any { group.getTrackFormat(it).id.matchesSideloadedId(id) }
        }
    }

    /**
     * Whether a track format carries the id we attached the sidecar under.
     *
     * The player qualifies a sideloaded track's id with the index of the source it was merged in
     * from, so what comes back is "1:our-id" rather than the id we set. Matching the tail is what
     * survives that, and anchoring on the colon keeps one id from matching another that merely ends
     * the same way.
     */
    private fun String?.matchesSideloadedId(id: String): Boolean =
        this == id || this?.endsWith(":$id") == true

    private fun dataSourceFactory(authorizationHeader: String?): DataSource.Factory {
        val client = OkHttpClient.Builder()
            .connectTimeout(STREAM_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(true)
            .build()
        val upstream = OkHttpDataSource.Factory(client).apply {
            if (authorizationHeader != null) {
                setDefaultRequestProperties(mapOf("Authorization" to authorizationHeader))
            }
        }
        return DefaultDataSource.Factory(context, upstream)
    }
}
