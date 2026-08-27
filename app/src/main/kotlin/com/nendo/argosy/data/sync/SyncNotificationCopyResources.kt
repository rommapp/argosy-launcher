package com.nendo.argosy.data.sync

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.sync.SyncNotificationCopy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotificationCopyResources @Inject constructor() : SyncNotificationCopy {

    override fun libraryStartFailedTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_start_failed_title)

    override fun libraryFailureDetail(rawMessage: String?): NotificationText {
        val message = rawMessage?.lowercase().orEmpty()
        return when {
            "timeout" in message || "timed out" in message ->
                NotificationText.Res(R.string.notif_sync_failure_timeout)
            "unable to resolve host" in message || "no address associated" in message ->
                NotificationText.Res(R.string.notif_sync_failure_unreachable)
            "connection abort" in message || "connection reset" in message ||
                "failed to connect" in message ->
                NotificationText.Res(R.string.notif_sync_failure_connection_lost)
            rawMessage.isNullOrBlank() ->
                NotificationText.Res(R.string.notif_sync_failure_unknown)
            else -> NotificationText.Raw(rawMessage)
        }
    }

    override fun libraryProgressTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_progress_title)

    override fun libraryProgressStarting(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_progress_starting)

    override fun libraryProgressPlatform(
        platformName: String,
        gamesDone: Int,
        gamesTotal: Int
    ): NotificationText = if (gamesTotal > 0) {
        NotificationText.Res(
            R.string.notif_sync_library_progress_platform_games,
            listOf(platformName, gamesDone, gamesTotal)
        )
    } else {
        NotificationText.Raw(platformName)
    }

    override fun libraryCompleteTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_complete_title)

    override fun libraryCompleteCounts(added: Int, updated: Int, removed: Int): NotificationText =
        if (removed > 0) {
            NotificationText.Res(
                R.string.notif_sync_library_complete_counts_with_removed,
                listOf(added, updated, removed)
            )
        } else {
            NotificationText.Res(
                R.string.notif_sync_library_complete_counts,
                listOf(added, updated)
            )
        }

    override fun libraryCompletedWithErrorsTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_completed_with_errors_title)

    override fun libraryFailedPlatforms(count: Int): NotificationText =
        NotificationText.Plural(R.plurals.notif_sync_library_failed_platforms, count, listOf(count))

    override fun libraryFailedTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_library_failed_title)

    override fun platformProgressTitle(platformName: String): NotificationText =
        NotificationText.Res(R.string.notif_sync_platform_progress_title, listOf(platformName))

    override fun platformProgressFetching(): NotificationText =
        NotificationText.Res(R.string.notif_sync_platform_progress_fetching)

    override fun platformCompleteTitle(platformName: String): NotificationText =
        NotificationText.Res(R.string.notif_sync_platform_complete_title, listOf(platformName))

    override fun platformCompleteCounts(added: Int, updated: Int, removed: Int): NotificationText =
        if (removed > 0) {
            NotificationText.Res(
                R.string.notif_sync_platform_complete_counts_with_removed,
                listOf(added, updated, removed)
            )
        } else {
            NotificationText.Res(
                R.string.notif_sync_platform_complete_counts,
                listOf(added, updated)
            )
        }

    override fun platformCompletedWithErrorsTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_platform_completed_with_errors_title)

    override fun platformErrorDetail(firstError: String?): NotificationText =
        firstError?.let { NotificationText.Raw(it) }
            ?: NotificationText.Res(R.string.notif_sync_platform_error_unknown)

    override fun platformFailedTitle(): NotificationText =
        NotificationText.Res(R.string.notif_sync_platform_failed_title)
}
