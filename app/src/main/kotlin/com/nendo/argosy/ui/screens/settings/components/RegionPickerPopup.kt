package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade
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
import androidx.compose.runtime.LaunchedEffect
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
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.rememberDragReorderState
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun RegionPickerPopup(
    regions: List<String>,
    enabledRegions: List<String>,
    focusIndex: Int,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    RegionPopupFrame(
        title = stringResource(R.string.settings_region_picker_title),
        subtitle = stringResource(R.string.settings_region_picker_subtitle_toggle),
        onDismiss = onDismiss
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            itemsIndexed(regions, key = { _, region -> region }) { index, region ->
                RegionPickerItem(
                    name = region,
                    rank = null,
                    isFocused = focusIndex == index,
                    isSelected = region in enabledRegions,
                    isHeld = false,
                    onClick = { onToggle(region) }
                )
            }
        }
    }

    FooterHints(
        hints = listOf(
            InputButton.DPAD to stringResource(R.string.settings_region_picker_hint_navigate),
            InputButton.A to stringResource(R.string.settings_region_picker_hint_toggle),
            InputButton.B to stringResource(R.string.settings_region_picker_hint_close)
        ),
        onHintClick = { button ->
            when (button) {
                InputButton.A -> regions.getOrNull(focusIndex)?.let(onToggle) ?: Unit
                InputButton.B -> onDismiss()
                else -> Unit
            }
        }
    )
}

@Composable
fun RegionPriorityPopup(
    order: List<String>,
    focusIndex: Int,
    heldRegion: String?,
    onFocus: (Int) -> Unit,
    onLift: () -> Unit,
    onLiftAt: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
    onDrop: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> key is String && key in order },
        onLift = { key ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLiftAt(key as String)
        },
        onMove = { key, index -> onMoveTo(key as String, index) },
        onDrop = onDrop
    )

    val reduced = reducedMotion()
    LaunchedEffect(focusIndex) {
        if (dragState.draggingKey == null) listState.animateScrollToItemCentered(focusIndex, reduced)
    }

    RegionPopupFrame(
        title = stringResource(R.string.settings_region_priority_title),
        subtitle = stringResource(R.string.settings_region_priority_subtitle),
        onDismiss = onBack
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingLg)
                .dragReorderContainer(dragState),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            itemsIndexed(order, key = { _, region -> region }) { index, region ->
                RegionPickerItem(
                    name = region,
                    rank = index,
                    isFocused = focusIndex == index,
                    isSelected = true,
                    isHeld = heldRegion == region,
                    modifier = Modifier
                        .then(
                            if (dragState.draggingKey == null || dragState.draggingKey == region) {
                                Modifier
                            } else {
                                Modifier.animateItem()
                            }
                        )
                        .dragReorderItem(dragState, region),
                    onClick = { if (heldRegion == null) onFocus(index) }
                )
            }
        }
    }

    FooterHints(
        forced = heldRegion != null,
        hints = if (heldRegion != null) {
            liftedReorderHints(
                move = stringResource(R.string.settings_region_priority_hint_move),
                cancel = stringResource(R.string.settings_region_priority_hint_cancel)
            )
        } else {
            listOf(
                InputButton.Y to stringResource(R.string.settings_region_priority_hint_reorder),
                InputButton.B to stringResource(R.string.settings_region_priority_hint_close)
            )
        },
        onHintClick = { button ->
            when (button) {
                InputButton.Y -> if (heldRegion == null) onLift() else Unit
                InputButton.A -> if (heldRegion != null) onDrop() else Unit
                InputButton.B -> onBack()
                else -> Unit
            }
        }
    )
}

@Composable
internal fun RegionPopupFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalPresenceEffect()
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
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            content()

            Spacer(modifier = Modifier.height(Dimens.spacingSm))
        }
    }
}

@Composable
internal fun RegionPickerItem(
    name: String,
    rank: Int?,
    isFocused: Boolean,
    isSelected: Boolean,
    isHeld: Boolean,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val emphasized = isFocused || isHeld
    val emphasisColor = lerp(theme.focusAccent, Color.White, 0.45f)
    Row(
        modifier = modifier
            .fillMaxWidth()
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
                if (isHeld) {
                    Modifier.border(
                        width = Dimens.borderThin,
                        color = theme.focusAccent,
                        shape = RoundedCornerShape(Dimens.radiusMd)
                    )
                } else {
                    Modifier
                }
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (emphasized) emphasisColor else MaterialTheme.colorScheme.onSurface
        )
        when {
            trailingLabel != null -> Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) emphasisColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            rank != null -> Text(
                text = "${rank + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) emphasisColor else MaterialTheme.colorScheme.primary
            )
            isSelected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) emphasisColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}
