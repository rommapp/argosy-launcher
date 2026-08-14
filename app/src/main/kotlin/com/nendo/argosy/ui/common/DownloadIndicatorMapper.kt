package com.nendo.argosy.ui.common

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

fun SteamDownloadState.toNotificationText(gameName: String): String? = when (this) {
    is SteamDownloadState.Preparing -> "Preparing: $gameName"
    is SteamDownloadState.Connecting -> "Connecting to Steam..."
    is SteamDownloadState.FetchingManifest -> "Fetching manifest: $gameName"
    is SteamDownloadState.Validating -> "Unpacking: $gameName"
    is SteamDownloadState.Downloading -> null // caller formats with progress
    is SteamDownloadState.Moving -> "Moving: $gameName"
    is SteamDownloadState.Cleaning -> "Cleaning up: $gameName"
    is SteamDownloadState.Paused -> "Paused: $gameName"
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
