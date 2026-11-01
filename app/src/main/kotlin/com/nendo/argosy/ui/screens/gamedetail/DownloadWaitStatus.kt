package com.nendo.argosy.ui.screens.gamedetail

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadState

enum class DownloadWaitStatus(
    @get:StringRes val menuLabelRes: Int
) {
    RETRYING(
        R.string.gamedetail_menu_play_button_retrying
    ),
    WAITING_FOR_NETWORK(
        R.string.gamedetail_menu_play_button_waiting_for_network
    ),
    WAITING_FOR_SERVER(
        R.string.gamedetail_menu_play_button_waiting_for_server
    )
}

fun DownloadProgress.toDownloadWaitStatus(): DownloadWaitStatus? = when (state) {
    DownloadState.RETRYING -> DownloadWaitStatus.RETRYING
    DownloadState.WAITING_FOR_NETWORK -> DownloadWaitStatus.WAITING_FOR_NETWORK
    DownloadState.DOWNLOADING -> DownloadWaitStatus.WAITING_FOR_SERVER.takeIf { isAwaitingServer }
    else -> null
}
