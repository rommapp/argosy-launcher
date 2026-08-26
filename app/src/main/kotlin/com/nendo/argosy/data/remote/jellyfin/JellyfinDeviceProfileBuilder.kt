package com.nendo.argosy.data.remote.jellyfin

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JellyfinDeviceProfileBuilder"
private const val KBPS_TO_BPS = 1000
private const val DEFAULT_MAX_STATIC_BITRATE_BPS = 100_000_000
private const val TRANSCODE_MIN_SEGMENTS = 1
private const val TRANSCODE_TARGET_VIDEO_CODEC = "h264"
private const val TRANSCODE_TARGET_AUDIO_CODEC = "aac"

/**
 * Codecs the transcode containers can carry without re-encoding. A codec named in a transcoding
 * profile is one the server is permitted to stream-copy into the output, so these lists are
 * intersected with what the device actually decodes and appended after the encode target - never
 * offered unconditionally. mpegts and mp4 both carry these; anything else is encoded to the target.
 */
private val TRANSCODE_COPYABLE_VIDEO_CODECS = listOf("h264", "hevc")
private val TRANSCODE_COPYABLE_AUDIO_CODECS = listOf("aac", "mp3", "ac3", "eac3")

/**
 * Codecs whose decoder ceiling is worth declaring. These two carry the libraries that reach a level
 * the hardware refuses; the rest are bounded by the containers they arrive in.
 */
private val DECODER_CEILING_CODECS = listOf("h264", "hevc")

/**
 * Containers this device can demux, which is a separate question from what it can decode.
 *
 * AVI is deliberately absent even though it is a large share of a typical library: Android's
 * extractor does not demux it, so declaring it produces a direct-play answer the client then cannot
 * open. Leaving it out costs a transcode; leaving it in costs a playback that fails after the
 * negotiation says it will work.
 */
private val DEMUXABLE_VIDEO_CONTAINERS = listOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "ts")
private val DEMUXABLE_AUDIO_CONTAINERS = listOf("mp3", "aac", "flac", "ogg", "wav", "m4a")

/**
 * Android decoder mime types mapped to the codec tokens the server matches against.
 *
 * The right-hand side is the server's own vocabulary - ffmpeg codec names - not Android's. The
 * server compares these as literal strings, so a token that is merely plausible never matches and
 * the profile entry silently does nothing.
 */
private val VIDEO_MIME_TO_CODEC = mapOf(
    "video/avc" to "h264",
    "video/hevc" to "hevc",
    "video/x-vnd.on2.vp8" to "vp8",
    "video/x-vnd.on2.vp9" to "vp9",
    "video/av01" to "av1",
    "video/mp4v-es" to "mpeg4",
    "video/mpeg2" to "mpeg2video",
    "video/3gpp" to "h263"
)

private val AUDIO_MIME_TO_CODEC = mapOf(
    "audio/mp4a-latm" to "aac",
    "audio/ac3" to "ac3",
    "audio/eac3" to "eac3",
    "audio/eac3-joc" to "eac3",
    "audio/mpeg" to "mp3",
    "audio/vorbis" to "vorbis",
    "audio/opus" to "opus",
    "audio/flac" to "flac",
    "audio/raw" to "pcm",
    "audio/vnd.dts" to "dts",
    "audio/vnd.dts.hd" to "dts",
    "audio/true-hd" to "truehd"
)

/**
 * Text subtitle formats the player renders itself, so the server can hand them over untouched.
 */
private val EXTERNAL_TEXT_SUBTITLE_FORMATS = listOf("vtt", "srt", "subrip", "ass", "ssa")

/**
 * Image-based subtitle formats. There is no way to render these without compositing them onto the
 * picture, which the server can only do by re-encoding the video.
 */
private val IMAGE_SUBTITLE_FORMATS = listOf("pgssub", "dvdsub", "dvbsub")

/**
 * Describes this device to the server, from what the device actually reports it can decode.
 *
 * Built from [MediaCodecList] at runtime rather than from a static table, because a static table is
 * a claim about hardware the app is not running on. Declaring a codec the SoC cannot decode does not
 * fail loudly - the server hands over a direct-play answer and the result is a video that plays with
 * silence, or a picture that never appears.
 *
 * The profile is rebuilt per negotiation rather than cached, since the bitrate ceiling and the
 * subtitle preference it is built from are user settings that can change between one playback and
 * the next.
 */
@Singleton
class JellyfinDeviceProfileBuilder @Inject constructor() {

    @Suppress("LongParameterList")
    fun build(
        maxStreamingBitrateKbps: Int? = null,
        maxHeight: Int? = null,
        maxFramerate: Int? = null,
        burnInImageSubtitles: Boolean = false,
        maxAudioChannels: Int = DEFAULT_MAX_AUDIO_CHANNELS
    ): JellyfinDeviceProfile {
        val decoders = readDecoders()
        val videoCodecs = decoders.videoCodecs.toList()
        val audioCodecs = decoders.audioCodecs.toList()
        val maxBitrateBps = maxStreamingBitrateKbps?.let { it * KBPS_TO_BPS }

        return JellyfinDeviceProfile(
            name = "Argosy",
            maxStreamingBitrate = maxBitrateBps,
            maxStaticBitrate = DEFAULT_MAX_STATIC_BITRATE_BPS,
            directPlayProfiles = buildDirectPlayProfiles(videoCodecs, audioCodecs),
            transcodingProfiles = buildTranscodingProfiles(videoCodecs, audioCodecs, maxAudioChannels),
            codecProfiles = buildCodecProfiles(decoders, maxHeight, maxFramerate),
            subtitleProfiles = buildSubtitleProfiles(burnInImageSubtitles)
        )
    }

    private fun buildDirectPlayProfiles(
        videoCodecs: List<String>,
        audioCodecs: List<String>
    ): List<JellyfinDirectPlayProfile> {
        if (videoCodecs.isEmpty() || audioCodecs.isEmpty()) return emptyList()
        val video = DEMUXABLE_VIDEO_CONTAINERS.map { container ->
            JellyfinDirectPlayProfile(
                container = container,
                type = PROFILE_TYPE_VIDEO,
                videoCodec = videoCodecs.joinToString(","),
                audioCodec = audioCodecs.joinToString(",")
            )
        }
        val audio = DEMUXABLE_AUDIO_CONTAINERS.map { container ->
            JellyfinDirectPlayProfile(
                container = container,
                type = PROFILE_TYPE_AUDIO,
                audioCodec = audioCodecs.joinToString(",")
            )
        }
        return video + audio
    }

    /**
     * What the server may produce when direct play fails - and, just as importantly, what it may
     * keep. Each codec list names the conservative encode target first, h264 video and aac audio,
     * so a stream that genuinely cannot be carried is re-encoded to the combination every device
     * plays. The device's own decodable codecs follow, filtered to what the container can hold,
     * because a codec listed here is one the server is allowed to stream-copy: an eac3 track on a
     * device without a Dolby decoder then costs an audio-only transcode while the h264 or hevc
     * picture is remuxed untouched, instead of a full re-encode of a video stream that was never
     * the problem.
     */
    private fun buildTranscodingProfiles(
        videoCodecs: List<String>,
        audioCodecs: List<String>,
        maxAudioChannels: Int
    ): List<JellyfinTranscodingProfile> {
        val video = copyableCodecList(
            TRANSCODE_TARGET_VIDEO_CODEC,
            videoCodecs,
            TRANSCODE_COPYABLE_VIDEO_CODECS
        )
        val audio = copyableCodecList(
            TRANSCODE_TARGET_AUDIO_CODEC,
            audioCodecs,
            TRANSCODE_COPYABLE_AUDIO_CODECS
        )
        return listOf(
            JellyfinTranscodingProfile(
                container = "ts",
                type = PROFILE_TYPE_VIDEO,
                videoCodec = video,
                audioCodec = audio,
                protocol = PROFILE_PROTOCOL_HLS,
                context = PROFILE_CONTEXT_STREAMING,
                maxAudioChannels = maxAudioChannels.toString(),
                minSegments = TRANSCODE_MIN_SEGMENTS,
                breakOnNonKeyFrames = true
            ),
            JellyfinTranscodingProfile(
                container = "mp4",
                type = PROFILE_TYPE_VIDEO,
                videoCodec = video,
                audioCodec = audio,
                protocol = PROFILE_PROTOCOL_HTTP,
                context = PROFILE_CONTEXT_STATIC,
                maxAudioChannels = maxAudioChannels.toString()
            ),
            JellyfinTranscodingProfile(
                container = "mp3",
                type = PROFILE_TYPE_AUDIO,
                audioCodec = "mp3",
                protocol = PROFILE_PROTOCOL_HTTP,
                context = PROFILE_CONTEXT_STREAMING
            )
        )
    }

    private fun copyableCodecList(
        target: String,
        decodable: List<String>,
        copyable: List<String>
    ): String =
        (listOf(target) + decodable.filter { it in copyable }).distinct().joinToString(",")

    /**
     * Ceilings within a codec the device does claim. A decoder that lists h264 still refuses a
     * stream above the level it was built for, and the server can only avoid handing one over if it
     * is told the number.
     *
     * [maxHeight] and [maxFramerate] are the user's own ceilings and ride on the same conditions,
     * which is the only place a resolution or frame-rate limit reaches the server: a condition the
     * source violates is what turns it into a transcode, and the ceiling then sizes that
     * transcode's output. They apply to every codec the device offers, not just the transcode
     * target - a limit written against h264 alone leaves an hevc copy of the same film
     * direct-playing at its full height.
     */
    private fun buildCodecProfiles(
        decoders: DecoderSupport,
        maxHeight: Int?,
        maxFramerate: Int?
    ): List<JellyfinCodecProfile> {
        val codecs = if (maxHeight == null && maxFramerate == null) {
            DECODER_CEILING_CODECS.toSet()
        } else {
            decoders.videoCodecs + TRANSCODE_TARGET_VIDEO_CODEC
        }
        return codecs.mapNotNull { codec ->
            val decoderMax = decoders.maxVideoSize[codec]?.takeIf { codec in DECODER_CEILING_CODECS }
            val conditions = videoSizeConditions(decoderMax, maxHeight, maxFramerate)
            if (conditions.isEmpty()) {
                null
            } else {
                JellyfinCodecProfile(
                    type = PROFILE_TYPE_VIDEO,
                    codec = codec,
                    conditions = conditions
                )
            }
        }
    }

    private fun videoSizeConditions(
        decoderMax: VideoSize?,
        maxHeight: Int?,
        maxFramerate: Int?
    ): List<JellyfinProfileCondition> {
        val height = listOfNotNull(decoderMax?.height, maxHeight).minOrNull()
        return buildList {
            decoderMax?.let { add(sizeCondition("Width", it.width)) }
            height?.let { add(sizeCondition("Height", it)) }
            maxFramerate?.let { add(sizeCondition("VideoFramerate", it)) }
        }
    }

    private fun sizeCondition(property: String, value: Int): JellyfinProfileCondition =
        JellyfinProfileCondition(
            condition = CONDITION_LESS_THAN_EQUAL,
            property = property,
            value = value.toString()
        )

    /**
     * Text subtitles are delivered as-is and drawn by the player. Image subtitles have no such path:
     * either the server burns them into the picture, which costs a full re-encode, or they are not
     * offered at all - which is why the choice is a user preference rather than a fixed answer.
     */
    private fun buildSubtitleProfiles(burnInImageSubtitles: Boolean): List<JellyfinSubtitleProfile> {
        val text = EXTERNAL_TEXT_SUBTITLE_FORMATS.map {
            JellyfinSubtitleProfile(format = it, method = SUBTITLE_METHOD_EXTERNAL)
        }
        val image = if (burnInImageSubtitles) burnInSubtitleProfiles() else emptyList()
        return text + image
    }

    /**
     * The only way an image subtitle reaches a viewer: the server draws it into the picture. Offered
     * on its own for the download profile, which declares nothing else - a saved file has no second
     * chance to fetch a track, so a picture subtitle either goes into the encode or does not exist.
     */
    fun burnInSubtitleProfiles(): List<JellyfinSubtitleProfile> =
        IMAGE_SUBTITLE_FORMATS.map {
            JellyfinSubtitleProfile(format = it, method = SUBTITLE_METHOD_ENCODE)
        }

    private data class VideoSize(val width: Int, val height: Int)

    private data class DecoderSupport(
        val videoCodecs: Set<String>,
        val audioCodecs: Set<String>,
        val maxVideoSize: Map<String, VideoSize>
    )

    private fun readDecoders(): DecoderSupport {
        val videoCodecs = linkedSetOf<String>()
        val audioCodecs = linkedSetOf<String>()
        val maxVideoSize = mutableMapOf<String, VideoSize>()

        val infos = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        }.getOrElse {
            Logger.warn(TAG, "codec list unavailable, falling back to transcode-only profile", it)
            emptyArray()
        }

        for (info in infos) {
            if (info.isEncoder) continue
            for (mime in info.supportedTypes) {
                val normalized = mime.lowercase()
                VIDEO_MIME_TO_CODEC[normalized]?.let { codec ->
                    videoCodecs += codec
                    recordMaxVideoSize(info, mime, codec, maxVideoSize)
                }
                AUDIO_MIME_TO_CODEC[normalized]?.let { audioCodecs += it }
            }
        }

        Logger.info(TAG, "decoders: video=$videoCodecs audio=$audioCodecs")
        return DecoderSupport(videoCodecs, audioCodecs, maxVideoSize)
    }

    private fun recordMaxVideoSize(
        info: MediaCodecInfo,
        mime: String,
        codec: String,
        into: MutableMap<String, VideoSize>
    ) {
        val capabilities = runCatching {
            info.getCapabilitiesForType(mime).videoCapabilities
        }.getOrNull() ?: return
        val width = capabilities.supportedWidths.upper
        val height = capabilities.supportedHeights.upper
        val existing = into[codec]
        if (existing == null || width * height > existing.width * existing.height) {
            into[codec] = VideoSize(width, height)
        }
    }

    companion object {
        /**
         * Ceiling on the audio channels a transcoding profile may carry, copied or encoded. Six
         * keeps a 5.1 track eligible for stream copy - the server refuses to copy a track whose
         * channel count exceeds this even when its codec is in the profile - and Android's
         * mandatory aac decoder handles 5.1, with the audio pipeline downmixing to whatever the
         * output device actually is. Direct play is not gated by this value at all.
         */
        const val DEFAULT_MAX_AUDIO_CHANNELS = 6
    }
}
