package com.nendo.argosy.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.DualScreenManagerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val notMirrored: StateFlow<Boolean> = MutableStateFlow(false)

/**
 * The insets a launcher surface keeps its content clear of: the visible system bars and the
 * display cutout. Hidden bars report zero, while the cutout stays reported, so a camera hole on a
 * phone in landscape still pushes the content aside. The cutout drops out while an external
 * display mirrors the panel, where the hole does not exist.
 */
val WindowInsets.Companion.launcherContent: WindowInsets
    @Composable
    @NonRestartableComposable
    get() {
        val manager by DualScreenManagerHolder.instanceFlow.collectAsState()
        val mirrored by (manager?.panelMirroredToExternal ?: notMirrored).collectAsState()
        return if (mirrored) systemBars else systemBars.union(displayCutout)
    }
