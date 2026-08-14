package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nendo.argosy.ui.common.AlwaysCrossfadeFactory
import com.nendo.argosy.ui.screens.media.FULL_PERCENT
import com.nendo.argosy.ui.screens.media.MediaBackdropSettings
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The title's own artwork behind a media screen, softened to the strength the user chose.
 *
 * The real image is drawn rather than a softened copy of it: blur, saturation and opacity are three
 * sliders the user can move at any time, and a picture that arrived already blurred would only be
 * right at whichever setting it was baked for. Blur is applied here for the same reason the address
 * carries the server's image tag - one file serves every setting, and the cache keeps it.
 *
 * Two layers, in the order [com.nendo.argosy.ui.screens.gamedetail.GameDetailScreen] stacks them:
 * the artwork, then a scrim that darkens towards the foot of the screen. Opacity fades the artwork
 * rather than thinning the scrim, so the floor under the content is fixed and legibility can only
 * improve as the artwork is turned down. Nothing is drawn at all without an image, which leaves a
 * title whose art has not arrived looking as it did before rather than dimmed for no reason.
 */
@Composable
fun MediaBackdrop(
    imageUrl: String,
    settings: MediaBackdropSettings,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isBlank()) return
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current

    val saturationMatrix = remember(settings.saturation) {
        ColorMatrix().apply { setToSaturation(settings.saturation.toFloat() / FULL_PERCENT) }
    }
    val blurRadius = (settings.blur * ComponentDefaults.MediaBackdrop.blurScale).dp
    val request = remember(imageUrl, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(
                ComponentDefaults.MediaBackdrop.requestWidth,
                ComponentDefaults.MediaBackdrop.requestHeight
            )
            .transitionFactory(AlwaysCrossfadeFactory(ComponentDefaults.MediaBackdrop.crossfadeMs))
            .build()
    }

    val scrimColor = if (theme.isDark) Color.Black else Color.White
    val scrimTop = if (theme.isDark) {
        ComponentDefaults.MediaBackdrop.scrimTopAlphaDark
    } else {
        ComponentDefaults.MediaBackdrop.scrimTopAlphaLight
    }
    val scrimBottom = if (theme.isDark) {
        ComponentDefaults.MediaBackdrop.scrimBottomAlphaDark
    } else {
        ComponentDefaults.MediaBackdrop.scrimBottomAlphaLight
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(saturationMatrix),
            alpha = settings.opacity.toFloat() / FULL_PERCENT,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scrimColor.copy(alpha = scrimTop),
                            scrimColor.copy(alpha = scrimBottom)
                        )
                    )
                )
        )
    }
}
