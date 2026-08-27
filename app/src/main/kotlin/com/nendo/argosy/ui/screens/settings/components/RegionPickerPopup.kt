package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.DragReorderState
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.rememberDragReorderState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun RegionPickerPopup(
    regions: List<String>,
    enabledRegions: List<String>,
    focusIndex: Int,
    heldRegion: String?,
    orderingEnabled: Boolean,
    onToggle: (String) -> Unit,
    onLift: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> orderingEnabled && key is String && key in enabledRegions },
        onLift = { key ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLift(key as String)
        },
        onMove = { key, index -> onMoveTo(key as String, index) },
        onDrop = onDrop
    )

    if (dragState.draggingKey == null) {
        FocusedScroll(
            listState = listState,
            focusedIndex = focusIndex
        )
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_region_picker_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (orderingEnabled) {
                    stringResource(R.string.settings_region_picker_subtitle_ordering)
                } else {
                    stringResource(R.string.settings_region_picker_subtitle_toggle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm)
                    .dragReorderContainer(dragState),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(regions, key = { _, region -> region }) { index, region ->
                    val rank = if (orderingEnabled) enabledRegions.indexOf(region) else -1
                    val touchDragActive = dragState.draggingKey != null
                    RegionPickerItem(
                        name = region,
                        rank = rank,
                        showRank = orderingEnabled,
                        isFocused = focusIndex == index,
                        isSelected = region in enabledRegions,
                        isHeld = heldRegion == region,
                        dragState = dragState,
                        modifier = if (touchDragActive && dragState.draggingKey != region) Modifier.animateItem() else Modifier,
                        onClick = { if (heldRegion == null) onToggle(region) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterHints(
                forced = heldRegion != null,
                hints = if (heldRegion != null) {
                    listOf(
                        InputButton.DPAD_VERTICAL to stringResource(R.string.settings_region_picker_hint_move),
                        InputButton.A to stringResource(R.string.settings_region_picker_hint_accept),
                        InputButton.B to stringResource(R.string.settings_region_picker_hint_cancel)
                    )
                } else if (orderingEnabled) {
                    listOf(
                        InputButton.DPAD to stringResource(R.string.settings_region_picker_hint_navigate),
                        InputButton.A to stringResource(R.string.settings_region_picker_hint_toggle),
                        InputButton.X to stringResource(R.string.settings_region_picker_hint_prioritize),
                        InputButton.B to stringResource(R.string.settings_region_picker_hint_close)
                    )
                } else {
                    listOf(
                        InputButton.DPAD to stringResource(R.string.settings_region_picker_hint_navigate),
                        InputButton.A to stringResource(R.string.settings_region_picker_hint_toggle),
                        InputButton.B to stringResource(R.string.settings_region_picker_hint_close)
                    )
                },
                onHintClick = { button ->
                    val focusedRegion = regions.getOrNull(focusIndex)
                    when (button) {
                        InputButton.A -> when {
                            heldRegion != null -> onDrop()
                            focusedRegion != null -> onToggle(focusedRegion)
                            else -> Unit
                        }
                        InputButton.X -> if (focusedRegion != null) onLift(focusedRegion) else Unit
                        InputButton.B -> onDismiss()
                        else -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun RegionPickerItem(
    name: String,
    rank: Int,
    showRank: Boolean,
    isFocused: Boolean,
    isSelected: Boolean,
    isHeld: Boolean,
    dragState: DragReorderState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dragReorderItem(dragState, name)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isHeld -> theme.focusAccent.copy(alpha = 0.3f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                    isFocused -> theme.focusAccent.copy(alpha = 0.15f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .then(
                if (isHeld) Modifier.border(
                    width = Dimens.borderThin,
                    color = theme.focusAccent,
                    shape = RoundedCornerShape(Dimens.radiusMd)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused || isHeld) lerp(theme.focusAccent, Color.White, 0.45f)
                    else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            if (showRank && rank >= 0) {
                Text(
                    text = "${rank + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused || isHeld) lerp(theme.focusAccent, Color.White, 0.45f)
                            else MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isFocused) lerp(theme.focusAccent, Color.White, 0.45f)
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}
