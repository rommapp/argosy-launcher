package com.nendo.argosy.ui.screens.syncmonitor

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTime

@Composable
fun SyncMonitorScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    viewModel: SyncMonitorViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) { viewModel.createInputHandler(onBack = onBack) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SYNC_MONITOR)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SYNC_MONITOR)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val theme = LocalArgosyTheme.current
    val reduced = reducedMotion()

    LaunchedEffect(uiState.focusedIndex, uiState.rows.size) {
        if (uiState.hasRows) listState.animateScrollToItemCentered(uiState.focusedIndex, reduced)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg)) {
            SyncMonitorHeader(uiState)

            Spacer(Modifier.height(Dimens.spacingMd))

            if (!uiState.hasRows) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.isConnected) {
                            stringResource(R.string.syncmonitor_empty_idle)
                        } else {
                            stringResource(R.string.syncmonitor_empty_disconnected)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingLg)),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    itemsIndexed(
                        uiState.enabledRows,
                        key = { _, row -> row.platformId }
                    ) { index, row ->
                        PlatformRow(
                            row = row,
                            focused = index == uiState.focusedIndex,
                            actionEnabled = uiState.canSyncRow(row),
                            onClick = { viewModel.focusRow(index) },
                            onAction = {
                                viewModel.focusRow(index)
                                viewModel.activateFocusedRow()
                            }
                        )
                    }

                    if (uiState.disabledRows.isNotEmpty()) {
                        item(key = "disabled-header") {
                            Text(
                                text = stringResource(R.string.syncmonitor_group_disabled),
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.textMute,
                                modifier = Modifier.padding(
                                    start = Dimens.spacingSm,
                                    top = Dimens.spacingMd,
                                    bottom = Dimens.spacingXs
                                )
                            )
                        }
                        itemsIndexed(
                            uiState.disabledRows,
                            key = { _, row -> row.platformId }
                        ) { index, row ->
                            val listIndex = uiState.enabledRows.size + index
                            PlatformRow(
                                row = row,
                                focused = listIndex == uiState.focusedIndex,
                                actionEnabled = true,
                                onClick = { viewModel.focusRow(listIndex) },
                                onAction = {
                                    viewModel.focusRow(listIndex)
                                    viewModel.activateFocusedRow()
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterHints(
                hints = buildHints(uiState),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> viewModel.activateFocusedRow()
                        InputButton.Y -> viewModel.syncAll()
                        InputButton.B -> onBack()
                        else -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun SyncMonitorHeader(state: SyncMonitorUiState) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.syncmonitor_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = headerSubtitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.failedCount > 0) warningColor() else theme.textDim
        )
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = stringResource(
                R.string.syncmonitor_library_summary,
                state.rows.size,
                state.totalGames,
                state.totalDownloaded
            ),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMute
        )
        if (state.isSyncing) {
            Spacer(Modifier.height(Dimens.spacingSm))
            if (state.enabledRows.any { it.gamesTotal > 0 }) {
                InterpolatedProgressBar(
                    target = state.passFraction,
                    style = ProgressBarStyle.Active,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ArgosyProgressBar(
                    progress = null,
                    style = ProgressBarStyle.Working,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun headerSubtitle(state: SyncMonitorUiState): String = when {
    state.isSyncing ->
        state.rows.getOrNull(state.activeIndex)?.name
            ?: stringResource(R.string.syncmonitor_progress_starting)
    state.failedCount > 0 ->
        stringResource(R.string.syncmonitor_finished_with_failures, state.failedCount)
    !state.isConnected -> stringResource(R.string.syncmonitor_not_connected)
    state.lastSyncedAt != null -> stringResource(
        R.string.syncmonitor_last_synced,
        formatRelativeTime(LocalContext.current, state.lastSyncedAt)
    )
    else -> stringResource(R.string.syncmonitor_never_synced)
}

@Composable
private fun PlatformRow(
    row: SyncMonitorRow,
    focused: Boolean,
    actionEnabled: Boolean,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val background = when {
        focused -> theme.focusAccent.copy(alpha = 0.15f)
        row.state == PlatformSyncState.SYNCING -> theme.surfaceElevated
        else -> theme.surfaceElevated.copy(alpha = 0.6f)
    }
    val context = LocalContext.current
    val iconUri = remember(row.slug) { PlatformIconAssets.resolveAssetUri(context, row.slug) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeightLg)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background)
            .clickableNoFocus(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconUri != null) {
                AsyncImage(
                    model = iconUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(Dimens.iconLg)
                )
            } else {
                Box(
                    modifier = Modifier.size(Dimens.iconLg),
                    contentAlignment = Alignment.Center
                ) {
                    StateIcon(row.state)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.syncEnabled) theme.textPrimary else theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rowSubtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor(row),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            RowTrailing(row)

            ActionPill(row = row, focused = focused, enabled = actionEnabled, onClick = onAction)
        }

        if (row.state == PlatformSyncState.SYNCING) {
            InterpolatedProgressBar(
                target = if (row.gamesTotal > 0) row.gamesDone.toFloat() / row.gamesTotal else 0f,
                style = ProgressBarStyle.Active,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ActionPill(
    row: SyncMonitorRow,
    focused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val tint = when {
        !enabled -> theme.textMute
        focused -> theme.focusAccent
        else -> theme.textDim
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(tint.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (row.syncEnabled) Icons.Default.Sync else Icons.Default.Check,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = if (row.syncEnabled) {
                stringResource(R.string.syncmonitor_action_sync)
            } else {
                stringResource(R.string.syncmonitor_action_enable)
            },
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun rowSubtitle(row: SyncMonitorRow): String = when {
    !row.syncEnabled -> stringResource(
        R.string.syncmonitor_row_excluded,
        row.games,
        row.downloaded
    )
    row.state == PlatformSyncState.QUEUED -> stringResource(R.string.syncmonitor_state_queued)
    row.state == PlatformSyncState.SYNCING && row.gamesTotal > 0 -> stringResource(
        R.string.syncmonitor_state_syncing,
        row.gamesDone,
        row.gamesTotal
    )
    row.state == PlatformSyncState.SYNCING -> stringResource(R.string.syncmonitor_progress_starting)
    row.state == PlatformSyncState.FAILED ->
        row.error ?: stringResource(R.string.syncmonitor_state_failed)
    else -> stringResource(
        R.string.syncmonitor_row_summary,
        row.games,
        row.downloaded,
        row.withSaves
    )
}

@Composable
private fun subtitleColor(row: SyncMonitorRow): Color {
    val theme = LocalArgosyTheme.current
    return when {
        !row.syncEnabled -> theme.textMute
        row.state == PlatformSyncState.SYNCING -> progressColor()
        row.state == PlatformSyncState.FAILED -> warningColor()
        else -> theme.textDim
    }
}

@Composable
private fun successColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.success
    } else {
        ColorTokens.Semantic.Light.success
    }

@Composable
private fun warningColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.warning
    } else {
        ColorTokens.Semantic.Light.warning
    }

@Composable
private fun infoColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.info
    } else {
        ColorTokens.Semantic.Light.info
    }

@Composable
private fun progressColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.progress
    } else {
        ColorTokens.Semantic.Light.progress
    }

@Composable
private fun stateColor(state: PlatformSyncState): Color {
    val theme = LocalArgosyTheme.current
    return when (state) {
        PlatformSyncState.IDLE -> theme.textDim
        PlatformSyncState.QUEUED -> theme.textMute
        PlatformSyncState.SYNCING -> progressColor()
        PlatformSyncState.DONE -> successColor()
        PlatformSyncState.ALREADY_SYNCED -> theme.textMute
        PlatformSyncState.FAILED -> warningColor()
    }
}

@Composable
private fun StateIcon(state: PlatformSyncState) {
    Icon(
        imageVector = when (state) {
            PlatformSyncState.IDLE, PlatformSyncState.QUEUED -> Icons.Default.Schedule
            PlatformSyncState.SYNCING -> Icons.Default.Sync
            PlatformSyncState.DONE -> Icons.Default.CheckCircle
            PlatformSyncState.ALREADY_SYNCED -> Icons.Default.Check
            PlatformSyncState.FAILED -> Icons.Default.ErrorOutline
        },
        contentDescription = null,
        tint = stateColor(state),
        modifier = Modifier.size(Dimens.iconSm)
    )
}

@Composable
private fun RowTrailing(row: SyncMonitorRow) {
    if (row.state != PlatformSyncState.DONE) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.added > 0) CountBadge(R.string.syncmonitor_badge_added, row.added, successColor())
        if (row.updated > 0) {
            CountBadge(R.string.syncmonitor_badge_updated, row.updated, infoColor())
        }
        if (row.removed > 0) {
            CountBadge(R.string.syncmonitor_badge_removed, row.removed, warningColor())
        }
    }
}

@Composable
private fun CountBadge(labelRes: Int, count: Int, color: Color) {
    Text(
        text = stringResource(labelRes, count),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

/**
 * Eases toward each new value instead of jumping to it, so a bar fed by network responses reads
 * as motion rather than as a series of steps. It never runs ahead of the value it was given.
 */
@Composable
private fun InterpolatedProgressBar(
    target: Float,
    style: ProgressBarStyle,
    modifier: Modifier = Modifier
) {
    val animatable = remember { Animatable(target) }
    LaunchedEffect(target) {
        animatable.animateTo(targetValue = target, animationSpec = MotionTokens.Spring.focusReduced)
    }
    ArgosyProgressBar(progress = animatable.value, style = style, modifier = modifier)
}

@Composable
private fun buildHints(state: SyncMonitorUiState): List<Pair<InputButton, String>> {
    val syncAllLabel = stringResource(R.string.syncmonitor_footer_sync_all)
    val syncOneLabel = stringResource(R.string.syncmonitor_action_sync)
    val enableLabel = stringResource(R.string.syncmonitor_action_enable)
    val backLabel = stringResource(R.string.syncmonitor_footer_back)
    val focused = state.focusedRow
    return buildList {
        when {
            focused != null && !focused.syncEnabled -> add(InputButton.A to enableLabel)
            state.canSyncFocused -> add(InputButton.A to syncOneLabel)
        }
        if (state.canSyncAll) add(InputButton.Y to syncAllLabel)
        add(InputButton.B to backLabel)
    }
}
