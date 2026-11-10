package com.nendo.argosy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

/**
 * A game's art blurred behind a screen, under a scrim that keeps the screen's text readable.
 * Draws the scrim alone when [imagePath] is null.
 */
@Composable
fun GameArtBackdrop(imagePath: String?, modifier: Modifier = Modifier) {
    val isDark = LocalLauncherTheme.current.isDarkTheme
    val scrim = if (isDark) Color.Black else Color.White
    Box(modifier = modifier.fillMaxSize()) {
        if (imagePath != null) {
            AsyncImage(
                model = rememberFileImageModel(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(Dimens.gameArtBackdropBlur)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scrim.copy(alpha = if (isDark) BACKDROP_TOP_DARK else BACKDROP_TOP_LIGHT),
                            scrim.copy(alpha = if (isDark) BACKDROP_BOTTOM_DARK else BACKDROP_BOTTOM_LIGHT)
                        )
                    )
                )
        )
    }
}

private const val BACKDROP_TOP_DARK = 0.55f
private const val BACKDROP_BOTTOM_DARK = 0.85f
private const val BACKDROP_TOP_LIGHT = 0.35f
private const val BACKDROP_BOTTOM_LIGHT = 0.7f
