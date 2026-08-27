package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.state.StateSyncFailureReason

/**
 * The words for a [StateSyncFailureReason]. It lives in `domain/` and must not hold `R`, so the
 * mapping is attached here, following [DownloadFailureReasonUi].
 */
fun StateSyncFailureReason.toNotificationText(): NotificationText = when (this) {
    StateSyncFailureReason.GameNotFound -> NotificationText.Res(R.string.error_state_sync_game_not_found)
    StateSyncFailureReason.NoLocalPath -> NotificationText.Res(R.string.error_state_sync_no_local_path)
}
