package com.nendo.argosy.ui.common

import com.nendo.argosy.data.download.DownloadState
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator

fun DownloadState.toIndicator(progressPercent: Float, extractionPercent: Float): GameDownloadIndicator =
    when (this) {
        DownloadState.DOWNLOADING,
        DownloadState.RETRYING,
        DownloadState.WAITING_FOR_NETWORK -> GameDownloadIndicator(isDownloading = true, progress = progressPercent)
        DownloadState.EXTRACTING,
        DownloadState.MOVING -> GameDownloadIndicator(isExtracting = true, progress = extractionPercent)
        DownloadState.PAUSED -> GameDownloadIndicator(isPaused = true, progress = progressPercent)
        DownloadState.QUEUED -> GameDownloadIndicator(isQueued = true)
        else -> GameDownloadIndicator.NONE
    }

@get:StringRes
val DownloadState.retryStatusLabelRes: Int?
    get() = when (this) {
        DownloadState.RETRYING -> R.string.downloads_item_status_retrying
        DownloadState.WAITING_FOR_NETWORK -> R.string.downloads_item_status_waiting_for_network
        else -> null
    }
