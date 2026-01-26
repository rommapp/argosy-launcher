package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair

@Composable
fun ScreenshotViewerOverlay(
    screenshots: List<ScreenshotPair>,
    currentIndex: Int,
    onNavigate: (delta: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (screenshots.isEmpty()) return

    val screenshot = screenshots[currentIndex.coerceIn(0, screenshots.size - 1)]
    var cacheLoadFailed by remember(currentIndex) { mutableStateOf(false) }
    val useRemote = cacheLoadFailed || screenshot.cachedPath == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        if (useRemote) {
            AsyncImage(
                model = screenshot.remoteUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = Dimens.settingsItemMinHeight)
            )
        } else {
            AsyncImage(
                model = java.io.File(screenshot.cachedPath!!),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = Dimens.settingsItemMinHeight),
                onError = { cacheLoadFailed = true }
            )
        }

        Row(modifier = Modifier.fillMaxSize().padding(bottom = Dimens.settingsItemMinHeight)) {
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickableNoFocus { onNavigate(-1) }
            )
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .clickableNoFocus(onClick = onDismiss)
            )
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickableNoFocus { onNavigate(1) }
            )
        }

        Text(
            text = "${currentIndex + 1} / ${screenshots.size}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)  // Keep as interaction threshold
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterBar(
                hints = listOf(
                    InputButton.DPAD_HORIZONTAL to "Navigate",
                    InputButton.B to "Close",
                    InputButton.X to "Set Background"
                )
            )
        }
    }
}
