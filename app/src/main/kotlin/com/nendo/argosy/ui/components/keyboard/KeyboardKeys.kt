package com.nendo.argosy.ui.components.keyboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.actionButtonLabelColor
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale

internal data class KeyGeometry(
    val position: KeyPosition,
    val key: KeyboardKey,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val column: Int
)

internal fun keyGeometry(
    rows: List<KeyboardRow>,
    units: Float,
    unitWidth: Dp,
    gap: Dp,
    keyHeight: Dp
): List<KeyGeometry> {
    val step = unitWidth + gap
    return rows.flatMapIndexed { rowIndex, row ->
        val rowUnits = row.keys.sumOf { it.width.toDouble() }.toFloat()
        val y = (keyHeight + gap) * rowIndex
        var x = step * ((units - rowUnits) / 2f)
        var nextColumn = 0
        row.keys.mapIndexedNotNull { index, key ->
            val start = x
            x += step * key.width
            if (key is KeyboardKey.Gap) {
                null
            } else {
                KeyGeometry(
                    position = KeyPosition(rowIndex, index),
                    key = key,
                    x = start,
                    y = y,
                    width = unitWidth * key.width + gap * (key.width - 1f),
                    column = nextColumn++
                )
            }
        }
    }
}

internal val FunctionKey.binding: InputButton
    get() = when (this) {
        FunctionKey.SHIFT -> InputButton.LEFT_STICK_CLICK
        FunctionKey.BACKSPACE -> InputButton.X
        FunctionKey.SPACE -> InputButton.Y
        FunctionKey.SYMBOLS, FunctionKey.LETTERS -> InputButton.LT
        FunctionKey.LAYOUT -> InputButton.SELECT
        FunctionKey.ENTER -> InputButton.START
    }

@StringRes
internal fun FunctionKey.descriptionRes(shift: ShiftState): Int = when (this) {
    FunctionKey.SHIFT -> if (shift == ShiftState.LOCKED) R.string.ui_keyboard_key_caps_lock else R.string.ui_keyboard_key_shift
    FunctionKey.BACKSPACE -> R.string.ui_keyboard_key_backspace
    FunctionKey.SPACE -> R.string.ui_keyboard_key_space
    FunctionKey.SYMBOLS -> R.string.ui_keyboard_key_symbols
    FunctionKey.LETTERS -> R.string.ui_keyboard_key_letters
    FunctionKey.LAYOUT -> R.string.ui_keyboard_key_layout
    FunctionKey.ENTER -> R.string.ui_keyboard_key_enter_default
}

internal fun KeyboardKey.Character.label(shift: ShiftState): String =
    if (shift == ShiftState.OFF) text else shifted ?: text.uppercase()

@Composable
internal fun KeyboardKeyView(
    geometry: KeyGeometry,
    keyHeight: Dp,
    state: KeyboardUiState,
    onKeyTap: (KeyPosition) -> Unit,
    onKeyLongPress: (KeyPosition) -> Unit
) {
    val focused = state.accentStrip == null && !state.pasteFocused && state.focus == geometry.position
    val placement = Modifier
        .offset(geometry.x, geometry.y)
        .size(geometry.width, keyHeight)
    val onTap = { onKeyTap(geometry.position) }
    val onLongPress = { onKeyLongPress(geometry.position) }
    when (val key = geometry.key) {
        is KeyboardKey.Character -> CharacterKey(
            label = key.label(state.shift),
            focused = focused,
            enabled = state.enabled,
            onTap = onTap,
            onLongPress = onLongPress,
            modifier = placement.keySemantics(key.label(state.shift), geometry)
        )
        is KeyboardKey.Function -> if (key.function == FunctionKey.ENTER) {
            EnterKey(state, focused, onTap, onLongPress, placement, geometry)
        } else {
            FunctionKeyView(key.function, state, focused, onTap, onLongPress, placement, geometry)
        }
        is KeyboardKey.Gap -> Unit
    }
}

private fun Modifier.keySemantics(description: String, geometry: KeyGeometry): Modifier =
    clearAndSetSemantics {
        contentDescription = description
        role = Role.Button
        collectionItemInfo = CollectionItemInfo(geometry.position.row, 1, geometry.column, 1)
    }

@Composable
internal fun CharacterKey(
    label: String,
    focused: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    KeySurface(
        fill = theme.surfaceRaised,
        focused = focused,
        enabled = enabled,
        onTap = onTap,
        onLongPress = onLongPress,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun FunctionKeyView(
    function: FunctionKey,
    state: KeyboardUiState,
    focused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    placement: Modifier,
    geometry: KeyGeometry
) {
    val theme = LocalArgosyTheme.current
    val iconTint = if (focused) theme.focusAccent else theme.textDim
    val shiftActive = function == FunctionKey.SHIFT && state.shift != ShiftState.OFF
    KeySurface(
        fill = theme.surfaceElevated,
        focused = focused,
        selected = shiftActive,
        enabled = state.enabled,
        onTap = onTap,
        onLongPress = onLongPress,
        modifier = placement.keySemantics(stringResource(function.descriptionRes(state.shift)), geometry)
    ) {
        InputGlyph(
            button = function.binding,
            tint = if (focused) theme.focusAccent else theme.textPrimary
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        when (function) {
            FunctionKey.SHIFT -> Icon(
                painter = painterResource(
                    if (state.shift == ShiftState.LOCKED) R.drawable.ic_keyboard_shift_locked else R.drawable.ic_keyboard_shift
                ),
                contentDescription = null,
                tint = if (shiftActive) theme.focusAccent else iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
            FunctionKey.BACKSPACE -> Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
            FunctionKey.LAYOUT -> Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
            FunctionKey.SPACE -> FunctionLabel(state.languageLabel, theme.textDim)
            FunctionKey.SYMBOLS -> FunctionLabel(stringResource(R.string.ui_keyboard_label_symbols), theme.textPrimary)
            FunctionKey.LETTERS -> FunctionLabel(stringResource(R.string.ui_keyboard_label_letters), theme.textPrimary)
            FunctionKey.ENTER -> Unit
        }
    }
}

@Composable
private fun EnterKey(
    state: KeyboardUiState,
    focused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    placement: Modifier,
    geometry: KeyGeometry
) {
    val theme = LocalArgosyTheme.current
    val fillAlpha = ComponentDefaults.ActionButton.restFillAlpha *
        if (state.canSubmit) 1f else ComponentDefaults.ActionButton.disabledFillAlpha
    val labelColor = actionButtonLabelColor(focused = focused, primary = true, enabled = state.canSubmit)
    val label = state.submitLabel.ifBlank { stringResource(R.string.ui_keyboard_key_enter_default) }
    KeySurface(
        fill = theme.focusAccent.copy(alpha = fillAlpha),
        focused = focused,
        focusTint = theme.textPrimary,
        enabled = state.enabled,
        onTap = onTap,
        onLongPress = onLongPress,
        modifier = placement.keySemantics(label, geometry)
    ) {
        InputGlyph(button = FunctionKey.ENTER.binding, tint = labelColor)
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        FunctionLabel(label, labelColor)
    }
}

@Composable
private fun FunctionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun KeySurface(
    fill: Color,
    focused: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusTint: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val interaction = remember { MutableInteractionSource() }
    val touch = if (enabled) {
        Modifier.clickableNoFocus(interactionSource = interaction, onClick = onTap, onLongClick = onLongPress)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .clip(shape)
            .background(fill)
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators(fill = true, ring = true),
                selected = selected,
                tint = focusTint,
                shape = shape
            )
            .then(touch)
            .padding(horizontal = Dimens.spacingXs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun AccentStripView(
    strip: AccentStrip,
    anchor: KeyGeometry,
    gridWidth: Dp,
    unitWidth: Dp,
    keyHeight: Dp,
    gap: Dp,
    enabled: Boolean,
    onAccentTap: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val count = strip.options.size
    val stripWidth = unitWidth * count + gap * (count + 1)
    val stripHeight = keyHeight + gap * 2
    val centered = anchor.x + anchor.width / 2 - stripWidth / 2
    val x = centered.coerceIn(0.dp, (gridWidth - stripWidth).coerceAtLeast(0.dp))
    Row(
        modifier = Modifier
            .offset(x, anchor.y - stripHeight)
            .size(stripWidth, stripHeight)
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(theme.surfaceElevated)
            .padding(gap),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        strip.options.forEachIndexed { index, option ->
            CharacterKey(
                label = option,
                focused = index == strip.focused,
                enabled = enabled,
                onTap = { onAccentTap(index) },
                onLongPress = { onAccentTap(index) },
                modifier = Modifier
                    .size(unitWidth, keyHeight)
                    .clearAndSetSemantics {
                        contentDescription = option
                        role = Role.Button
                    }
            )
        }
    }
}
