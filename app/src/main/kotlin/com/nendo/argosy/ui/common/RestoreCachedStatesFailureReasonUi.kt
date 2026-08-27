package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesFailureReason

/**
 * The words for a [RestoreCachedStatesFailureReason]. It lives in `domain/` and must not hold
 * `R`, so the mapping is attached here, following [DownloadFailureReasonUi].
 */
fun RestoreCachedStatesFailureReason.toNotificationText(): NotificationText = when (this) {
    RestoreCachedStatesFailureReason.GameNotFound ->
        NotificationText.Res(R.string.error_restore_cached_states_game_not_found)
    RestoreCachedStatesFailureReason.NoLocalPath ->
        NotificationText.Res(R.string.error_restore_cached_states_no_local_path)
    RestoreCachedStatesFailureReason.StateDirectoryUnresolved ->
        NotificationText.Res(R.string.error_restore_cached_states_directory_unresolved)
    is RestoreCachedStatesFailureReason.Unexpected ->
        message?.let { NotificationText.Raw(it) }
            ?: NotificationText.Res(R.string.error_restore_cached_states_unexpected)
}
