package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.sync.SyncPlatformFailureReason

/**
 * The words for a [SyncPlatformFailureReason]. It lives in `domain/` and must not hold `R`, so
 * the mapping is attached here, following [DownloadFailureReasonUi].
 */
fun SyncPlatformFailureReason.toNotificationText(): NotificationText = when (this) {
    SyncPlatformFailureReason.NotConnected ->
        NotificationText.Res(R.string.error_sync_platform_not_connected)
    SyncPlatformFailureReason.PlatformNotFound ->
        NotificationText.Res(R.string.error_sync_platform_not_found)
    is SyncPlatformFailureReason.Unexpected ->
        message?.let { NotificationText.Raw(it) } ?: NotificationText.Res(R.string.error_sync_platform_unexpected)
}
