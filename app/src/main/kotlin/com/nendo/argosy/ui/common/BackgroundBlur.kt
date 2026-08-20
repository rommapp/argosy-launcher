package com.nendo.argosy.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The blur a background is drawn with for the stored preference level, the same on every surface
 * that draws one.
 */
val Int.backgroundBlurDp: Dp
    get() = (this * ComponentDefaults.MediaBackdrop.blurScale).dp
