package com.nendo.argosy.util

/**
 * Folds a name supplied by a server or an archive into one a volume will accept.
 *
 * Android refuses these characters on emulated and FAT-family storage and fails the create with
 * EPERM rather than substituting anything, so a name has to be folded before it reaches the
 * filesystem. The set mirrors `android.os.FileUtils.buildValidFatFilename`, and matches the class
 * the local-file matchers already fold when they compare an on-disk name to a server one, so a
 * downloaded file still answers to the name RomM knows it by.
 */
object FileNames {

    val INVALID_CHARS = Regex("[\\\\:*?\"<>|/\\x00-\\x1f]")

    private const val REPLACEMENT = "_"

    /**
     * A single name as it can exist on disk. Path separators fold like any other refused
     * character, so the result never reaches outside its directory.
     */
    fun sanitize(name: String): String {
        val folded = INVALID_CHARS.replace(name, REPLACEMENT).trimEnd(' ', '.')
        return folded.ifBlank { REPLACEMENT }
    }

    /**
     * An archive entry path with every segment folded. Empty, `.` and `..` segments are dropped,
     * so a crafted entry cannot climb out of the directory it is being extracted into.
     */
    fun sanitizeRelativePath(path: String): String =
        path.split('/', '\\')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/") { sanitize(it) }
}
