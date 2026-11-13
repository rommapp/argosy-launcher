package com.nendo.argosy.ui.screens.stream

import androidx.compose.runtime.compositionLocalOf
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.data.streaming.StreamFirstRule

/**
 * The account's choice for games not on this device and the platforms the connected server can
 * stream, lowercased.
 */
data class StreamFirstUi(
    val action: NotOnDeviceAction = NotOnDeviceAction.DOWNLOAD,
    val platforms: Set<String> = emptySet()
) {
    fun leads(platformSlug: String?, isRommGame: Boolean, isOnDevice: Boolean): Boolean {
        val hasContainer = platformSlug?.lowercase()?.let { it in platforms } == true
        return isRommGame && StreamFirstRule.leadsWithStream(action, isOnDevice, hasContainer)
    }
}

val LocalStreamFirst = compositionLocalOf { StreamFirstUi() }
