package com.nendo.argosy.data.media

import androidx.media3.common.MimeTypes
import com.nendo.argosy.data.storage.FileAccessLayer

private const val FORMAT_ASS = "ass"
private const val FORMAT_SRT = "srt"
private const val FORMAT_VTT = "vtt"
private const val UNKNOWN_LANGUAGE = "und"
private const val SIDECAR_NAME_PARTS = 2

/**
 * How one subtitle track is asked for and what the player parses it as.
 *
 * ASS keeps its own format rather than being converted, because conversion to WebVTT discards the
 * positioning and styling a quarter of this kind of library depends on. A track with no text form at
 * all - a picture subtitle - has no delivery and answers null.
 */
data class MediaSubtitleDelivery(val format: String, val mimeType: String)

/**
 * The single answer to whether a subtitle track can be handed over as text, shared by the streaming
 * negotiation and the download path. Two answers to this question would let a track download as a
 * file the player then refuses to parse.
 */
fun subtitleDeliveryFor(codec: String?, isTextSubtitleStream: Boolean): MediaSubtitleDelivery? =
    when (codec?.lowercase()) {
        "ass", "ssa" -> MediaSubtitleDelivery(FORMAT_ASS, MimeTypes.TEXT_SSA)
        "subrip", "srt" -> MediaSubtitleDelivery(FORMAT_SRT, MimeTypes.APPLICATION_SUBRIP)
        "vtt", "webvtt" -> MediaSubtitleDelivery(FORMAT_VTT, MimeTypes.TEXT_VTT)
        "mov_text", "text", "subtitle" -> MediaSubtitleDelivery(FORMAT_VTT, MimeTypes.TEXT_VTT)
        else -> if (isTextSubtitleStream) {
            MediaSubtitleDelivery(FORMAT_VTT, MimeTypes.TEXT_VTT)
        } else {
            null
        }
    }

/**
 * One subtitle file stored beside a downloaded video.
 */
data class MediaSubtitleSidecar(
    val path: String,
    val streamIndex: Int,
    val language: String?,
    val delivery: MediaSubtitleDelivery
)

/**
 * Subtitle files kept next to a downloaded video.
 *
 * A device-sized download is an encode the server produces with one video stream and one audio
 * stream, so no subtitle survives inside it. Every text track is therefore fetched as its own file
 * and stored beside the video, named after it.
 *
 * The name is the whole record: there is no row anywhere that lists these files. Deriving them from
 * the video's own path is what makes them findable by the player, by the delete path and by the
 * storage walk, and is what carries them through a media-folder move - the tree moves whole and the
 * rows are repointed, so a sidecar is still beside its video afterwards.
 */
object MediaSubtitleSidecars {

    private val FORMATS = setOf(FORMAT_ASS, FORMAT_SRT, FORMAT_VTT)

    /**
     * Where one track's file belongs. The stream index is part of the name because a title can carry
     * several tracks in one language, and a name that only spelled the language would have them
     * overwrite one another.
     */
    fun pathFor(videoPath: String, streamIndex: Int, language: String?, format: String): String =
        "${videoPath.substringBeforeLast('.')}.${languageTag(language)}.$streamIndex.$format"

    /**
     * Every sidecar belonging to one video. Read from the directory rather than from a record,
     * because the directory is the record.
     */
    fun listFor(videoPath: String, fileAccessLayer: FileAccessLayer): List<MediaSubtitleSidecar> {
        val parent = videoPath.substringBeforeLast('/', "")
        if (parent.isEmpty()) return emptyList()
        val prefix = videoPath.substringAfterLast('/').substringBeforeLast('.') + "."
        val entries = fileAccessLayer.listFiles(parent) ?: return emptyList()
        return entries.mapNotNull { entry ->
            if (!entry.isFile || !entry.name.startsWith(prefix)) return@mapNotNull null
            parse(entry.path, entry.name.removePrefix(prefix))
        }
    }

    fun deleteAllFor(videoPath: String, fileAccessLayer: FileAccessLayer) {
        listFor(videoPath, fileAccessLayer).forEach { fileAccessLayer.delete(it.path) }
    }

    fun bytesFor(videoPath: String, fileAccessLayer: FileAccessLayer): Long =
        listFor(videoPath, fileAccessLayer).sumOf { fileAccessLayer.length(it.path) }

    private fun parse(path: String, suffix: String): MediaSubtitleSidecar? {
        val format = suffix.substringAfterLast('.', "")
        if (format !in FORMATS) return null
        val parts = suffix.removeSuffix(".$format").split('.')
        if (parts.size != SIDECAR_NAME_PARTS) return null
        val streamIndex = parts[1].toIntOrNull() ?: return null
        val delivery = subtitleDeliveryFor(format, isTextSubtitleStream = true) ?: return null
        return MediaSubtitleSidecar(
            path = path,
            streamIndex = streamIndex,
            language = parts[0].takeIf { it != UNKNOWN_LANGUAGE },
            delivery = delivery
        )
    }

    private fun languageTag(language: String?): String =
        language?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }
            ?: UNKNOWN_LANGUAGE
}
