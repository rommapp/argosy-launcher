package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        screenshot.cachedPath?.let { path ->
            AsyncImage(
                model = java.io.File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp)
            )
        }
        AsyncImage(
            model = screenshot.remoteUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp)
        )

        Row(modifier = Modifier.fillMaxSize().padding(bottom = 56.dp)) {
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickable(
                        onClick = { onNavigate(-1) },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            )
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .clickable(
                        onClick = onDismiss,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            )
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickable(
                        onClick = { onNavigate(1) },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            )
        }

        Text(
            text = "${currentIndex + 1} / ${screenshots.size}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterBar(
                hints = listOf(
                    InputButton.DPAD_HORIZONTAL to "Navigate",
                    InputButton.SOUTH to "Close",
                    InputButton.EAST to "Close",
                    InputButton.WEST to "Set Background"
                )
            )
        }
    }
}
