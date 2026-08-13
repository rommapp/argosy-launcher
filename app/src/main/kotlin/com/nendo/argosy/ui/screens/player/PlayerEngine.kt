package com.nendo.argosy.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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
 * cache keyed on the URL, and the trickplay and subtitle addresses are built to be cacheable.
 */
@OptIn(UnstableApi::class)
class PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: JellyfinConnectionManager,
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository
) {

    suspend fun authorizationHeader(): String = withContext(Dispatchers.IO) {
        val prefs = jellyfinPreferencesRepository.preferences.first()
        JellyfinApiFactory.buildAuthorizationHeader(
            deviceId = connectionManager.getDeviceId() ?: prefs.deviceId.orEmpty(),
            deviceName = connectionManager.getDeviceName(),
            token = prefs.accessToken
        )
    }

    fun createPlayer(authorizationHeader: String, listener: Player.Listener): ExoPlayer {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory(authorizationHeader))
        return ExoPlayer.Builder(context)
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
     * player only ever sees the audio ones. The subtitle is addressed by the id it was attached
     * with, which survives whatever order the tracks come back in.
     */
    fun applySelections(player: ExoPlayer, audioOrdinal: Int?, subtitleStreamIndex: Int?) {
        val tracks = player.currentTracks
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        audioGroup(tracks, audioOrdinal)?.let {
            builder.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
        }

        val subtitleGroup = subtitleStreamIndex?.let { subtitleGroup(tracks, it) }
        if (subtitleGroup != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(TrackSelectionOverride(subtitleGroup.mediaTrackGroup, 0))
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }

        player.trackSelectionParameters = builder.build()
    }

    private fun audioGroup(tracks: Tracks, ordinal: Int?): Tracks.Group? {
        if (ordinal == null) return null
        return tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(ordinal)
    }

    private fun subtitleGroup(tracks: Tracks, streamIndex: Int): Tracks.Group? {
        val id = sideloadedSubtitleId(streamIndex)
        return tracks.groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_TEXT &&
                (0 until group.length).any { group.getTrackFormat(it).id == id }
        }
    }

    private fun dataSourceFactory(authorizationHeader: String): DataSource.Factory {
        val client = OkHttpClient.Builder()
            .connectTimeout(STREAM_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(true)
            .build()
        val upstream = OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(mapOf("Authorization" to authorizationHeader))
        return DefaultDataSource.Factory(context, upstream)
    }
}
