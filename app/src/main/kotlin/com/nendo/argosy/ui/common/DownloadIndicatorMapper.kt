package com.nendo.argosy.ui.common

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator

val SteamDownloadState.appId: Long?
    get() = when (this) {
        is SteamDownloadState.Preparing -> appId
        is SteamDownloadState.Connecting -> appId
        is SteamDownloadState.FetchingManifest -> appId
        is SteamDownloadState.Validating -> appId
        is SteamDownloadState.Downloading -> appId
        is SteamDownloadState.Moving -> appId
        is SteamDownloadState.Completed -> appId
        is SteamDownloadState.Failed -> appId
        is SteamDownloadState.Paused -> appId
        is SteamDownloadState.Cleaning -> appId
        is SteamDownloadState.Idle -> null
    }

fun SteamDownloadState.toIndicator(progress: Float): GameDownloadIndicator? = when (this) {
    is SteamDownloadState.Preparing,
    is SteamDownloadState.Connecting,
    is SteamDownloadState.FetchingManifest -> GameDownloadIndicator(isQueued = true)
    is SteamDownloadState.Validating -> GameDownloadIndicator(isExtracting = true, progress = progress)
    is SteamDownloadState.Downloading -> GameDownloadIndicator(isDownloading = true, progress = progress)
    is SteamDownloadState.Moving -> GameDownloadIndicator(isExtracting = true, progress = 1f)
    is SteamDownloadState.Cleaning -> GameDownloadIndicator(isExtracting = true, progress = 0f)
    is SteamDownloadState.Paused -> GameDownloadIndicator(isPaused = true, progress = progress)
    is SteamDownloadState.Completed,
    is SteamDownloadState.Failed,
    is SteamDownloadState.Idle -> null
}

fun SteamDownloadState.toDownloadStatus(progress: Float): Pair<GameDownloadStatus, Float>? = when (this) {
    is SteamDownloadState.Preparing,
    is SteamDownloadState.Connecting,
    is SteamDownloadState.FetchingManifest -> GameDownloadStatus.QUEUED to 0f
    is SteamDownloadState.Validating -> GameDownloadStatus.EXTRACTING to progress
    is SteamDownloadState.Downloading -> GameDownloadStatus.DOWNLOADING to progress
    is SteamDownloadState.Moving -> GameDownloadStatus.EXTRACTING to 1f
    is SteamDownloadState.Cleaning -> GameDownloadStatus.EXTRACTING to 0f
    is SteamDownloadState.Paused -> GameDownloadStatus.PAUSED to progress
    is SteamDownloadState.Completed -> GameDownloadStatus.DOWNLOADED to 1f
    is SteamDownloadState.Failed -> GameDownloadStatus.NOT_DOWNLOADED to 0f
    is SteamDownloadState.Idle -> null
}

/**
 * The foreground notification line for a Steam download's current phase. Null for
 * [SteamDownloadState.Downloading]: the caller formats that one with a live progress percentage,
 * and for the states with nothing left to report.
 */
fun SteamDownloadState.toNotificationText(context: Context, gameName: String): String? = when (this) {
    is SteamDownloadState.Preparing ->
        context.getString(R.string.download_indicator_notification_preparing, gameName)
    is SteamDownloadState.Connecting ->
        context.getString(R.string.download_indicator_notification_connecting)
    is SteamDownloadState.FetchingManifest ->
        context.getString(R.string.download_indicator_notification_fetching_manifest, gameName)
    is SteamDownloadState.Validating ->
        context.getString(R.string.download_indicator_notification_unpacking, gameName)
    is SteamDownloadState.Downloading -> null
    is SteamDownloadState.Moving ->
        context.getString(R.string.download_indicator_notification_moving, gameName)
    is SteamDownloadState.Cleaning ->
        context.getString(R.string.download_indicator_notification_cleaning, gameName)
    is SteamDownloadState.Paused ->
        context.getString(R.string.download_indicator_notification_paused, gameName)
    is SteamDownloadState.Completed,
    is SteamDownloadState.Failed,
    is SteamDownloadState.Idle -> null
}

fun DownloadState.toIndicator(progressPercent: Float, extractionPercent: Float): GameDownloadIndicator =
    when (this) {
        DownloadState.DOWNLOADING -> GameDownloadIndicator(isDownloading = true, progress = progressPercent)
        DownloadState.EXTRACTING,
        DownloadState.MOVING -> GameDownloadIndicator(isExtracting = true, progress = extractionPercent)
        DownloadState.PAUSED -> GameDownloadIndicator(isPaused = true, progress = progressPercent)
        DownloadState.QUEUED -> GameDownloadIndicator(isQueued = true)
        else -> GameDownloadIndicator.NONE
    }
