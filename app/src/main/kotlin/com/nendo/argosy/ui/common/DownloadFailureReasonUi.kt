package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.data.download.DownloadFailureReason

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * The words for a queue-level download failure. `DownloadFailureReason` lives in `data/` and must
 * not hold `R`, so the mapping is attached here, following [MigrationResult.toNotificationText].
 * [DownloadFailureReason.ServerError] and the exception-carrying cases pass their text through
 * untranslated: it did not come from this app.
 */
fun DownloadFailureReason.toNotificationText(): NotificationText = when (this) {
    is DownloadFailureReason.InvalidContentType ->
        NotificationText.Res(R.string.error_download_invalid_content_type, listOf(contentType))
    DownloadFailureReason.FileTooSmall ->
        NotificationText.Res(R.string.error_download_file_too_small)
    is DownloadFailureReason.ServerError -> NotificationText.Raw(message)
    is DownloadFailureReason.Unexpected ->
        message?.let { NotificationText.Raw(it) } ?: NotificationText.Res(R.string.error_download_unexpected)
    is DownloadFailureReason.InsufficientUnpackSpace -> {
        val requiredMb = (requiredBytes / BYTES_PER_MB).toInt()
        val availableMb = (availableBytes / BYTES_PER_MB).toInt()
        when (location) {
            DownloadFailureReason.StorageLocation.INTERNAL -> NotificationText.Res(
                R.string.error_download_insufficient_unpack_space_internal,
                listOf(requiredMb, availableMb)
            )
            DownloadFailureReason.StorageLocation.ROM -> NotificationText.Res(
                R.string.error_download_insufficient_unpack_space_rom,
                listOf(requiredMb, availableMb)
            )
        }
    }
    DownloadFailureReason.UnpackEscapedStagingFolder ->
        NotificationText.Res(R.string.error_download_unpack_escaped_staging)
    DownloadFailureReason.StagedPathMissing ->
        NotificationText.Res(R.string.error_download_staged_path_missing)
    is DownloadFailureReason.InsufficientDeploySpace -> NotificationText.Res(
        R.string.error_download_insufficient_deploy_space,
        listOf((requiredBytes / BYTES_PER_MB).toInt(), (availableBytes / BYTES_PER_MB).toInt())
    )
    DownloadFailureReason.MoveToStorageFailed ->
        NotificationText.Res(R.string.error_download_move_to_storage_failed)
    DownloadFailureReason.MovedFileNotFound ->
        NotificationText.Res(R.string.error_download_moved_file_not_found)
    DownloadFailureReason.DownloadedFileMissing ->
        NotificationText.Res(R.string.error_download_downloaded_file_missing)
    DownloadFailureReason.NoDownloadEntryFound ->
        NotificationText.Res(R.string.error_download_no_download_entry)
    DownloadFailureReason.DownloadedFileNoLongerExists ->
        NotificationText.Res(R.string.error_download_no_longer_exists)
    is DownloadFailureReason.ExtractionFailed ->
        message?.let { NotificationText.Raw(it) } ?: NotificationText.Res(R.string.error_download_extraction_failed)
    is DownloadFailureReason.LegacyRaw -> NotificationText.Raw(message)
}
