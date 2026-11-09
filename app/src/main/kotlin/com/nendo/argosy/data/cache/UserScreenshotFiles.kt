package com.nendo.argosy.data.cache

internal const val USER_SCREENSHOT_FILE_PREFIX = "uss_"
private const val DEFAULT_USER_SCREENSHOT_EXTENSION = "png"
private const val MAX_EXTENSION_LENGTH = 5

private val EXTENSION_BY_CONTENT_TYPE = mapOf(
    "image/png" to "png",
    "image/jpeg" to "jpg",
    "image/jpg" to "jpg",
    "image/webp" to "webp",
    "image/gif" to "gif",
    "image/bmp" to "bmp"
)

fun userScreenshotIdPrefix(rommId: Long, screenshotId: Long): String =
    "$USER_SCREENSHOT_FILE_PREFIX${rommId}_${screenshotId}_"

/**
 * File name without extension for RomM gallery screenshot [screenshotId] at [version], the
 * screenshot's `updated_at`. A cached file whose name starts with this stem and a dot holds
 * that version's bytes.
 */
fun userScreenshotFileStem(rommId: Long, screenshotId: Long, version: String): String =
    "${userScreenshotIdPrefix(rommId, screenshotId)}${artUrlHash(version)}"

fun isUserScreenshotFileCurrent(fileName: String, rommId: Long, screenshotId: Long, version: String): Boolean =
    fileName.startsWith("${userScreenshotFileStem(rommId, screenshotId, version)}.")

/**
 * The extension a downloaded gallery screenshot is stored under: RomM's `file_extension` when it
 * is a plain short token, else the one [contentType] names, else png.
 */
fun userScreenshotExtension(fileExtension: String?, contentType: String?): String {
    val declared = fileExtension?.trim()?.trimStart('.')?.lowercase()
        ?.takeIf { ext -> ext.length in 1..MAX_EXTENSION_LENGTH && ext.all { it in 'a'..'z' || it in '0'..'9' } }
    if (declared != null) return declared
    val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
    return EXTENSION_BY_CONTENT_TYPE[mime] ?: DEFAULT_USER_SCREENSHOT_EXTENSION
}
