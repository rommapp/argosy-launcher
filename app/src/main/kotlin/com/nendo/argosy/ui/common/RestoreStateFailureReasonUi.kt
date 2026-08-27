package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.state.RestoreStateFailureReason

/**
 * The words for a [RestoreStateFailureReason]. It lives in `domain/` and must not hold `R`, so
 * the mapping is attached here, following [DownloadFailureReasonUi].
 */
fun RestoreStateFailureReason.toNotificationText(): NotificationText = when (this) {
    RestoreStateFailureReason.TargetPathUnresolved ->
        NotificationText.Res(R.string.error_restore_state_target_path_unresolved)
    RestoreStateFailureReason.WriteFailed ->
        NotificationText.Res(R.string.error_restore_state_write_failed)
}
