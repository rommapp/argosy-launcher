package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticClick
import androidx.compose.ui.semantics.onLongClick as semanticLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.input.CapturingInputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.horizontalEdgeFade
import com.nendo.argosy.ui.util.touchOnly

const val APP_BAR_NOTHING_FOCUSED = -2
const val APP_BAR_DRAWER_INDEX = -1

data class DisplayFocusTarget(val displayId: Int, val number: Int)

@Composable
fun CompanionAppBar(
    apps: List<String>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedIndex: Int = APP_BAR_NOTHING_FOCUSED,
    onAppLongPress: ((String) -> Unit)? = null,
    onOpenDrawer: () -> Unit = {},
    onKeyboardToggle: (() -> Unit)? = null,
    focusDisplays: List<DisplayFocusTarget> = emptyList(),
    focusPickerOpen: Boolean = false,
    focusPickerIndex: Int = 0,
    onFocusPickerToggle: (() -> Unit)? = null,
    onFocusPickerMove: (Int) -> Unit = {},
    onFocusDisplay: (Int) -> Unit = {},
    onSwapRoles: (() -> Unit)? = null,
    toolsOpen: Boolean = false,
    toolIndex: Int = 1,
    onToolsToggle: () -> Unit = {},
    onToolFocus: (Int) -> Unit = {},
    onToolsDismiss: () -> Unit = {},
    controllerInputEnabled: Boolean = true,
    maximumWidth: Dp = Dimens.breadcrumbMaxWidth
) {
    val uiScale = LocalUiScale.current
    val dockScale = maxOf(uiScale.scale, minimumTouchTarget / DimensionTokens.Icon.xl.dp)
    CompositionLocalProvider(LocalUiScale provides uiScale.copy(scale = dockScale)) {
        val listState = rememberLazyListState()
        val appIconSize = Dimens.iconXl
        val controlIconSize = Dimens.iconMd
        val slot = maxOf(minimumTouchTarget, appIconSize)
        val gap = Dimens.spacingSm
        val padding = Dimens.spacingMd
        val height = slot + gap * 2
        val caretRotation by animateFloatAsState(
            targetValue = if (toolsOpen) 180f else 0f,
            animationSpec = tween(Motion.durationMicro),
            label = "dock-caret"
        )

        LaunchedEffect(focusedIndex, apps.size) {
            if (focusedIndex in apps.indices) listState.animateScrollToItem(focusedIndex)
        }

        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val widths = dockWidths(
                maximum = minOf(maxWidth, maximumWidth).value,
                appCount = apps.size,
                slot = slot.value,
                gap = gap.value,
                padding = padding.value,
                appIconSize = appIconSize.value,
                controlIconSize = controlIconSize.value
            )
            Row(
                modifier = Modifier
                    .width(widths.width.dp)
                    .height(height)
                    .frostedSurface()
                    .padding(horizontal = widths.padding.dp, vertical = gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockControl(slot, focusedIndex == APP_BAR_DRAWER_INDEX, onClick = onOpenDrawer) { tint ->
                    Icon(
                        Icons.Default.Apps,
                        stringResource(R.string.dual_home_hint_app_bar_all_apps),
                        tint = tint,
                        modifier = Modifier.size(controlIconSize)
                    )
                }
                Spacer(Modifier.width(widths.groupGap.dp))
                if (apps.isNotEmpty()) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.width(widths.appsWidth.dp)
                            .horizontalEdgeFade(listState, fadeWidth = padding),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        userScrollEnabled = widths.appsWidth <
                            apps.size * slot.value + (apps.size - 1) * gap.value
                    ) {
                        itemsIndexed(apps, key = { _, app -> app }) { index, app ->
                            CompanionAppItem(
                                packageName = app,
                                isFocused = index == focusedIndex,
                                onClick = { onAppClick(app) },
                                onLongPress = onAppLongPress?.let { press -> { press(app) } }
                            )
                        }
                    }
                    Spacer(Modifier.width(widths.groupGap.dp))
                }
                Box {
                    DockControl(
                        slot,
                        focusedIndex == apps.size || toolsOpen,
                        onClick = {
                            if (toolsOpen && focusPickerOpen) onFocusPickerToggle?.invoke()
                            onToolsToggle()
                        }
                    ) { tint ->
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.dual_companion_app_bar_tools_description),
                            tint = tint,
                            modifier = Modifier.size(controlIconSize)
                                .graphicsLayer { rotationZ = caretRotation }
                        )
                    }
                    if (toolsOpen) {
                        DockToolsPopup(
                            slot = slot,
                            height = height,
                            gap = gap,
                            padding = padding,
                            toolIndex = toolIndex,
                            onToolFocus = onToolFocus,
                            onDismiss = onToolsDismiss,
                            onSwapRoles = onSwapRoles,
                            onKeyboardToggle = onKeyboardToggle,
                            displays = focusDisplays,
                            pickerOpen = focusPickerOpen,
                            pickerIndex = focusPickerIndex,
                            onPickerToggle = onFocusPickerToggle,
                            onPickerMove = onFocusPickerMove,
                            onFocusDisplay = onFocusDisplay,
                            controllerInputEnabled = controllerInputEnabled
                        )
                    }
                }
            }
        }
    }
}

internal class DockPopupPositionProvider(
    private val inset: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = dockPopupLeft(anchorBounds.center.x, popupContentSize.width, windowSize.width, inset),
        y = (anchorBounds.bottom - popupContentSize.height).coerceIn(
            inset.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
            (windowSize.height - popupContentSize.height - inset).coerceAtLeast(inset)
        )
    )
}

@Composable
private fun DockToolsPopup(
    slot: Dp,
    height: Dp,
    gap: Dp,
    padding: Dp,
    toolIndex: Int,
    onToolFocus: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSwapRoles: (() -> Unit)?,
    onKeyboardToggle: (() -> Unit)?,
    displays: List<DisplayFocusTarget>,
    pickerOpen: Boolean,
    pickerIndex: Int,
    onPickerToggle: (() -> Unit)?,
    onPickerMove: (Int) -> Unit,
    onFocusDisplay: (Int) -> Unit,
    controllerInputEnabled: Boolean
) {
    val enabled = listOf(onSwapRoles != null, onKeyboardToggle != null,
        onPickerToggle != null && displays.size > 1)
    val dismiss = {
        if (pickerOpen) onPickerToggle?.invoke()
        onDismiss()
    }
    val activate: (Int) -> Unit = { index ->
        if (enabled.getOrNull(index) == true) {
            onToolFocus(index)
            when (index) {
                0 -> { dismiss(); onSwapRoles?.invoke() }
                1 -> { dismiss(); onKeyboardToggle?.invoke() }
                2 -> onPickerToggle?.invoke()
            }
        }
    }

    if (controllerInputEnabled) {
        val move by rememberUpdatedState<(Int) -> Unit> { delta ->
            if (pickerOpen) onPickerMove(delta)
            else onToolFocus(dockToolFocusMove(toolIndex, delta, enabled))
        }
        val confirm by rememberUpdatedState<() -> Unit> {
            if (pickerOpen) {
                displays.getOrNull(pickerIndex)?.let { onFocusDisplay(it.displayId) }
                onDismiss()
            } else {
                activate(toolIndex)
            }
        }
        val close by rememberUpdatedState(dismiss)
        val up by rememberUpdatedState<() -> Unit> {
            if (pickerOpen) onPickerMove(-1)
        }
        val down by rememberUpdatedState<() -> Unit> {
            if (pickerOpen) onPickerMove(1) else dismiss()
        }
        val handler = remember {
            object : CapturingInputHandler {
                override fun onLeft(): InputResult { move(-1); return InputResult.HANDLED }
                override fun onRight(): InputResult { move(1); return InputResult.HANDLED }
                override fun onUp(): InputResult { up(); return InputResult.HANDLED }
                override fun onDown(): InputResult { down(); return InputResult.HANDLED }
                override fun onConfirm(): InputResult { confirm(); return InputResult.HANDLED }
                override fun onBack(): InputResult { close(); return InputResult.HANDLED }
            }
        }
        ModalPresenceEffect()
        ModalInputEffect(active = true, handler = handler)
    }

    val density = LocalDensity.current
    val positionProvider = remember(density, padding) {
        with(density) { DockPopupPositionProvider(padding.roundToPx()) }
    }
    val pickerState = rememberLazyListState()
    LaunchedEffect(pickerOpen, pickerIndex, displays.size) {
        if (pickerOpen && pickerIndex in displays.indices) pickerState.animateScrollToItem(pickerIndex)
    }
    val pickerMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - height * 2 - padding * 2)
        .coerceAtLeast(slot)
    val popupWidth = slot * 3 + gap * 2 + padding * 2

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = false, clippingEnabled = true)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            if (pickerOpen) {
                LazyColumn(
                    state = pickerState,
                    modifier = Modifier.width(slot + padding * 2)
                        .heightIn(max = pickerMaxHeight)
                        .frostedSurface(RoundedCornerShape(Dimens.radiusLg))
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(displays, key = { _, target -> target.displayId }) { index, target ->
                        DockControl(slot, index == pickerIndex, onClick = {
                            onFocusDisplay(target.displayId)
                            onDismiss()
                        }) { tint ->
                            Text(target.number.toString(), color = tint,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Spacer(Modifier.height(gap))
            }
            Row(
                modifier = Modifier.width(popupWidth)
                    .height(height).frostedSurface()
                    .padding(horizontal = padding, vertical = gap),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockControl(slot, false, enabled[0], { activate(0) }) { tint ->
                    Icon(painterResource(R.drawable.ic_swap_screens),
                        stringResource(R.string.dual_companion_app_bar_swap_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
                DockControl(slot, false, enabled[1], { activate(1) }) { tint ->
                    Icon(Icons.Default.Keyboard,
                        stringResource(R.string.dual_companion_app_bar_keyboard_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
                DockControl(slot, false, enabled[2], { activate(2) }) { tint ->
                    Icon(Icons.Default.Tv,
                        stringResource(R.string.dual_companion_app_bar_focus_description),
                        tint = tint, modifier = Modifier.size(Dimens.iconMd))
                }
            }
            Spacer(
                Modifier.width(popupWidth)
                    .height(height)
                    .touchOnly(dismiss)
            )
        }
    }
}

@Composable
private fun DockControl(
    slot: Dp,
    focused: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    val tint = if (focused) LocalArgosyTheme.current.focusAccent else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(slot)
            .then(if (enabled) Modifier.touchOnly(onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) semanticClick { onClick(); true } else disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        content(if (enabled) tint else tint.copy(alpha = ComponentDefaults.QuickPanel.disabledContentAlpha))
    }
}

@Composable
internal fun CompanionAppItem(
    packageName: String,
    isFocused: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.size(maxOf(minimumTouchTarget, Dimens.iconXl))
            .let { base ->
                if (onLongPress == null) base.touchOnly(onClick)
                else base.touchOnly(onClick = onClick, onLongPress = onLongPress)
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                semanticClick { onClick(); true }
                if (onLongPress != null) semanticLongClick { onLongPress(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = AppIconData(packageName),
            contentDescription = packageName,
            modifier = Modifier.size(Dimens.iconXl).argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(ring = true),
                shape = RoundedCornerShape(Dimens.radiusSm)
            ),
            contentScale = ContentScale.Fit
        )
    }
}
