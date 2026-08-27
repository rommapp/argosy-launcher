package com.nendo.argosy.data.download

/**
 * Why a queued download or an extraction retry did not finish.
 *
 * This is what [DownloadQueueDao.updateState] persists in place of the English sentence the
 * queue used to store: [DownloadFailureReasonCodec] turns each case into a stable token before
 * it reaches the database, so a locale change or a reworded string resource never invalidates a
 * row already on disk. [ServerError], [Unexpected] and [ExtractionFailed] keep their message as
 * plain text because that content is genuinely dynamic (a server response, an exception message);
 * everything else is a fixed condition the UI layer labels from a string resource.
 */
sealed class DownloadFailureReason {
    data class InvalidContentType(val contentType: String) : DownloadFailureReason()
    data object FileTooSmall : DownloadFailureReason()
    data class ServerError(val message: String) : DownloadFailureReason()
    data class Unexpected(val message: String?) : DownloadFailureReason()
    data class InsufficientUnpackSpace(
        val requiredBytes: Long,
        val availableBytes: Long,
        val location: StorageLocation
    ) : DownloadFailureReason()
    data object UnpackEscapedStagingFolder : DownloadFailureReason()
    data object StagedPathMissing : DownloadFailureReason()
    data class InsufficientDeploySpace(val requiredBytes: Long, val availableBytes: Long) : DownloadFailureReason()
    data object MoveToStorageFailed : DownloadFailureReason()
    data object MovedFileNotFound : DownloadFailureReason()
    data object DownloadedFileMissing : DownloadFailureReason()
    data object NoDownloadEntryFound : DownloadFailureReason()
    data object DownloadedFileNoLongerExists : DownloadFailureReason()
    data class ExtractionFailed(val message: String?) : DownloadFailureReason()

    /**
     * A row written before this reason existed, or one this codec otherwise failed to parse. The
     * text is shown as-is: it is already on a user's device and cannot be translated after the
     * fact, so tolerating it beats discarding it or leaving the row blank.
     */
    data class LegacyRaw(val message: String) : DownloadFailureReason()

    enum class StorageLocation { INTERNAL, ROM }
}

/**
 * Encodes a [DownloadFailureReason] as a stable string token for the `errorReason` column, and
 * decodes it back. The token before the first colon is what must never be renumbered or reused
 * for a different meaning; everything after it is that case's own dynamic data, kept intact by
 * always splitting with a bounded limit so a colon inside a server or exception message is never
 * mistaken for a field boundary.
 */
object DownloadFailureReasonCodec {
    private const val SEP = ":"

    private const val TOKEN_INVALID_CONTENT_TYPE = "INVALID_CONTENT_TYPE"
    private const val TOKEN_FILE_TOO_SMALL = "FILE_TOO_SMALL"
    private const val TOKEN_SERVER_ERROR = "SERVER_ERROR"
    private const val TOKEN_UNEXPECTED = "UNEXPECTED"
    private const val TOKEN_INSUFFICIENT_UNPACK_SPACE = "INSUFFICIENT_UNPACK_SPACE"
    private const val TOKEN_UNPACK_ESCAPED_STAGING = "UNPACK_ESCAPED_STAGING"
    private const val TOKEN_STAGED_PATH_MISSING = "STAGED_PATH_MISSING"
    private const val TOKEN_INSUFFICIENT_DEPLOY_SPACE = "INSUFFICIENT_DEPLOY_SPACE"
    private const val TOKEN_MOVE_TO_STORAGE_FAILED = "MOVE_TO_STORAGE_FAILED"
    private const val TOKEN_MOVED_FILE_NOT_FOUND = "MOVED_FILE_NOT_FOUND"
    private const val TOKEN_DOWNLOADED_FILE_MISSING = "DOWNLOADED_FILE_MISSING"
    private const val TOKEN_NO_DOWNLOAD_ENTRY = "NO_DOWNLOAD_ENTRY"
    private const val TOKEN_DOWNLOADED_FILE_NO_LONGER_EXISTS = "DOWNLOADED_FILE_NO_LONGER_EXISTS"
    private const val TOKEN_EXTRACTION_FAILED = "EXTRACTION_FAILED"

    fun encode(reason: DownloadFailureReason): String = when (reason) {
        is DownloadFailureReason.InvalidContentType ->
            "$TOKEN_INVALID_CONTENT_TYPE$SEP${reason.contentType}"
        DownloadFailureReason.FileTooSmall -> TOKEN_FILE_TOO_SMALL
        is DownloadFailureReason.ServerError ->
            "$TOKEN_SERVER_ERROR$SEP${reason.message}"
        is DownloadFailureReason.Unexpected ->
            "$TOKEN_UNEXPECTED$SEP${reason.message.orEmpty()}"
        is DownloadFailureReason.InsufficientUnpackSpace ->
            "$TOKEN_INSUFFICIENT_UNPACK_SPACE$SEP${reason.requiredBytes}$SEP${reason.availableBytes}$SEP${reason.location.name}"
        DownloadFailureReason.UnpackEscapedStagingFolder -> TOKEN_UNPACK_ESCAPED_STAGING
        DownloadFailureReason.StagedPathMissing -> TOKEN_STAGED_PATH_MISSING
        is DownloadFailureReason.InsufficientDeploySpace ->
            "$TOKEN_INSUFFICIENT_DEPLOY_SPACE$SEP${reason.requiredBytes}$SEP${reason.availableBytes}"
        DownloadFailureReason.MoveToStorageFailed -> TOKEN_MOVE_TO_STORAGE_FAILED
        DownloadFailureReason.MovedFileNotFound -> TOKEN_MOVED_FILE_NOT_FOUND
        DownloadFailureReason.DownloadedFileMissing -> TOKEN_DOWNLOADED_FILE_MISSING
        DownloadFailureReason.NoDownloadEntryFound -> TOKEN_NO_DOWNLOAD_ENTRY
        DownloadFailureReason.DownloadedFileNoLongerExists -> TOKEN_DOWNLOADED_FILE_NO_LONGER_EXISTS
        is DownloadFailureReason.ExtractionFailed ->
            "$TOKEN_EXTRACTION_FAILED$SEP${reason.message.orEmpty()}"
        is DownloadFailureReason.LegacyRaw -> reason.message
    }

    fun decode(raw: String?): DownloadFailureReason? {
        if (raw.isNullOrEmpty()) return null
        val token = raw.substringBefore(SEP)
        fun rest(limit: Int): List<String> = raw.split(SEP, limit = limit)
        return when (token) {
            TOKEN_INVALID_CONTENT_TYPE -> DownloadFailureReason.InvalidContentType(rest(2).getOrElse(1) { "" })
            TOKEN_FILE_TOO_SMALL -> DownloadFailureReason.FileTooSmall
            TOKEN_SERVER_ERROR -> DownloadFailureReason.ServerError(rest(2).getOrElse(1) { "" })
            TOKEN_UNEXPECTED -> DownloadFailureReason.Unexpected(rest(2).getOrNull(1)?.takeIf { it.isNotEmpty() })
            TOKEN_INSUFFICIENT_UNPACK_SPACE -> {
                val parts = rest(4)
                DownloadFailureReason.InsufficientUnpackSpace(
                    requiredBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                    availableBytes = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                    location = parts.getOrNull(3)
                        ?.let { runCatching { DownloadFailureReason.StorageLocation.valueOf(it) }.getOrNull() }
                        ?: DownloadFailureReason.StorageLocation.ROM
                )
            }
            TOKEN_UNPACK_ESCAPED_STAGING -> DownloadFailureReason.UnpackEscapedStagingFolder
            TOKEN_STAGED_PATH_MISSING -> DownloadFailureReason.StagedPathMissing
            TOKEN_INSUFFICIENT_DEPLOY_SPACE -> {
                val parts = rest(3)
                DownloadFailureReason.InsufficientDeploySpace(
                    requiredBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                    availableBytes = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                )
            }
            TOKEN_MOVE_TO_STORAGE_FAILED -> DownloadFailureReason.MoveToStorageFailed
            TOKEN_MOVED_FILE_NOT_FOUND -> DownloadFailureReason.MovedFileNotFound
            TOKEN_DOWNLOADED_FILE_MISSING -> DownloadFailureReason.DownloadedFileMissing
            TOKEN_NO_DOWNLOAD_ENTRY -> DownloadFailureReason.NoDownloadEntryFound
            TOKEN_DOWNLOADED_FILE_NO_LONGER_EXISTS -> DownloadFailureReason.DownloadedFileNoLongerExists
            TOKEN_EXTRACTION_FAILED ->
                DownloadFailureReason.ExtractionFailed(rest(2).getOrNull(1)?.takeIf { it.isNotEmpty() })
            else -> decodeLegacy(raw)
        }
    }

    /**
     * Rows written before failures were tokenised hold the English sentence the old build
     * produced. One of them is load-bearing: the retry path offers to resume an interrupted
     * extraction only when the stored reason is an extraction failure, and it used to decide
     * that by looking for this phrase. Recognising it here keeps that offer working for a
     * download that failed before the upgrade, instead of quietly dropping those rows to the
     * ordinary retry.
     */
    private fun decodeLegacy(raw: String): DownloadFailureReason =
        if (raw.contains(LEGACY_EXTRACTION_FAILED)) {
            DownloadFailureReason.ExtractionFailed(raw)
        } else {
            DownloadFailureReason.LegacyRaw(raw)
        }

    private const val LEGACY_EXTRACTION_FAILED = "Extraction failed"
}
