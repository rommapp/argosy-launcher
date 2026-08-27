package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.SyncProgress

private const val SWITCH_PLATFORM_SLUG = "switch"

/**
 * The line the sync overlay shows for this step.
 *
 * `SyncProgress` lives in `domain/` and must not import `R`, so the copy is attached here
 * instead, following the shape of [CompletionStatusUi] and [SortOptionUi]. The sealed class
 * keeps only the identity of the step and the values it carries.
 *
 * [SyncProgress.Error] is passed through untranslated: its text comes from the server or from
 * an exception, so it is not the app's to restate.
 */
@Composable
fun SyncProgress.statusMessage(): String = when (this) {
    is SyncProgress.Idle -> ""
    is SyncProgress.PreLaunch.CheckingSave -> when (found) {
        null -> stringResource(R.string.sync_prelaunch_checking_save)
        true -> stringResource(R.string.sync_prelaunch_save_found)
        false -> stringResource(R.string.sync_prelaunch_save_absent)
    }
    is SyncProgress.PreLaunch.Connecting -> when (success) {
        null -> stringResource(R.string.sync_prelaunch_connecting)
        true -> stringResource(R.string.sync_prelaunch_connected)
        false -> stringResource(R.string.sync_prelaunch_connect_failed)
    }
    is SyncProgress.PreLaunch.Downloading -> when (success) {
        null -> stringResource(R.string.sync_prelaunch_downloading)
        true -> stringResource(R.string.sync_prelaunch_download_complete)
        false -> stringResource(R.string.sync_prelaunch_download_failed)
    }
    is SyncProgress.PreLaunch.Writing -> when (success) {
        null -> stringResource(R.string.sync_prelaunch_writing)
        true -> stringResource(R.string.sync_prelaunch_write_complete)
        false -> stringResource(R.string.sync_prelaunch_write_failed)
    }
    is SyncProgress.PreLaunch.Launching -> stringResource(R.string.sync_prelaunch_launching)
    is SyncProgress.PostSession.CheckingSave -> when (found) {
        null -> stringResource(R.string.sync_postsession_checking_save)
        true -> stringResource(R.string.sync_postsession_save_found)
        false -> stringResource(R.string.sync_postsession_save_absent)
    }
    is SyncProgress.PostSession.Connecting -> when (success) {
        null -> stringResource(R.string.sync_postsession_connecting)
        true -> stringResource(R.string.sync_postsession_connected)
        false -> stringResource(R.string.sync_postsession_connect_failed)
    }
    is SyncProgress.PostSession.Uploading -> when (success) {
        null -> stringResource(R.string.sync_postsession_uploading)
        true -> stringResource(R.string.sync_postsession_upload_complete)
        false -> stringResource(R.string.sync_postsession_upload_queued)
    }
    is SyncProgress.PostSession.Complete -> stringResource(R.string.sync_postsession_complete)
    is SyncProgress.Error -> message
    is SyncProgress.Skipped -> stringResource(R.string.sync_progress_skipped)
    is SyncProgress.HardcoreConflict -> stringResource(R.string.sync_progress_hardcore_conflict)
    is SyncProgress.LocalModified -> stringResource(R.string.sync_progress_local_modified)
    is SyncProgress.PostSessionConflict -> stringResource(R.string.sync_progress_save_conflict)
    is SyncProgress.BlockedReason.PermissionRequired ->
        stringResource(R.string.sync_blocked_permission_title)
    is SyncProgress.BlockedReason.SavePathNotFound ->
        stringResource(R.string.sync_blocked_path_not_found_title)
    is SyncProgress.BlockedReason.AccessDenied ->
        stringResource(R.string.sync_blocked_access_denied_title)
}

/**
 * The explanation shown under [statusMessage], for the steps that have one.
 */
@Composable
fun SyncProgress.detailMessage(): String? = when (this) {
    is SyncProgress.BlockedReason.PermissionRequired -> stringResource(
        R.string.sync_blocked_permission_detail,
        emulatorName ?: stringResource(R.string.sync_blocked_permission_emulator_fallback)
    )
    is SyncProgress.BlockedReason.SavePathNotFound -> stringResource(
        R.string.sync_blocked_path_not_found_detail,
        emulatorName ?: stringResource(R.string.sync_blocked_path_not_found_emulator_fallback)
    )
    is SyncProgress.BlockedReason.AccessDenied -> accessDeniedDetail()
    else -> null
}

@Composable
private fun SyncProgress.BlockedReason.AccessDenied.accessDeniedDetail(): String {
    val owner = emulatorName ?: stringResource(R.string.sync_blocked_access_denied_emulator_fallback)
    return if (platformSlug == SWITCH_PLATFORM_SLUG) {
        val target = emulatorName ?: stringResource(R.string.sync_blocked_access_denied_configure_fallback)
        stringResource(R.string.sync_blocked_access_denied_switch_detail, owner, target)
    } else {
        stringResource(R.string.sync_blocked_access_denied_detail, owner)
    }
}
