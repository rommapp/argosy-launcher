package com.nendo.argosy.core.media

/**
 * The moving-picture files a tile can be pointed at on this device.
 *
 * GIF is here beside the video containers on purpose: a looping animation is exactly the sort of
 * thing someone reaches for when decorating a page, and refusing it would send them looking for a
 * converter to make a two-second loop into an mp4.
 */
object VideoFileTypes {
    val EXTENSIONS: Set<String> = setOf(
        "mp4",
        "m4v",
        "mkv",
        "webm",
        "mov",
        "avi",
        "gif"
    )

    fun isVideoFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS
}
