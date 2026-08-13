package com.nendo.argosy.data.remote.jellyfin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * What this device can play, in the server's own vocabulary.
 *
 * Every token here is a libretro-style upstream identifier: the server matches these strings
 * literally against its own probe of the file, so an invented or analogised codec name silently
 * makes a profile entry unmatchable. Names come from Jellyfin's own profile schema, never from
 * Android's mime strings, which is why [JellyfinDeviceProfileBuilder] translates rather than passes
 * through.
 */
@JsonClass(generateAdapter = true)
data class JellyfinDeviceProfile(
    @Json(name = "Name") val name: String? = null,
    @Json(name = "MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @Json(name = "MaxStaticBitrate") val maxStaticBitrate: Int? = null,
    @Json(name = "MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Int? = null,
    @Json(name = "DirectPlayProfiles") val directPlayProfiles: List<JellyfinDirectPlayProfile> = emptyList(),
    @Json(name = "TranscodingProfiles") val transcodingProfiles: List<JellyfinTranscodingProfile> = emptyList(),
    @Json(name = "ContainerProfiles") val containerProfiles: List<JellyfinContainerProfile> = emptyList(),
    @Json(name = "CodecProfiles") val codecProfiles: List<JellyfinCodecProfile> = emptyList(),
    @Json(name = "SubtitleProfiles") val subtitleProfiles: List<JellyfinSubtitleProfile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JellyfinDirectPlayProfile(
    @Json(name = "Container") val container: String,
    @Json(name = "Type") val type: String,
    @Json(name = "VideoCodec") val videoCodec: String? = null,
    @Json(name = "AudioCodec") val audioCodec: String? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinTranscodingProfile(
    @Json(name = "Container") val container: String,
    @Json(name = "Type") val type: String,
    @Json(name = "VideoCodec") val videoCodec: String? = null,
    @Json(name = "AudioCodec") val audioCodec: String? = null,
    @Json(name = "Protocol") val protocol: String? = null,
    @Json(name = "Context") val context: String = PROFILE_CONTEXT_STREAMING,
    @Json(name = "MaxAudioChannels") val maxAudioChannels: String? = null,
    @Json(name = "MinSegments") val minSegments: Int? = null,
    @Json(name = "BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean = false,
    @Json(name = "CopyTimestamps") val copyTimestamps: Boolean = false
)

@JsonClass(generateAdapter = true)
data class JellyfinContainerProfile(
    @Json(name = "Type") val type: String,
    @Json(name = "Container") val container: String? = null,
    @Json(name = "Conditions") val conditions: List<JellyfinProfileCondition> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JellyfinCodecProfile(
    @Json(name = "Type") val type: String,
    @Json(name = "Codec") val codec: String? = null,
    @Json(name = "Conditions") val conditions: List<JellyfinProfileCondition> = emptyList(),
    @Json(name = "ApplyConditions") val applyConditions: List<JellyfinProfileCondition> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JellyfinProfileCondition(
    @Json(name = "Condition") val condition: String,
    @Json(name = "Property") val property: String,
    @Json(name = "Value") val value: String,
    @Json(name = "IsRequired") val isRequired: Boolean = false
)

@JsonClass(generateAdapter = true)
data class JellyfinSubtitleProfile(
    @Json(name = "Format") val format: String,
    @Json(name = "Method") val method: String
)

const val PROFILE_TYPE_VIDEO = "Video"
const val PROFILE_TYPE_AUDIO = "Audio"

const val PROFILE_CONTEXT_STREAMING = "Streaming"
const val PROFILE_CONTEXT_STATIC = "Static"

const val PROFILE_PROTOCOL_HLS = "hls"
const val PROFILE_PROTOCOL_HTTP = "http"

const val SUBTITLE_METHOD_EXTERNAL = "External"
const val SUBTITLE_METHOD_EMBED = "Embed"
const val SUBTITLE_METHOD_ENCODE = "Encode"

const val CONDITION_LESS_THAN_EQUAL = "LessThanEqual"
const val CONDITION_NOT_EQUALS = "NotEquals"
const val CONDITION_EQUALS_ANY = "EqualsAny"
