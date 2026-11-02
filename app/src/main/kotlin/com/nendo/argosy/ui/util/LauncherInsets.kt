package com.nendo.argosy.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

/**
 * The insets a launcher surface keeps its content clear of: the visible system bars and the
 * display cutout. Hidden bars report zero, while the cutout stays reported, so a camera hole on a
 * phone in landscape still pushes the content aside.
 */
val WindowInsets.Companion.launcherContent: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = systemBars.union(displayCutout)
