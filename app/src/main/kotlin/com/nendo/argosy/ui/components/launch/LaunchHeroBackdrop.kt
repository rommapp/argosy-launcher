package com.nendo.argosy.ui.components.launch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nendo.argosy.ui.common.ExpandingClipShape
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import com.nendo.argosy.ui.theme.Dimens

/**
 * [origin]'s art, clipped to its column at [expansion] 0 and filling the screen at 1.
 */
@Composable
fun LaunchHeroBackdrop(
    origin: LaunchHeroOrigin,
    expansion: () -> Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { Dimens.radiusPanel.toPx() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val model = rememberFileImageModel(origin.artPath)
    val request = remember(model, origin.artIsScreenshot) {
        ImageRequest.Builder(context)
            .data(model)
            .apply { if (origin.artIsScreenshot) stackedScreenshotBackdrop() }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .graphicsLayer {
                shape = ExpandingClipShape(origin.bounds.translate(-rootOffset), expansion(), radiusPx)
                clip = true
            }
    )
}
