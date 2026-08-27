package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.MediaAudioLanguage
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode

/**
 * The picker label. These enums live in `data/preferences` and must not import `R`, and are
 * persisted by enum name (see `fromString`), so the label lives here beside the stored value
 * rather than on it.
 */
@get:StringRes
val MediaDownloadQuality.labelRes: Int
    get() = when (this) {
        MediaDownloadQuality.ORIGINAL -> R.string.media_pref_download_quality_original
        MediaDownloadQuality.HIGH -> R.string.media_pref_download_quality_high
        MediaDownloadQuality.MEDIUM -> R.string.media_pref_download_quality_medium
        MediaDownloadQuality.LOW -> R.string.media_pref_download_quality_low
    }

@get:StringRes
val MediaStreamingQuality.labelRes: Int
    get() = when (this) {
        MediaStreamingQuality.AUTO -> R.string.media_pref_streaming_quality_auto
        MediaStreamingQuality.HIGH -> R.string.media_pref_streaming_quality_high
        MediaStreamingQuality.MEDIUM -> R.string.media_pref_streaming_quality_medium
        MediaStreamingQuality.LOW -> R.string.media_pref_streaming_quality_low
    }

@get:StringRes
val MediaSubtitleMode.labelRes: Int
    get() = when (this) {
        MediaSubtitleMode.OFF -> R.string.media_pref_subtitle_mode_off
        MediaSubtitleMode.FORCED_ONLY -> R.string.media_pref_subtitle_mode_forced_only
        MediaSubtitleMode.PREFERRED -> R.string.media_pref_subtitle_mode_preferred
    }

@get:StringRes
val MediaAudioLanguage.labelRes: Int
    get() = when (this) {
        MediaAudioLanguage.ENGLISH -> R.string.media_pref_audio_language_english
        MediaAudioLanguage.JAPANESE -> R.string.media_pref_audio_language_japanese
        MediaAudioLanguage.SPANISH -> R.string.media_pref_audio_language_spanish
        MediaAudioLanguage.FRENCH -> R.string.media_pref_audio_language_french
        MediaAudioLanguage.GERMAN -> R.string.media_pref_audio_language_german
        MediaAudioLanguage.ITALIAN -> R.string.media_pref_audio_language_italian
        MediaAudioLanguage.PORTUGUESE -> R.string.media_pref_audio_language_portuguese
        MediaAudioLanguage.RUSSIAN -> R.string.media_pref_audio_language_russian
        MediaAudioLanguage.KOREAN -> R.string.media_pref_audio_language_korean
        MediaAudioLanguage.CHINESE -> R.string.media_pref_audio_language_chinese
    }

@get:StringRes
val MediaSubtitleLanguage.labelRes: Int
    get() = when (this) {
        MediaSubtitleLanguage.ENGLISH -> R.string.media_pref_subtitle_language_english
        MediaSubtitleLanguage.JAPANESE -> R.string.media_pref_subtitle_language_japanese
        MediaSubtitleLanguage.SPANISH -> R.string.media_pref_subtitle_language_spanish
        MediaSubtitleLanguage.FRENCH -> R.string.media_pref_subtitle_language_french
        MediaSubtitleLanguage.GERMAN -> R.string.media_pref_subtitle_language_german
        MediaSubtitleLanguage.ITALIAN -> R.string.media_pref_subtitle_language_italian
        MediaSubtitleLanguage.PORTUGUESE -> R.string.media_pref_subtitle_language_portuguese
        MediaSubtitleLanguage.RUSSIAN -> R.string.media_pref_subtitle_language_russian
        MediaSubtitleLanguage.KOREAN -> R.string.media_pref_subtitle_language_korean
        MediaSubtitleLanguage.CHINESE -> R.string.media_pref_subtitle_language_chinese
    }
