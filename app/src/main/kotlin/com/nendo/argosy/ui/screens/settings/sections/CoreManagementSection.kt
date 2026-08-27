package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.screens.settings.CoreChipState
import com.nendo.argosy.ui.screens.settings.PlatformCoreRow
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus

internal sealed class CoreManagementItem(val key: String) {
    val isFocusable: Boolean get() = this is Platform

    data object Header : CoreManagementItem("header")
    data class Platform(val row: PlatformCoreRow) : CoreManagementItem(row.platformSlug)
}

private fun buildCoreManagementItems(platforms: List<PlatformCoreRow>): List<CoreManagementItem> =
    buildList {
        add(CoreManagementItem.Header)
        for (platform in platforms) {
            add(CoreManagementItem.Platform(platform))
        }
    }

private fun createCoreManagementLayout(items: List<CoreManagementItem>) =
    SettingsLayout<CoreManagementItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true }
    )

internal data class CoreManagementLayoutInfo(
    val layout: SettingsLayout<CoreManagementItem, Unit>,
    val items: List<CoreManagementItem>
)

internal fun createCoreManagementLayoutInfo(
    platforms: List<PlatformCoreRow>
): CoreManagementLayoutInfo {
    val items = buildCoreManagementItems(platforms)
    return CoreManagementLayoutInfo(createCoreManagementLayout(items), items)
}

internal fun coreManagementMaxFocusIndex(platforms: List<PlatformCoreRow>): Int {
    val items = buildCoreManagementItems(platforms)
    return createCoreManagementLayout(items).maxFocusIndex(Unit)
}

internal fun coreManagementItemAtFocusIndex(
    index: Int,
    platforms: List<PlatformCoreRow>
): CoreManagementItem? {
    val items = buildCoreManagementItems(platforms)
    return createCoreManagementLayout(items).itemAtFocusIndex(index, Unit)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreManagementSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val coreState = uiState.coreManagement

    val layoutInfo = remember(coreState.platforms) {
        createCoreManagementLayoutInfo(coreState.platforms)
    }

    FocusedScroll(
        listState = listState,
        focusedIndex = layoutInfo.layout.focusToListIndex(coreState.focusedPlatformIndex, Unit)
    )

    if (coreState.platforms.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.settings_shell_coremanagement_no_platforms),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val visibleItems = remember(layoutInfo) { layoutInfo.layout.visibleItems(Unit) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        items(visibleItems.size, key = { visibleItems[it].key }) { index ->
            when (val item = visibleItems[index]) {
                CoreManagementItem.Header -> {
                    Text(
                        text = stringResource(R.string.settings_shell_coremanagement_header),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.spacingSm)
                    )
                }
                is CoreManagementItem.Platform -> {
                    val platformFocusIndex = layoutInfo.layout.focusIndexOf(item, Unit)
                    val isPlatformFocused = platformFocusIndex == coreState.focusedPlatformIndex

                    PlatformCoreRowItem(
                        platform = item.row,
                        isPlatformFocused = isPlatformFocused,
                        focusedCoreIndex = if (isPlatformFocused) coreState.focusedCoreIndex else item.row.activeCoreIndex,
                        isOnline = coreState.isOnline,
                        downloadingCoreId = coreState.downloadingCoreId,
                        onCoreClick = { coreIndex ->
                            viewModel.selectCoreAt(platformFocusIndex, coreIndex)
                        }
                    )
                }
            }
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
    onCoreClick: (Int) -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isPlatformFocused) {
                    focusAccent.copy(alpha = 0.15f)
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
                lerp(focusAccent, Color.White, 0.45f)
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
                    onClick = { onCoreClick(index) }
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
    val theme = LocalArgosyTheme.current
    val semanticColors = LocalLauncherTheme.current.semanticColors
    val focusedContent = lerp(theme.focusAccent, Color.White, 0.45f)
    val statusColor = when {
        core.isActive -> semanticColors.success
        core.isInstalled -> semanticColors.info
        isOnline -> LocalArgosyTheme.current.destructive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textColor = when {
        isFocused -> focusedContent
        isPlatformFocused -> statusColor.copy(alpha = 0.8f)
        else -> statusColor.copy(alpha = 0.6f)
    }

    val chipShape = RoundedCornerShape(Dimens.radiusPill)
    val background = when {
        isFocused -> theme.focusAccent.copy(alpha = 0.15f).compositeOver(theme.surfaceElevated)
        core.isActive -> theme.focusAccent.copy(alpha = 0.108f).compositeOver(theme.surfaceElevated)
        else -> theme.surfaceElevated
    }

    Box(
        modifier = Modifier
            .height(Dimens.iconLg - Dimens.spacingXs)
            .clip(chipShape)
            .background(background)
            .border(Dimens.borderThin, if (isFocused) focusedContent else Color.Transparent, chipShape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        CoreChipContent(
            core = core,
            isDownloading = isDownloading,
            textColor = textColor,
            statusColor = statusColor
        )
    }
}

@Composable
private fun CoreChipContent(
    core: CoreChipState,
    isDownloading: Boolean,
    textColor: Color,
    statusColor: Color
) {
    val semanticColors = LocalLauncherTheme.current.semanticColors

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
                    contentDescription = stringResource(R.string.settings_shell_coremanagement_active_desc),
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            !core.isInstalled -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.settings_shell_coremanagement_download_required_desc),
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
        if (core.netplaySupported) {
            CoreTag(
                text = stringResource(R.string.settings_shell_coremanagement_netplay_tag),
                color = semanticColors.info
            )
        }
        if (core.updateAvailable && core.isInstalled) {
            CoreTag(
                text = stringResource(R.string.settings_shell_coremanagement_update_tag),
                color = semanticColors.warning
            )
        }
    }
}

@Composable
internal fun CoreTag(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .padding(horizontal = Dimens.spacingXs, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 9.sp
        )
    }
}
