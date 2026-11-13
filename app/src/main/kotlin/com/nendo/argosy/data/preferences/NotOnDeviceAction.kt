package com.nendo.argosy.data.preferences

enum class NotOnDeviceAction(val token: String) {
    DOWNLOAD("download"),
    STREAM("stream");

    companion object {
        fun fromToken(value: String?): NotOnDeviceAction =
            entries.firstOrNull { it.token == value } ?: DOWNLOAD
    }
}
