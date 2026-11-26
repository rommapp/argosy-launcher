package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

fun artScrimFor(isDark: Boolean): Color =
    if (isDark) ColorTokens.Domain.ArtScrim.dark else ColorTokens.Domain.ArtScrim.light

@Composable
@ReadOnlyComposable
fun artScrimColor(): Color = artScrimFor(LocalArgosyTheme.current.isDark)
