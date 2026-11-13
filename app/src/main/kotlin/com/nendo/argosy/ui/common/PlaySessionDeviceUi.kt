package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.social.uploader.PlaySessionPull

/**
 * The label for a play session device id that names no real device, or null when [deviceId]
 * is a real device whose stored name is shown as is.
 */
@StringRes
fun playSessionDeviceLabelRes(deviceId: String): Int? = when (deviceId) {
    PlaySessionPull.ROMM_STREAMING_DEVICE_ID -> R.string.play_session_device_romm_streaming
    else -> null
}
