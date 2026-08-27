package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.nendo.argosy.util.formatBytes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

data class MenuLayoutState(
    val hasDescription: Boolean = false,
    val hasScreenshots: Boolean = false,
    val hasAchievements: Boolean = false,
    val hasSocialAccount: Boolean = false,
    val hasSaveSync: Boolean = false,
    val hasRelated: Boolean = false,
    val hasPerGameSettings: Boolean = false
)

sealed class MenuItem(
    val key: String,
    val visibleWhen: (MenuLayoutState) -> Boolean = { true }
) {
    data object Play : MenuItem("play")
    data object Saves : MenuItem("saves", visibleWhen = { it.hasSaveSync })
    data object Favorite : MenuItem("favorite")
    data object Privacy : MenuItem("privacy", visibleWhen = { it.hasSocialAccount })
    data object PerGameSettings : MenuItem("per_game_settings", visibleWhen = { it.hasPerGameSettings })
    data object Options : MenuItem("options")
    data object Details : MenuItem("details")
    data object Description : MenuItem("description", visibleWhen = { it.hasDescription })
    data object Screenshots : MenuItem("screenshots", visibleWhen = { it.hasScreenshots })
    data object Achievements : MenuItem("achievements", visibleWhen = { it.hasAchievements })
    data object RelatedGames : MenuItem("related", visibleWhen = { it.hasRelated })

    companion object {
        val ALL: List<MenuItem>
            get() = listOf(Play, Saves, Favorite, Privacy, PerGameSettings, Options, Details, Description, Screenshots, Achievements, RelatedGames)
    }
}

val menuLayout = SettingsLayout<MenuItem, MenuLayoutState>(
    allItems = MenuItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { null }
)

data class GameDetailMenuState(
    val focusedIndex: Int = 0,
    val downloadStatus: GameDownloadStatus = GameDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val isFavorite: Boolean = false,
    val saveStatus: SaveStatusInfo? = null,
    val isSyncingSaves: Boolean = false,
    val downloadSizeBytes: Long? = null,
    val isPrivate: Boolean = false
)

@Composable
fun GameDetailMenu(
    layoutState: MenuLayoutState,
    displayState: GameDetailMenuState,
    onItemClick: (MenuItem) -> Unit,
    onFocusChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val visibleItems = menuLayout.visibleItems(layoutState)
    val listState = rememberLazyListState()

    LaunchedEffect(displayState.focusedIndex, visibleItems) {
        val position = visibleItems.indexOfFirst {
            menuLayout.focusIndexOf(it, layoutState) == displayState.focusedIndex
        }
        if (position < 0) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }
        val fullyVisible = itemInfo != null &&
            itemInfo.offset >= layoutInfo.viewportStartOffset &&
            itemInfo.offset + itemInfo.size <= layoutInfo.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(position)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .padding(end = if (isCompact) Dimens.spacingXs else Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        horizontalAlignment = if (isCompact) Alignment.CenterHorizontally else Alignment.Start
    ) {

        items(visibleItems, key = { it.key }) { item ->
            val focusIndex = menuLayout.focusIndexOf(item, layoutState)
            val isFocused = focusIndex == displayState.focusedIndex

            when (item) {
                MenuItem.Play -> {
                    PlayMenuItem(
                        downloadStatus = displayState.downloadStatus,
                        downloadProgress = displayState.downloadProgress,
                        isFocused = isFocused,
                        downloadSizeBytes = displayState.downloadSizeBytes,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Saves -> {
                    SavesMenuItem(
                        status = displayState.saveStatus,
                        isSyncing = displayState.isSyncingSaves,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Favorite -> {
                    FavoriteMenuItem(
                        isFavorite = displayState.isFavorite,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Privacy -> {
                    PrivacyMenuItem(
                        isPrivate = displayState.isPrivate,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.PerGameSettings -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_per_game_settings),
                        icon = Icons.Default.Settings,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Options -> {
                    OptionsMenuItem(
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                }

                MenuItem.Details -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_details),
                        icon = Icons.Default.Info,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Description -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_description),
                        icon = Icons.Default.Description,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Screenshots -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_screenshots),
                        icon = Icons.Default.Image,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.Achievements -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_achievements),
                        icon = Icons.Default.EmojiEvents,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }

                MenuItem.RelatedGames -> {
                    IconTextMenuItem(
                        label = stringResource(R.string.gamedetail_menu_related_games),
                        icon = Icons.Default.Gamepad,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(focusIndex); onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayMenuItem(
    downloadStatus: GameDownloadStatus,
    downloadProgress: Float,
    isFocused: Boolean,
    downloadSizeBytes: Long?,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val label = when (downloadStatus) {
        GameDownloadStatus.EXTRACTING ->
            stringResource(R.string.gamedetail_menu_play_button_extracting)
        GameDownloadStatus.DOWNLOADING -> stringResource(
            R.string.gamedetail_menu_play_button_progress_percent,
            (downloadProgress * 100).toInt()
        )
        GameDownloadStatus.QUEUED ->
            stringResource(R.string.gamedetail_menu_play_button_queued)
        GameDownloadStatus.WAITING_FOR_STORAGE ->
            stringResource(R.string.gamedetail_menu_play_button_no_space)
        GameDownloadStatus.PAUSED ->
            stringResource(R.string.gamedetail_menu_play_button_resume)
        GameDownloadStatus.FAILED ->
            stringResource(R.string.gamedetail_menu_play_button_retry)
        GameDownloadStatus.DOWNLOADED ->
            stringResource(R.string.gamedetail_menu_play_button_play)
        GameDownloadStatus.NEEDS_INSTALL ->
            stringResource(R.string.gamedetail_menu_play_button_install)
        GameDownloadStatus.NOT_DOWNLOADED ->
            stringResource(R.string.gamedetail_menu_play_button_download)
    }

    val isFailed = downloadStatus == GameDownloadStatus.FAILED
    val baseContainerColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val baseContentColor = if (isFailed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    val containerColor = if (isFocused) {
        baseContainerColor
    } else {
        baseContainerColor.copy(alpha = 0.5f)
    }

    val contentColor = if (isFocused) {
        baseContentColor
    } else {
        baseContentColor.copy(alpha = 0.7f)
    }

    val icon = when (downloadStatus) {
        GameDownloadStatus.DOWNLOADED -> Icons.Default.PlayArrow
        GameDownloadStatus.NEEDS_INSTALL -> Icons.Default.InstallMobile
        GameDownloadStatus.QUEUED -> Icons.Default.Schedule
        GameDownloadStatus.PAUSED -> Icons.Default.Pause
        GameDownloadStatus.WAITING_FOR_STORAGE -> Icons.Default.Warning
        else -> Icons.Default.Download
    }

    val isInProgress = downloadStatus in listOf(
        GameDownloadStatus.QUEUED,
        GameDownloadStatus.WAITING_FOR_STORAGE,
        GameDownloadStatus.DOWNLOADING,
        GameDownloadStatus.EXTRACTING,
        GameDownloadStatus.PAUSED
    )
    val isProgressActionable = downloadStatus == GameDownloadStatus.PAUSED ||
        downloadStatus == GameDownloadStatus.WAITING_FOR_STORAGE
    val showDownloadSize = downloadStatus == GameDownloadStatus.NOT_DOWNLOADED && downloadSizeBytes != null

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isInProgress) {
            val animatedProgress by animateFloatAsState(
                targetValue = downloadProgress,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                label = "btn_fill"
            )
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val fillColor = containerColor
            val progressContentColor = if (downloadStatus == GameDownloadStatus.WAITING_FOR_STORAGE) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onPrimary
            }

            Box(
                modifier = (if (isCompact) Modifier else Modifier.fillMaxWidth())
                    .height(40.dp)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .drawBehind {
                        drawRect(trackColor)
                        drawRect(fillColor, size = size.copy(width = size.width * animatedProgress))
                    }
                    .then(
                        if (isProgressActionable) Modifier.clickableNoFocus(onClick = onClick)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(
                        if (isCompact) PaddingValues(Dimens.spacingSm) else ButtonDefaults.ContentPadding
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = progressContentColor,
                        modifier = Modifier.size(if (isCompact) Dimens.iconMd else Dimens.iconSm)
                    )
                    if (!isCompact) {
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = progressContentColor
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(Dimens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                contentPadding = if (isCompact) {
                    PaddingValues(Dimens.spacingSm)
                } else {
                    ButtonDefaults.ContentPadding
                },
                modifier = (if (isCompact) Modifier else Modifier.fillMaxWidth()).focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isCompact) Dimens.iconMd else Dimens.iconSm)
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    if (showDownloadSize) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = formatBytes(downloadSizeBytes!!),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SavesMenuItem(
    status: SaveStatusInfo?,
    isSyncing: Boolean,
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val effectiveStatus = status?.status ?: SaveSyncStatus.NO_SAVE
    val needsAttention = effectiveStatus == SaveSyncStatus.LOCAL_NEWER ||
        effectiveStatus == SaveSyncStatus.LOCAL_ONLY ||
        effectiveStatus == SaveSyncStatus.PENDING_UPLOAD
    val icon = if (isSyncing) Icons.Default.Sync else effectiveStatus.icon

    val iconTint = when {
        isSyncing || needsAttention -> MaterialTheme.colorScheme.secondary
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = when {
        needsAttention -> MaterialTheme.colorScheme.secondary
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textStyle = if (isFocused || needsAttention) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val slotName = if (isSyncing) {
        stringResource(R.string.gamedetail_menu_saves_syncing)
    } else {
        status?.channelName ?: stringResource(R.string.gamedetail_menu_saves_default_channel)
    }
    val saveDate = if (!isSyncing) status?.displayTime(LocalContext.current) else null

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isEnabled = !isSyncing,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = slotName,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = slotName, style = textStyle, color = textColor)
                    if (saveDate != null) {
                        Text(
                            text = saveDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = icon,
                    contentDescription = slotName,
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun FavoriteMenuItem(
    isFavorite: Boolean,
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val favoriteColor = MaterialTheme.colorScheme.error
    val softRed = favoriteColor.copy(alpha = 0.6f)

    val iconTint = when {
        isFavorite -> favoriteColor
        isFocused -> softRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder

    val textColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isFocused) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = if (isFavorite) {
                    stringResource(R.string.gamedetail_menu_favorite_compact_remove_description)
                } else {
                    stringResource(R.string.gamedetail_menu_favorite_compact_add_description)
                },
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.gamedetail_menu_favorite_label),
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = if (isFavorite) {
                        stringResource(R.string.gamedetail_menu_favorite_row_remove_description)
                    } else {
                        stringResource(R.string.gamedetail_menu_favorite_row_add_description)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun PrivacyMenuItem(
    isPrivate: Boolean,
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val iconTint = when {
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = if (isPrivate) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
    val label = if (isPrivate) {
        stringResource(R.string.gamedetail_menu_privacy_private)
    } else {
        stringResource(R.string.gamedetail_menu_privacy_public)
    }

    val textColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isFocused) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun OptionsMenuItem(
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val iconTint = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isFocused) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription =
                    stringResource(R.string.gamedetail_menu_options_compact_description),
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.gamedetail_menu_options_label),
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription =
                        stringResource(R.string.gamedetail_menu_options_row_description),
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun IconTextMenuItem(
    label: String,
    icon: ImageVector,
    isFocused: Boolean,
    isCompact: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textStyle = if (isFocused && isEnabled) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused && isEnabled,
        isEnabled = isEnabled,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun MenuItemWithLeftBorder(
    isFocused: Boolean,
    isEnabled: Boolean = true,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }

    Box(
        modifier = Modifier
            .then(if (isCompact) Modifier else Modifier.fillMaxWidth())
            .then(
                if (isFocused) {
                    Modifier.drawBehind {
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset.Zero,
                            size = size.copy(width = borderWidthPx)
                        )
                    }
                } else Modifier
            )
            .then(
                if (isEnabled) Modifier.clickableNoFocus(onClick = onClick)
                else Modifier
            )
            .padding(
                start = Dimens.spacingMd,
                end = if (isCompact) Dimens.spacingSm else 0.dp,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingSm
            ),
        contentAlignment = if (isCompact) Alignment.Center else Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
internal fun EndWeightedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val processed = preprocessPlatformName(text)
    val words = processed.split(" ")
    if (words.size <= 2) {
        Text(text = processed, style = style, color = color, modifier = modifier)
        return
    }

    val lines = buildEndWeightedLines(words)
    Column(modifier = modifier) {
        lines.forEach { line ->
            Text(
                text = line,
                style = style,
                color = color,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private val KEEP_TOGETHER_FRAGMENTS = listOf(
    "Game Boy",
    "Neo Geo",
    "PC Engine",
    "Master System",
    "Mega Drive",
    "Game Gear",
    "Sega CD",
    "Sega Saturn",
    "Virtual Boy",
    "Wii U",
    "Xbox 360",
    "Xbox One",
    "Series X"
)

private fun preprocessPlatformName(text: String): String {
    var result = text
    for (fragment in KEEP_TOGETHER_FRAGMENTS) {
        result = result.replace(fragment, fragment.replace(" ", "\u00A0"))
    }
    return result
}

private fun buildEndWeightedLines(words: List<String>): List<String> {
    if (words.size <= 1) return words

    var bestSplit = 1
    var bestDiff = Int.MAX_VALUE

    for (splitAt in 1 until words.size) {
        val firstLen = words.subList(0, splitAt).sumOf { it.length } + splitAt - 1
        val lastLen = words.subList(splitAt, words.size).sumOf { it.length } + (words.size - splitAt - 1)

        if (lastLen >= firstLen) {
            val diff = lastLen - firstLen
            if (diff < bestDiff) {
                bestDiff = diff
                bestSplit = splitAt
            }
        }
    }

    val firstPart = words.subList(0, bestSplit).joinToString("\u00A0")
    val lastPart = words.subList(bestSplit, words.size).joinToString("\u00A0")

    return listOf(firstPart, lastPart)
}

