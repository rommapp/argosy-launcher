package com.nendo.argosy.ui.components.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The controller keyboard, sized from the space it is given. Place it in a box that fills the
 * window: a docked sheet sits at the bottom center and the space above it reports onDismissTap;
 * a window too short for a docked sheet, or FULL_SCREEN placement, fills the whole box.
 */
@Composable
fun ControllerKeyboardSheet(
    state: KeyboardUiState,
    placement: KeyboardPlacement,
    onKeyTap: (KeyPosition) -> Unit,
    onKeyLongPress: (KeyPosition) -> Unit,
    onAccentTap: (Int) -> Unit,
    onDismissTap: () -> Unit,
    onCaretTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPasteTap: () -> Unit = {}
) {
    val scale = LocalUiScale.current.scale
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val sizing = remember(windowWidth, windowHeight, placement, scale) {
            keyboardSizing(
                windowWidthDp = windowWidth.value / scale,
                windowHeightDp = windowHeight.value / scale,
                forceFull = placement == KeyboardPlacement.FULL_SCREEN
            )
        }
        val full = sizing.placement == KeyboardPlacement.FULL_SCREEN
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (!full) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickableNoFocus(onClick = onDismissTap)
                )
            }
            KeyboardSurface(
                state = state,
                sizing = sizing,
                scale = scale,
                onKeyTap = onKeyTap,
                onKeyLongPress = onKeyLongPress,
                onAccentTap = onAccentTap,
                onCaretTap = onCaretTap,
                onPasteTap = onPasteTap
            )
        }
    }
}

private fun Float.scaledDp(scale: Float): Dp = (this * scale).dp

@Composable
private fun KeyboardSurface(
    state: KeyboardUiState,
    sizing: KeyboardSizing,
    scale: Float,
    onKeyTap: (KeyPosition) -> Unit,
    onKeyLongPress: (KeyPosition) -> Unit,
    onAccentTap: (Int) -> Unit,
    onCaretTap: (Int) -> Unit,
    onPasteTap: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val full = sizing.placement == KeyboardPlacement.FULL_SCREEN
    val padding = sizing.paddingDp.scaledDp(scale)
    val gap = sizing.gapDp.scaledDp(scale)
    val sheetWidth = sizing.sheetWidthDp.scaledDp(scale)
    val shape = if (full) {
        RectangleShape
    } else {
        RoundedCornerShape(topStart = Dimens.radiusPanel, topEnd = Dimens.radiusPanel)
    }
    val hairline = theme.hairlineLow
    val hairlineWidth = Dimens.borderThin
    val edge = if (full) {
        Modifier
    } else {
        Modifier.drawBehind {
            val stroke = hairlineWidth.toPx()
            drawLine(hairline, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
        }
    }
    Column(
        modifier = Modifier
            .width(sheetWidth)
            .onSizeChanged { ControllerKeyboard.reportDockedHeight(if (full) 0 else it.height) }
            .then(if (full) Modifier.fillMaxHeight() else Modifier)
            .clip(shape)
            .background(theme.surfaceBase)
            .then(edge)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(padding, if (full) Alignment.Bottom else Alignment.Top)
    ) {
        KeyboardEcho(
            state = state,
            compact = sizing.compact,
            multiline = full,
            height = sizing.echoHeightDp.scaledDp(scale),
            onCaretTap = onCaretTap,
            onPasteTap = onPasteTap
        )
        KeyboardGrid(
            state = state,
            width = sheetWidth - padding * 2,
            keyHeight = sizing.keyHeightDp.scaledDp(scale),
            gap = gap,
            onKeyTap = onKeyTap,
            onKeyLongPress = onKeyLongPress,
            onAccentTap = onAccentTap
        )
    }
}

@Composable
private fun KeyboardGrid(
    state: KeyboardUiState,
    width: Dp,
    keyHeight: Dp,
    gap: Dp,
    onKeyTap: (KeyPosition) -> Unit,
    onKeyLongPress: (KeyPosition) -> Unit,
    onAccentTap: (Int) -> Unit
) {
    val units = state.units
    val unitWidth = (width - gap * (units - 1f)) / units
    val keys = remember(state.rows, units, unitWidth, gap, keyHeight) {
        keyGeometry(state.rows, units, unitWidth, gap, keyHeight)
    }
    val rowCount = state.rows.size
    val columnCount = state.rows.maxOfOrNull { row -> row.keys.count { it !is KeyboardKey.Gap } } ?: 0
    val gridHeight = keyHeight * rowCount + gap * (rowCount - 1).coerceAtLeast(0)
    val alpha = if (state.enabled) 1f else ComponentDefaults.QuickPanel.disabledContentAlpha
    Box(
        modifier = Modifier
            .size(width, gridHeight)
            .alpha(alpha)
            .semantics { collectionInfo = CollectionInfo(rowCount, columnCount) }
    ) {
        keys.forEach { geometry ->
            key(geometry.position) {
                KeyboardKeyView(
                    geometry = geometry,
                    keyHeight = keyHeight,
                    state = state,
                    onKeyTap = onKeyTap,
                    onKeyLongPress = onKeyLongPress
                )
            }
        }
        val strip = state.accentStrip
        val anchor = strip?.let { keys.firstOrNull { it.position == strip.anchor } }
        if (strip != null && anchor != null) {
            AccentStripView(
                strip = strip,
                anchor = anchor,
                gridWidth = width,
                unitWidth = unitWidth,
                keyHeight = keyHeight,
                gap = gap,
                enabled = state.enabled,
                onAccentTap = onAccentTap
            )
        }
    }
}
