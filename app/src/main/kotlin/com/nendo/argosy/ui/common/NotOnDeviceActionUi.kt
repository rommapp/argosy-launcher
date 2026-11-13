package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NotOnDeviceAction

@get:StringRes
val NotOnDeviceAction.labelRes: Int
    get() = when (this) {
        NotOnDeviceAction.DOWNLOAD -> R.string.stream_setting_not_on_device_option_download
        NotOnDeviceAction.STREAM -> R.string.stream_setting_not_on_device_option_stream
    }

@get:StringRes
val NotOnDeviceAction.subtitleRes: Int
    get() = when (this) {
        NotOnDeviceAction.DOWNLOAD -> R.string.stream_setting_not_on_device_subtitle_download
        NotOnDeviceAction.STREAM -> R.string.stream_setting_not_on_device_subtitle_stream
    }
