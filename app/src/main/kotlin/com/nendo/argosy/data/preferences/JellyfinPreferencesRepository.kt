package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download payload for a media item: the source file, or a server transcode sized for the device.
 */
enum class MediaDownloadQuality(
    val displayName: String,
    val maxHeight: Int?,
    val maxBitrateKbps: Int?
) {
    ORIGINAL("Original File", null, null),
    HIGH("High - 1080p", 1080, 8000),
    MEDIUM("Medium - 720p", 720, 4000),
    LOW("Low - 480p", 480, 2000);

    companion object {
        fun fromString(value: String?): MediaDownloadQuality =
            entries.find { it.name == value } ?: ORIGINAL
    }
}

/**
 * Ceiling the client asks the server to stay under when it negotiates playback.
 */
enum class MediaStreamingBitrate(val displayName: String, val kbps: Int?) {
    AUTO("Auto", null),
    MBPS_2("2 Mbps", 2000),
    MBPS_4("4 Mbps", 4000),
    MBPS_8("8 Mbps", 8000),
    MBPS_10("10 Mbps", 10000),
    MBPS_20("20 Mbps", 20000),
    MBPS_40("40 Mbps", 40000);

    companion object {
        fun fromString(value: String?): MediaStreamingBitrate =
            entries.find { it.name == value } ?: AUTO
    }
}

enum class MediaSubtitleMode(val displayName: String) {
    OFF("Off"),
    FORCED_ONLY("Forced Only"),
    PREFERRED("Preferred Language");

    companion object {
        fun fromString(value: String?): MediaSubtitleMode =
            entries.find { it.name == value } ?: PREFERRED
    }
}

/**
 * Subtitle language the player prefers. A media stream carries an ISO 639 code whose form depends
 * on whoever muxed the file, so each entry lists every code it can arrive as and [matches] accepts
 * any of them.
 */
/**
 * Audio language the player reaches for when a title carries more than one track. A media stream
 * carries an ISO 639 code whose form depends on whoever muxed the file, so each entry lists every
 * code it can arrive as and [matches] accepts any of them.
 */
enum class MediaAudioLanguage(val displayName: String, val codes: List<String>) {
    ENGLISH("English", listOf("eng", "en")),
    JAPANESE("Japanese", listOf("jpn", "ja")),
    SPANISH("Spanish", listOf("spa", "es")),
    FRENCH("French", listOf("fra", "fre", "fr")),
    GERMAN("German", listOf("deu", "ger", "de")),
    ITALIAN("Italian", listOf("ita", "it")),
    PORTUGUESE("Portuguese", listOf("por", "pt")),
    RUSSIAN("Russian", listOf("rus", "ru")),
    KOREAN("Korean", listOf("kor", "ko")),
    CHINESE("Chinese", listOf("zho", "chi", "zh"));

    fun matches(language: String?): Boolean {
        val normalized = language?.trim()?.lowercase() ?: return false
        return codes.any { it == normalized }
    }

    companion object {
        fun fromString(value: String?): MediaAudioLanguage =
            entries.find { it.name == value } ?: ENGLISH
    }
}

enum class MediaSubtitleLanguage(val displayName: String, val codes: List<String>) {
    ENGLISH("English", listOf("eng", "en")),
    JAPANESE("Japanese", listOf("jpn", "ja")),
    SPANISH("Spanish", listOf("spa", "es")),
    FRENCH("French", listOf("fra", "fre", "fr")),
    GERMAN("German", listOf("deu", "ger", "de")),
    ITALIAN("Italian", listOf("ita", "it")),
    PORTUGUESE("Portuguese", listOf("por", "pt")),
    RUSSIAN("Russian", listOf("rus", "ru")),
    KOREAN("Korean", listOf("kor", "ko")),
    CHINESE("Chinese", listOf("zho", "chi", "zh"));

    fun matches(language: String?): Boolean {
        val normalized = language?.trim()?.lowercase() ?: return false
        return codes.any { it == normalized }
    }

    companion object {
        fun fromString(value: String?): MediaSubtitleLanguage =
            entries.find { it.name == value } ?: ENGLISH
    }
}

data class JellyfinPreferences(
    val serverUrl: String? = null,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val downloadQuality: MediaDownloadQuality = MediaDownloadQuality.ORIGINAL,
    val maxStreamingBitrate: MediaStreamingBitrate = MediaStreamingBitrate.AUTO,
    val audioLanguage: MediaAudioLanguage = MediaAudioLanguage.ENGLISH,
    val subtitleMode: MediaSubtitleMode = MediaSubtitleMode.PREFERRED,
    val subtitleLanguage: MediaSubtitleLanguage = MediaSubtitleLanguage.ENGLISH,
    val burnInImageSubtitles: Boolean = false,
    val shareMediaPresence: Boolean = true
) {
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank()
}

@Singleton
class JellyfinPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("jellyfin_server_url")
        val DEVICE_ID = stringPreferencesKey("jellyfin_device_id")
        val ACCESS_TOKEN = stringPreferencesKey("jellyfin_access_token")
        val USER_ID = stringPreferencesKey("jellyfin_user_id")
        val USER_NAME = stringPreferencesKey("jellyfin_user_name")
        val DOWNLOAD_QUALITY = stringPreferencesKey("jellyfin_download_quality")
        val MAX_STREAMING_BITRATE = stringPreferencesKey("jellyfin_max_streaming_bitrate")
        val AUDIO_LANGUAGE = stringPreferencesKey("jellyfin_audio_language")
        val SUBTITLE_MODE = stringPreferencesKey("jellyfin_subtitle_mode")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("jellyfin_subtitle_language")
        val BURN_IN_IMAGE_SUBTITLES = booleanPreferencesKey("jellyfin_burn_in_image_subtitles")
        val SHARE_MEDIA_PRESENCE = booleanPreferencesKey("jellyfin_share_media_presence")
    }

    val preferences: Flow<JellyfinPreferences> = dataStore.data.map { prefs ->
        JellyfinPreferences(
            serverUrl = prefs[Keys.SERVER_URL],
            deviceId = prefs[Keys.DEVICE_ID],
            accessToken = prefs[Keys.ACCESS_TOKEN],
            userId = prefs[Keys.USER_ID],
            userName = prefs[Keys.USER_NAME],
            downloadQuality = MediaDownloadQuality.fromString(prefs[Keys.DOWNLOAD_QUALITY]),
            maxStreamingBitrate = MediaStreamingBitrate.fromString(prefs[Keys.MAX_STREAMING_BITRATE]),
            audioLanguage = MediaAudioLanguage.fromString(prefs[Keys.AUDIO_LANGUAGE]),
            subtitleMode = MediaSubtitleMode.fromString(prefs[Keys.SUBTITLE_MODE]),
            subtitleLanguage = MediaSubtitleLanguage.fromString(prefs[Keys.SUBTITLE_LANGUAGE]),
            burnInImageSubtitles = prefs[Keys.BURN_IN_IMAGE_SUBTITLES] ?: false,
            shareMediaPresence = prefs[Keys.SHARE_MEDIA_PRESENCE] ?: true
        )
    }

    suspend fun setServerUrl(url: String?) {
        dataStore.edit { prefs ->
            val trimmed = url?.trim()?.trimEnd('/')
            if (trimmed.isNullOrBlank()) prefs.remove(Keys.SERVER_URL)
            else prefs[Keys.SERVER_URL] = trimmed
        }
    }

    suspend fun setDeviceId(deviceId: String) {
        dataStore.edit { it[Keys.DEVICE_ID] = deviceId }
    }

    suspend fun setCredentials(accessToken: String, userId: String, userName: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.USER_ID] = userId
            if (userName.isNullOrBlank()) prefs.remove(Keys.USER_NAME)
            else prefs[Keys.USER_NAME] = userName
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_NAME)
        }
    }

    suspend fun setDownloadQuality(quality: MediaDownloadQuality) {
        dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setMaxStreamingBitrate(bitrate: MediaStreamingBitrate) {
        dataStore.edit { it[Keys.MAX_STREAMING_BITRATE] = bitrate.name }
    }

    suspend fun setAudioLanguage(language: MediaAudioLanguage) {
        dataStore.edit { it[Keys.AUDIO_LANGUAGE] = language.name }
    }

    suspend fun setSubtitleMode(mode: MediaSubtitleMode) {
        dataStore.edit { it[Keys.SUBTITLE_MODE] = mode.name }
    }

    suspend fun setSubtitleLanguage(language: MediaSubtitleLanguage) {
        dataStore.edit { it[Keys.SUBTITLE_LANGUAGE] = language.name }
    }

    suspend fun setBurnInImageSubtitles(enabled: Boolean) {
        dataStore.edit { it[Keys.BURN_IN_IMAGE_SUBTITLES] = enabled }
    }

    suspend fun setShareMediaPresence(enabled: Boolean) {
        dataStore.edit { it[Keys.SHARE_MEDIA_PRESENCE] = enabled }
    }
}
