package com.nendo.argosy.hardware

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.ui.theme.ALauncherColors

private val DefaultPrimary = if (BuildConfig.DEBUG) ALauncherColors.Orange else ALauncherColors.Indigo

@Composable
fun SecondaryHomeTheme(
    primaryColor: Int? = null,
    content: @Composable () -> Unit
) {
    val effectivePrimary = primaryColor?.let { Color(it) } ?: DefaultPrimary

    val colorScheme = remember(effectivePrimary) {
        darkColorScheme(
            primary = effectivePrimary,
            onPrimary = Color.White,
            secondary = ALauncherColors.Teal,
            onSecondary = Color.White,
            background = ALauncherColors.SurfaceDark,
            onBackground = ALauncherColors.OnSurfaceDark,
            surface = ALauncherColors.SurfaceDark,
            onSurface = ALauncherColors.OnSurfaceDark,
            surfaceVariant = ALauncherColors.SurfaceDarkVariant,
            onSurfaceVariant = ALauncherColors.OnSurfaceDark.copy(alpha = 0.8f)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
