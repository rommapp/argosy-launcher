package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.sync.SyncLibraryFailureReason

/**
 * The words for a [SyncLibraryFailureReason]. It lives in `domain/` and must not hold `R`, so
 * the mapping is attached here, following [DownloadFailureReasonUi].
 */
fun SyncLibraryFailureReason.toNotificationText(): NotificationText = when (this) {
    SyncLibraryFailureReason.NotConnected ->
        NotificationText.Res(R.string.error_sync_library_not_connected)
    is SyncLibraryFailureReason.PlatformCountFailed -> NotificationText.Raw(serverMessage)
    is SyncLibraryFailureReason.Unexpected ->
        message?.let { NotificationText.Raw(it) } ?: NotificationText.Res(R.string.error_sync_library_unexpected)
}
