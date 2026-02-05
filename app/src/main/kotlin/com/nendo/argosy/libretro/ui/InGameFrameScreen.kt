package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.frame.FrameManager
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun InGameFrameScreen(
    manager: FrameManager,
    isOffline: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentOnConfirm = rememberUpdatedState(onConfirm)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                manager.previousFrame(localOnly = isOffline)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                manager.nextFrame(localOnly = isOffline)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm.value()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArrowButton(
                direction = -1,
                onClick = { manager.previousFrame(localOnly = isOffline) },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            ArrowButton(
                direction = 1,
                onClick = { manager.nextFrame(localOnly = isOffline) },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
            )
        }

        if (manager.isDownloading) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        FrameInfoBar(
            manager = manager,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
        )

        FooterBar(
            hints = buildFrameFooterHints(),
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> currentOnConfirm.value()
                    InputButton.B -> currentOnDismiss.value()
                    InputButton.DPAD_HORIZONTAL -> {}
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    return inputHandler
}

@Composable
private fun ArrowButton(
    direction: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickableNoFocus(onClick = onClick)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (direction < 0) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (direction < 0) "Previous" else "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun FrameInfoBar(
    manager: FrameManager,
    modifier: Modifier = Modifier
) {
    val frameId = manager.selectedFrameId
    val frameName = if (frameId != null) {
        manager.getFrameEntry(frameId)?.displayName ?: frameId
    } else {
        "No Frame"
    }

    val isInstalled = frameId == null || manager.isFrameInstalled(frameId)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = frameName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun buildFrameFooterHints(): List<Pair<InputButton, String>> {
    return listOf(
        InputButton.DPAD_HORIZONTAL to "Change",
        InputButton.A to "Select",
        InputButton.B to "Cancel"
    )
}
