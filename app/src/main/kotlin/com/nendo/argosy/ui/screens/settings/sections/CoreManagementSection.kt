package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.settings.CoreChipState
import com.nendo.argosy.ui.screens.settings.PlatformCoreRow
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreManagementSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val coreState = uiState.coreManagement

    FocusedScroll(
        listState = listState,
        focusedIndex = coreState.focusedPlatformIndex + 1 // +1 for header item
    )

    if (coreState.platforms.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No supported platforms enabled for sync",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        item(key = "header") {
            Text(
                text = "Select cores for each platform",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )
        }

        itemsIndexed(
            items = coreState.platforms,
            key = { _, platform -> platform.platformSlug }
        ) { platformIndex, platform ->
            val isPlatformFocused = platformIndex == coreState.focusedPlatformIndex

            PlatformCoreRowItem(
                platform = platform,
                isPlatformFocused = isPlatformFocused,
                focusedCoreIndex = if (isPlatformFocused) coreState.focusedCoreIndex else platform.activeCoreIndex,
                isOnline = coreState.isOnline,
                downloadingCoreId = coreState.downloadingCoreId,
                onCoreClick = { viewModel.selectCoreForPlatform() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformCoreRowItem(
    platform: PlatformCoreRow,
    isPlatformFocused: Boolean,
    focusedCoreIndex: Int,
    isOnline: Boolean,
    downloadingCoreId: String?,
    onCoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isPlatformFocused) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .padding(Dimens.spacingSm)
    ) {
        Text(
            text = platform.platformName,
            style = MaterialTheme.typography.titleSmall,
            color = if (isPlatformFocused) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(bottom = Dimens.spacingXs)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            platform.cores.forEachIndexed { index, core ->
                val isChipFocused = isPlatformFocused && index == focusedCoreIndex
                val isDownloading = downloadingCoreId == core.coreId

                CoreChip(
                    core = core,
                    isFocused = isChipFocused,
                    isPlatformFocused = isPlatformFocused,
                    isOnline = isOnline,
                    isDownloading = isDownloading,
                    onClick = onCoreClick
                )
            }
        }
    }
}

@Composable
private fun CoreChip(
    core: CoreChipState,
    isFocused: Boolean,
    isPlatformFocused: Boolean,
    isOnline: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when {
        core.isActive -> Color(0xFF4CAF50)
        core.isInstalled -> Color(0xFF2196F3)
        isOnline -> Color(0xFFE57373)
        else -> Color.Gray
    }

    val textColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isPlatformFocused -> statusColor.copy(alpha = 0.8f)
        else -> statusColor.copy(alpha = 0.6f)
    }

    val buttonHeight = Dimens.iconLg - Dimens.spacingXs

    if (isFocused) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(buttonHeight),
            contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone),
            border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.onPrimaryContainer)
        ) {
            CoreChipContent(
                core = core,
                isDownloading = isDownloading,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                statusColor = statusColor
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = Modifier.height(buttonHeight),
            contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.elevationNone)
        ) {
            CoreChipContent(
                core = core,
                isDownloading = isDownloading,
                textColor = textColor,
                statusColor = statusColor
            )
        }
    }
}

@Composable
private fun CoreChipContent(
    core: CoreChipState,
    isDownloading: Boolean,
    textColor: Color,
    statusColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            isDownloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = textColor,
                    strokeWidth = 2.dp
                )
            }
            core.isActive -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            !core.isInstalled -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download required",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = core.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
