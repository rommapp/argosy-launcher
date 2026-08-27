package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.download.DownloadGameFailureReason

/**
 * The words for a [DownloadGameFailureReason]. It lives in `domain/` and must not hold `R`, so
 * the mapping is attached here, following [DownloadFailureReasonUi]. [RomInfoFetchFailed] keeps
 * the server's own message untranslated inside the sentence describing what failed.
 */
fun DownloadGameFailureReason.toNotificationText(): NotificationText = when (this) {
    DownloadGameFailureReason.GameNotFound ->
        NotificationText.Res(R.string.error_download_game_not_found)
    DownloadGameFailureReason.GameNotSynced ->
        NotificationText.Res(R.string.error_download_game_not_synced)
    is DownloadGameFailureReason.InvalidFileType ->
        NotificationText.Res(R.string.error_download_invalid_file_type, listOf(extension))
    is DownloadGameFailureReason.RomInfoFetchFailed ->
        NotificationText.Res(R.string.error_download_rom_info_fetch_failed, listOf(serverMessage))
    DownloadGameFailureReason.NoDiscsFound ->
        NotificationText.Res(R.string.error_download_no_discs_found)
    DownloadGameFailureReason.AllDiscsAlreadyDownloaded ->
        NotificationText.Res(R.string.error_download_all_discs_downloaded)
    DownloadGameFailureReason.NoDiscsQueued ->
        NotificationText.Res(R.string.error_download_no_discs_queued)
    DownloadGameFailureReason.GameNotFoundForRepair ->
        NotificationText.Res(R.string.error_download_game_not_found_for_repair)
    DownloadGameFailureReason.NotMultiDisc ->
        NotificationText.Res(R.string.error_download_not_multi_disc)
    is DownloadGameFailureReason.Extraction -> reason.toNotificationText()
}
