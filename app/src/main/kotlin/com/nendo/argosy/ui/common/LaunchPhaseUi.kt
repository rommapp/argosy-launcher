package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.LaunchFailure
import com.nendo.argosy.domain.model.LaunchPhase

@get:StringRes
val LaunchPhase.waitLabelRes: Int?
    get() = when (this) {
        LaunchPhase.PREVIOUS -> R.string.ui_launch_overlay_wait_previous
        LaunchPhase.RECAP_LOADING -> R.string.ui_launch_overlay_wait_recap
        LaunchPhase.SAVE -> R.string.ui_launch_overlay_wait_save
        LaunchPhase.CORE -> R.string.ui_launch_overlay_wait_core
        LaunchPhase.TEXTURES -> R.string.ui_launch_overlay_wait_textures
        LaunchPhase.PREPARE -> R.string.ui_launch_overlay_wait_prepare
        LaunchPhase.LAUNCHING -> null
        LaunchPhase.QUESTION -> null
    }

@get:StringRes
val LaunchFailure.labelRes: Int
    get() = when (this) {
        LaunchFailure.SERVER_UNREACHABLE -> R.string.ui_launch_overlay_failed_connect
        LaunchFailure.SERVER_TIMED_OUT -> R.string.ui_launch_overlay_failed_timeout
        LaunchFailure.SAVE_DOWNLOAD_FAILED -> R.string.ui_launch_overlay_failed_download
        LaunchFailure.SAVE_WRITE_FAILED -> R.string.ui_launch_overlay_failed_write
    }
