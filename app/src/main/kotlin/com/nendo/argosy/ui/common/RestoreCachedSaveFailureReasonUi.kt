package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveFailureReason

/**
 * The words for a [RestoreCachedSaveFailureReason]. It lives in `domain/` and must not hold `R`,
 * so the mapping is attached here, following [DownloadFailureReasonUi].
 */
fun RestoreCachedSaveFailureReason.toNotificationText(): NotificationText = when (this) {
    RestoreCachedSaveFailureReason.GameNotFound ->
        NotificationText.Res(R.string.error_restore_cached_save_game_not_found)
    RestoreCachedSaveFailureReason.NoLocalCopy ->
        NotificationText.Res(R.string.error_restore_cached_save_no_local_copy)
    RestoreCachedSaveFailureReason.SaveLocationUnresolved ->
        NotificationText.Res(R.string.error_restore_cached_save_location_unresolved)
    RestoreCachedSaveFailureReason.ClearExistingSaveFailed ->
        NotificationText.Res(R.string.error_restore_cached_save_clear_failed)
    RestoreCachedSaveFailureReason.NoLocalCacheId ->
        NotificationText.Res(R.string.error_restore_cached_save_no_local_cache_id)
    RestoreCachedSaveFailureReason.NoServerSaveId ->
        NotificationText.Res(R.string.error_restore_cached_save_no_server_save_id)
    RestoreCachedSaveFailureReason.RestoreFailed ->
        NotificationText.Res(R.string.error_restore_cached_save_restore_failed)
}
