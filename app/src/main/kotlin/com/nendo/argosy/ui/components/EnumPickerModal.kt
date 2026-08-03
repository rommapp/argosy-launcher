package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade

/** V2 enum option picker: self-contained full-list modal, current value pre-focused + marked. */
@Composable
fun EnumPickerModal(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (!visibleState.currentState && !visibleState.targetState) return

    var focusedIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, options.lastIndex)) }
    val optionCount by rememberUpdatedState(options.size)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(visible) {
        if (visible) focusedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusedIndex = (focusedIndex - 1).mod(optionCount)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focusedIndex = (focusedIndex + 1).mod(optionCount)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnSelect(focusedIndex)
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = visible, handler = inputHandler)

    Popup(properties = PopupProperties(focusable = false)) {
        val theme = LocalArgosyTheme.current
        val duration = Motion.durationDrawer / 2
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase)),
            exit = fadeOut(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase)),
        ) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(bottom = gripReserveBottomInset())
                    .clickableNoFocus { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                val maxPanelHeight = maxHeight * 0.85f
                Box(
                    modifier = Modifier
                        .widthIn(max = Dimens.modalWidthLg)
                        .heightIn(max = maxPanelHeight)
                        .clickableNoFocus { },
                ) {
                    GlassPanel {
                        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary,
                            )
                            Spacer(Modifier.height(Dimens.spacingMd))
                            val listState = rememberLazyListState()
                            FocusedScroll(listState = listState, focusedIndex = focusedIndex)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f, fill = false).verticalEdgeFade(listState, fadeHeight = Dimens.spacingMd),
                                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                            ) {
                                itemsIndexed(options, key = { index, _ -> index }) { index, option ->
                                    EnumPickerRow(
                                        label = option,
                                        focused = index == focusedIndex,
                                        selected = index == selectedIndex,
                                        onClick = { onSelect(index) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnumPickerRow(
    label: String,
    focused: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators.ListRow,
                selected = selected,
                shape = shape,
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) lerp(theme.focusAccent, Color.White, 0.45f) else theme.textPrimary,
        )
    }
}
