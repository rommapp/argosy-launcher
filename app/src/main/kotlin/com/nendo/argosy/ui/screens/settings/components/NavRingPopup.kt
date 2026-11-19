package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.rememberDragReorderState
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.verticalEdgeFade

@Composable
fun NavRingPopup(
    enabled: List<String>,
    focusIndex: Int,
    heldToken: String?,
    onFocus: (Int) -> Unit,
    onToggle: (String) -> Unit,
    onLift: () -> Unit,
    onLiftAt: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
    onDrop: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val rows = remember(enabled) { NavRing.rows(enabled) }

    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> key is String && key in enabled },
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
        title = stringResource(R.string.settings_nav_ring_modal_title),
        subtitle = stringResource(R.string.settings_nav_ring_modal_subtitle),
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
            itemsIndexed(rows, key = { _, token -> token }) { index, token ->
                val page = NavRing.page(token)
                val isEnabled = token in enabled
                RegionPickerItem(
                    name = page?.let { stringResource(it.labelRes) }.orEmpty(),
                    rank = if (isEnabled) index else null,
                    isFocused = focusIndex == index,
                    isSelected = isEnabled,
                    isHeld = heldToken == token,
                    trailingLabel = if (NavRing.isPinned(token)) {
                        stringResource(R.string.settings_nav_ring_pinned)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .then(
                            if (dragState.draggingKey == null || dragState.draggingKey == token) {
                                Modifier
                            } else {
                                Modifier.animateItem()
                            }
                        )
                        .dragReorderItem(dragState, token),
                    onClick = {
                        if (heldToken == null) {
                            onFocus(index)
                            onToggle(token)
                        }
                    }
                )
            }
        }
    }

    FooterHints(
        forced = heldToken != null,
        hints = if (heldToken != null) {
            liftedReorderHints(
                move = stringResource(R.string.settings_nav_ring_hint_move),
                cancel = stringResource(R.string.settings_nav_ring_hint_cancel)
            )
        } else {
            listOf(InputButton.Y to stringResource(R.string.settings_nav_ring_hint_reorder))
        },
        onHintClick = { button ->
            when (button) {
                InputButton.Y -> if (heldToken == null) onLift() else Unit
                InputButton.A -> if (heldToken != null) onDrop() else Unit
                InputButton.B -> onBack()
                else -> Unit
            }
        }
    )
}
