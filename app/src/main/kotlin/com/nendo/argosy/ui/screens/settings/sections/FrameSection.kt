package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.frame.FrameRegistry
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun FrameSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val frameRegistry = remember { viewModel.getFrameRegistry() }
    val allFrames = remember { frameRegistry.getAllFrames() }
    val platformContext = uiState.builtinVideo.currentPlatformContext
    val platformSettings = platformContext?.let {
        uiState.platformLibretro.platformSettings[it.platformId]
    }
    val currentFrameOverride = platformSettings?.frame

    val downloadingId = uiState.frameDownloadingId
    val installedIds = if (uiState.frameInstalledRefresh >= 0) {
        frameRegistry.getInstalledIds()
    } else emptySet()

    val defaultFrameForPlatform = remember(platformContext?.platformSlug) {
        platformContext?.platformSlug?.let { slug ->
            frameRegistry.getFramesForPlatform(slug).firstOrNull()?.id
        }
    }

    val previewFrameId = when {
        currentFrameOverride == null -> defaultFrameForPlatform
        currentFrameOverride == "none" -> null
        else -> currentFrameOverride
    }

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            Text(
                text = "Select Frame",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                item(key = "auto") {
                    val autoLabel = if (defaultFrameForPlatform != null) {
                        val frameName = allFrames.find { it.id == defaultFrameForPlatform }?.displayName
                        "Auto ($frameName)"
                    } else {
                        "Auto (None)"
                    }
                    FrameListItem(
                        displayName = autoLabel,
                        isSelected = currentFrameOverride == null,
                        isInstalled = true,
                        isDownloading = false,
                        isFocused = uiState.focusedIndex == 0,
                        onClick = { viewModel.updatePlatformLibretroSetting(
                            com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef.Frame, null
                        ) },
                        onDownload = {}
                    )
                }

                item(key = "none") {
                    FrameListItem(
                        displayName = "None",
                        isSelected = currentFrameOverride == "none",
                        isInstalled = true,
                        isDownloading = false,
                        isFocused = uiState.focusedIndex == 1,
                        onClick = { viewModel.updatePlatformLibretroSetting(
                            com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef.Frame, "none"
                        ) },
                        onDownload = {}
                    )
                }

                itemsIndexed(
                    allFrames,
                    key = { _, frame -> frame.id }
                ) { index, frame ->
                    val isInstalled = frame.id in installedIds
                    val isDownloading = downloadingId == frame.id
                    FrameListItem(
                        displayName = frame.displayName,
                        isSelected = currentFrameOverride == frame.id,
                        isInstalled = isInstalled,
                        isDownloading = isDownloading,
                        isFocused = uiState.focusedIndex == index + 2,
                        onClick = {
                            if (isInstalled) {
                                viewModel.updatePlatformLibretroSetting(
                                    com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef.Frame, frame.id
                                )
                            }
                        },
                        onDownload = {
                            if (!isInstalled && !isDownloading) {
                                viewModel.downloadAndSelectFrame(frame.id)
                            }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            FramePreview(
                frameRegistry = frameRegistry,
                frameId = previewFrameId
            )
        }
    }
}

@Composable
private fun FrameListItem(
    displayName: String,
    isSelected: Boolean,
    isInstalled: Boolean,
    isDownloading: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(backgroundColor)
            .clickableNoFocus { if (isInstalled) onClick() else onDownload() }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        when {
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            isSelected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = contentColor
            )
            !isInstalled -> Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Download",
                tint = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FramePreview(
    frameRegistry: FrameRegistry,
    frameId: String?
) {
    if (frameId == null) {
        Text(
            text = "No frame selected",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val bitmap = remember(frameId) {
        frameRegistry.loadFrame(frameId)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Frame preview",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = "Frame not available",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun framePickerMaxFocusIndex(frameRegistry: FrameRegistry): Int {
    return frameRegistry.getAllFrames().size + 1
}
