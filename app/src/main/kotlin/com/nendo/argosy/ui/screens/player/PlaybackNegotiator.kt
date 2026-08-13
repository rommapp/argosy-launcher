package com.nendo.argosy.ui.screens.player

import androidx.media3.common.MimeTypes
import com.nendo.argosy.data.preferences.JellyfinPreferences
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinDeviceProfileBuilder
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaSource
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaStream
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackInfoRequest
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.PLAY_METHOD_DIRECT_PLAY
import com.nendo.argosy.data.remote.jellyfin.PLAY_METHOD_TRANSCODE
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "PlaybackNegotiator"
private const val KBPS_TO_BPS = 1000
private const val STREAM_TYPE_AUDIO = "Audio"
private const val STREAM_TYPE_SUBTITLE = "Subtitle"
private const val SUBTITLE_FORMAT_VTT = "vtt"

/**
 * Turns one item id into one playable address.
 *
 * Every playback goes through here, and the answer is thrown away when that playback ends. The
 * server decides direct play against a device profile built from this device's own decoders, and
 * that decision depends on the current network, the user's bitrate ceiling and the transcode
 * sessions already running - none of which hold still between one playback and the next. The
 * addresses it returns also expire with the transcode session behind them, so a cached one plays
 * for a while and then stops mid-film.
 */
class PlaybackNegotiator @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val profileBuilder: JellyfinDeviceProfileBuilder,
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository
) {

    suspend fun readPreferences(): JellyfinPreferences =
        withContext(Dispatchers.IO) { jellyfinPreferencesRepository.preferences.first() }

    /**
     * [burnInImageSubtitles] is passed in rather than read here because it is a decision about this
     * playback, not a standing setting. The stored preference only seeds the first negotiation; a
     * viewer who turns burn-in on for one film with unreadable subtitles has not asked for every
     * subsequent film to be re-encoded.
     */
    @Suppress("LongParameterList")
    suspend fun negotiate(
        itemId: String,
        startPositionMs: Long,
        burnInImageSubtitles: Boolean,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        mediaSourceId: String? = null
    ): PlaybackNegotiation = withContext(Dispatchers.IO) {
        val prefs = jellyfinPreferencesRepository.preferences.first()
        val userId = apiClient.currentUserId()
            ?: return@withContext PlaybackNegotiation.Failed("Not signed in to Jellyfin")

        val bitrateKbps = prefs.maxStreamingBitrate.kbps
        val profile = profileBuilder.build(
            maxStreamingBitrateKbps = bitrateKbps,
            burnInImageSubtitles = burnInImageSubtitles
        )

        val request = JellyfinPlaybackInfoRequest(
            userId = userId,
            maxStreamingBitrate = bitrateKbps?.let { it * KBPS_TO_BPS },
            startTimeTicks = startPositionMs * TICKS_PER_MILLISECOND,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId,
            deviceProfile = profile
        )

        when (val result = apiClient.getPlaybackInfo(itemId, request)) {
            is JellyfinResult.Error -> PlaybackNegotiation.Failed(result.message)
            is JellyfinResult.Success -> {
                val source = pickSource(result.data.mediaSources, mediaSourceId)
                    ?: return@withContext PlaybackNegotiation.Failed("The server offered no playable version")
                resolve(
                    itemId = itemId,
                    source = source,
                    playSessionId = result.data.playSessionId,
                    requestedAudio = audioStreamIndex,
                    requestedSubtitle = subtitleStreamIndex,
                    prefs = prefs
                )
            }
        }
    }

    /**
     * Alternate versions of one item arrive as several media sources. V1 plays the one the caller
     * already chose, or the first, rather than prompting.
     */
    private fun pickSource(
        sources: List<JellyfinMediaSource>,
        requestedId: String?
    ): JellyfinMediaSource? =
        sources.firstOrNull { it.id == requestedId } ?: sources.firstOrNull()

    /**
     * A remux and a full re-encode both leave an encoder running server-side, and the server tells
     * them apart only by degree. Both are therefore reported as a transcode, because the report is
     * what drives the encoder shutdown at the end of the session; calling a remux direct play would
     * skip that shutdown and leave the process alive until the server's own timeout.
     */
    @Suppress("LongParameterList")
    private fun resolve(
        itemId: String,
        source: JellyfinMediaSource,
        playSessionId: String?,
        requestedAudio: Int?,
        requestedSubtitle: Int?,
        prefs: JellyfinPreferences
    ): PlaybackNegotiation {
        val transcodeUrl = apiClient.buildStreamUrl(itemId, source.transcodingUrl)
        val isTranscode = transcodeUrl != null
        val streamUrl = transcodeUrl ?: directPlayUrl(itemId, source)
        if (!isTranscode && !source.supportsDirectPlay && !source.supportsDirectStream) {
            return PlaybackNegotiation.Failed("This title cannot be played on this device")
        }

        val audioStreams = source.mediaStreams.filter { it.type == STREAM_TYPE_AUDIO }
        val subtitleStreams = source.mediaStreams.filter { it.type == STREAM_TYPE_SUBTITLE }

        val audioTracks = audioStreams.mapIndexed { ordinal, stream ->
            stream.toTrack(ordinal, isSubtitle = false)
        }
        val subtitleTracks = subtitleStreams.mapIndexed { ordinal, stream ->
            stream.toTrack(ordinal, isSubtitle = true)
        }

        val selectedAudio = requestedAudio ?: autoAudio(audioStreams, source.defaultAudioStreamIndex, prefs)

        val selectedSubtitle = requestedSubtitle ?: autoSubtitle(
            subtitleStreams = subtitleStreams,
            defaultIndex = source.defaultSubtitleStreamIndex,
            mode = prefs.subtitleMode,
            prefs = prefs
        )

        val sideloaded = subtitleStreams.mapNotNull { stream ->
            val delivery = subtitleDelivery(stream) ?: return@mapNotNull null
            SideloadedSubtitle(
                streamIndex = stream.index,
                url = apiClient.buildSubtitleUrl(
                    itemId = itemId,
                    mediaSourceId = source.id,
                    streamIndex = stream.index,
                    format = delivery.format
                ),
                mimeType = delivery.mimeType,
                language = stream.language
            )
        }

        Logger.info(TAG, "negotiated $itemId method=${if (isTranscode) "transcode" else "direct"}")

        return PlaybackNegotiation.Ready(
            NegotiatedPlayback(
                itemId = itemId,
                mediaSourceId = source.id,
                playSessionId = playSessionId,
                streamUrl = streamUrl,
                playMethod = if (isTranscode) PLAY_METHOD_TRANSCODE else PLAY_METHOD_DIRECT_PLAY,
                isTranscode = isTranscode,
                isHls = isHlsDelivery(source, streamUrl),
                runtimeMs = (source.runTimeTicks ?: 0L) / TICKS_PER_MILLISECOND,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                audioStreamIndex = selectedAudio,
                subtitleStreamIndex = selectedSubtitle,
                sideloadedSubtitles = sideloaded
            )
        )
    }

    /**
     * The address of the source file itself. `static=true` is what tells the server to hand the
     * bytes over untouched rather than open an encoder; authorization rides in the request header
     * the data source attaches, so no token is written into the address.
     */
    private fun directPlayUrl(itemId: String, source: JellyfinMediaSource): String {
        val params = apiClient.buildOriginalFileParams(source.id)
            .map { (key, value) -> "$key=$value" }
            .joinToString("&")
        return "${apiClient.baseUrl}/Videos/$itemId/stream?$params"
    }

    /**
     * Whether the answer arrives as a playlist rather than a single file, which decides the media
     * source factory the player builds.
     */
    private fun isHlsDelivery(source: JellyfinMediaSource, streamUrl: String): Boolean =
        source.transcodingSubProtocol.equals("hls", ignoreCase = true) ||
            streamUrl.contains(".m3u8", ignoreCase = true)

    /**
     * Which audio track comes up without the user asking.
     *
     * The preferred language is tried before the server's own default, because the default is the
     * flag whoever muxed the file happened to set and the preference is what this viewer said they
     * wanted. Language matching goes through the preference's own list of ISO 639 spellings: the
     * same language arrives as `fra`, `fre` or `fr` depending on the muxer, and matching one
     * spelling silently misses the other two.
     */
    private fun autoAudio(
        audioStreams: List<JellyfinMediaStream>,
        defaultIndex: Int?,
        prefs: JellyfinPreferences
    ): Int? {
        if (audioStreams.size > 1) {
            audioStreams.firstOrNull { prefs.audioLanguage.matches(it.language) }
                ?.let { return it.index }
        }
        return defaultIndex
            ?: audioStreams.firstOrNull { it.isDefault }?.index
            ?: audioStreams.firstOrNull()?.index
    }

    /**
     * Which subtitle comes up without the user asking.
     *
     * Only text subtitles are auto-selected. An image subtitle can only be shown by having the
     * server composite it onto the picture, which means a second negotiation and a full re-encode -
     * a battery event on a handheld that nobody asked for. The user can still choose one, and that
     * choice pays for the reload knowingly.
     */
    private fun autoSubtitle(
        subtitleStreams: List<JellyfinMediaStream>,
        defaultIndex: Int?,
        mode: MediaSubtitleMode,
        prefs: JellyfinPreferences
    ): Int? {
        if (mode == MediaSubtitleMode.OFF) return null
        val textStreams = subtitleStreams.filter { subtitleDelivery(it) != null }
        if (textStreams.isEmpty()) return null
        if (mode == MediaSubtitleMode.FORCED_ONLY) {
            return textStreams.firstOrNull { it.isForced }?.index
        }
        return textStreams.firstOrNull { prefs.subtitleLanguage.matches(it.language) }?.index
            ?: textStreams.firstOrNull { it.index == defaultIndex }?.index
    }

    private data class SubtitleDelivery(val format: String, val mimeType: String)

    /**
     * How a subtitle track is asked for and what the player should parse it as. ASS keeps its own
     * format rather than being converted, because conversion to WebVTT discards the positioning and
     * styling that a quarter of this kind of library depends on.
     */
    private fun subtitleDelivery(stream: JellyfinMediaStream): SubtitleDelivery? =
        when (stream.codec?.lowercase()) {
            "ass", "ssa" -> SubtitleDelivery("ass", MimeTypes.TEXT_SSA)
            "subrip", "srt" -> SubtitleDelivery("srt", MimeTypes.APPLICATION_SUBRIP)
            "vtt", "webvtt" -> SubtitleDelivery(SUBTITLE_FORMAT_VTT, MimeTypes.TEXT_VTT)
            "mov_text", "text", "subtitle" -> SubtitleDelivery(SUBTITLE_FORMAT_VTT, MimeTypes.TEXT_VTT)
            else -> if (stream.isTextSubtitleStream) {
                SubtitleDelivery(SUBTITLE_FORMAT_VTT, MimeTypes.TEXT_VTT)
            } else {
                null
            }
        }

    private fun JellyfinMediaStream.toTrack(ordinal: Int, isSubtitle: Boolean): PlayerTrack =
        PlayerTrack(
            streamIndex = index,
            ordinal = ordinal,
            label = displayTitle
                ?: title
                ?: listOfNotNull(language, codec).joinToString(" ").ifBlank { "Track ${ordinal + 1}" },
            language = language,
            isTextSubtitle = isSubtitle && subtitleDelivery(this) != null,
            isDefault = isDefault
        )
}
