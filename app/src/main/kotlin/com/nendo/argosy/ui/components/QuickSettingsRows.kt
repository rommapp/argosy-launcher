package com.nendo.argosy.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.ArgosyTrackSlider
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.primitives.SwitchChip
import com.nendo.argosy.ui.primitives.SwitchedTrackSlider
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.primitives.rememberSwitchedPhase
import com.nendo.argosy.ui.primitives.segmentedInlineWidth
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.roundToInt

private val QuickRowFocus = FocusIndicators.ListRow

@Composable
private fun leadingSlotWidth(): Dp =
    (ComponentDefaults.SwitchedSlider.chipSizeDp * LocalUiScale.current.scale).dp

@Composable
private fun quickValueColor(isFocused: Boolean): Color {
    val theme = LocalArgosyTheme.current
    val color by animateColorAsState(
        targetValue = if (isFocused) theme.focusAccent else theme.textDim,
        animationSpec = Motion.focusColorSpec,
        label = "quick-value"
    )
    return color
}

@Composable
fun quickPercentLabel(fraction: Float): String =
    stringResource(R.string.ui_quick_settings_percent, (fraction * 100f).roundToInt())

@Composable
internal fun QuickSectionHeader(title: String) {
    PanelSectionHeader(
        title = title,
        modifier = Modifier.padding(
            start = Dimens.spacingMd,
            end = Dimens.spacingMd,
            top = Dimens.spacingMd,
            bottom = Dimens.spacingXs
        )
    )
}

@Composable
internal fun QuickRowFrame(
    isFocused: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    twoLine: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val click = when {
        onClick == null -> Modifier
        onLongClick != null && enabled -> Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
        else -> Modifier.clickableNoFocus(enabled = enabled, onClick = onClick)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingSm)
            .heightIn(min = if (twoLine) Dimens.menuRowHeightLg else Dimens.menuRowHeight)
            .argosyFocusIndicators(focused = isFocused && enabled, indicators = QuickRowFocus, shape = shape)
            .clip(shape)
            .alpha(if (enabled) 1f else ComponentDefaults.QuickPanel.disabledContentAlpha)
            .then(click)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
private fun QuickLabelLine(
    label: String,
    leading: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(leadingSlotWidth()), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalArgosyTheme.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun QuickRowIcon(icon: ImageVector, isFocused: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = quickValueColor(isFocused),
        modifier = Modifier.size(Dimens.iconMd)
    )
}

@Composable
internal fun QuickValueText(text: String, isFocused: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = quickValueColor(isFocused),
        maxLines = 1
    )
}

@Composable
private fun SecondLine(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = leadingSlotWidth() + Dimens.spacingSm)
    ) {
        content()
    }
}

@Composable
internal fun QuickToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onToggle: (Boolean) -> Unit,
    description: String? = null
) {
    val toggle = {
        onFocus()
        onToggle(!checked)
    }
    QuickRowFrame(isFocused = isFocused, onClick = toggle, twoLine = description != null) {
        QuickLabelLine(
            label = label,
            leading = { QuickRowIcon(icon, isFocused) }
        ) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            ArgosyToggle(checked = checked, onToggle = { toggle() }, focused = isFocused)
        }
        if (description != null) {
            SecondLine {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalArgosyTheme.current.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun QuickStatusRow(
    icon: ImageVector,
    label: String,
    valueText: String
) {
    QuickDetailRow(icon = icon, label = label, isFocused = false, onClick = null, valueText = valueText)
}

@Composable
internal fun QuickDetailRow(
    icon: ImageVector,
    label: String,
    isFocused: Boolean,
    onClick: (() -> Unit)?,
    valueText: String? = null,
    labelWraps: Boolean = false,
    leading: (@Composable (icon: @Composable () -> Unit) -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val rowIcon: @Composable () -> Unit = { QuickRowIcon(icon, isFocused) }
    QuickRowFrame(isFocused = isFocused, onClick = onClick) {
        if (labelWraps) {
            QuickWrappingLabelLine(
                label = label,
                leading = { if (leading == null) rowIcon() else leading(rowIcon) }
            ) {
                if (valueText != null) QuickValueText(valueText, isFocused)
                trailing()
            }
        } else {
            QuickLabelLine(
                label = label,
                leading = { if (leading == null) rowIcon() else leading(rowIcon) }
            ) {
                if (valueText != null) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    QuickValueText(valueText, isFocused)
                }
                trailing()
            }
        }
    }
}

@Composable
private fun QuickWrappingLabelLine(
    label: String,
    leading: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    val gap = Dimens.spacingSm
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(leadingSlotWidth()), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(modifier = Modifier.width(gap))
        Layout(
            content = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalArgosyTheme.current.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            },
            modifier = Modifier.weight(1f)
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val gapPx = gap.roundToPx()
            val trailingPlaceable = measurables[1].measure(Constraints(maxWidth = width))
            val trailingWidth = trailingPlaceable.width
            val labelSingleLine = measurables[0].maxIntrinsicWidth(Constraints.Infinity)
            val sideBySide = trailingWidth == 0 || labelSingleLine + gapPx + trailingWidth <= width
            if (sideBySide) {
                val labelWidth = (width - if (trailingWidth == 0) 0 else gapPx + trailingWidth).coerceAtLeast(0)
                val labelPlaceable = measurables[0].measure(Constraints(maxWidth = labelWidth))
                val height = maxOf(labelPlaceable.height, trailingPlaceable.height, constraints.minHeight)
                layout(width, height) {
                    labelPlaceable.place(0, (height - labelPlaceable.height) / 2)
                    trailingPlaceable.place(width - trailingWidth, (height - trailingPlaceable.height) / 2)
                }
            } else {
                val labelPlaceable = measurables[0].measure(Constraints(maxWidth = width))
                val height = maxOf(labelPlaceable.height + trailingPlaceable.height, constraints.minHeight)
                layout(width, height) {
                    labelPlaceable.place(0, 0)
                    trailingPlaceable.place(0, labelPlaceable.height)
                }
            }
        }
    }
}

@Composable
internal fun QuickSliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    fraction: Float,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onFractionChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    QuickRowFrame(isFocused = isFocused, onClick = onFocus, enabled = enabled, twoLine = true) {
        QuickLabelLine(
            label = label,
            leading = { QuickRowIcon(icon, isFocused) }
        ) {
            QuickValueText(valueText, isFocused)
        }
        SecondLine {
            ArgosyTrackSlider(
                value = fraction,
                onValueChange = { value ->
                    onFocus()
                    onFractionChange(value)
                },
                focused = isFocused
            )
        }
    }
}

@Composable
internal fun QuickSwitchedSliderRow(
    icon: ImageVector,
    label: String,
    on: Boolean,
    fraction: Float,
    valueText: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onFractionChange: (Float) -> Unit,
    offText: String? = null
) {
    val phase = rememberSwitchedPhase(on)
    val offLabel = offText ?: stringResource(R.string.ui_quick_settings_off)
    QuickRowFrame(isFocused = isFocused, onClick = onFocus, twoLine = true) {
        QuickLabelLine(
            label = label,
            leading = {
                SwitchChip(
                    icon = icon,
                    on = on,
                    onToggle = {
                        onFocus()
                        onToggle(!on)
                    },
                    contentDescription = label
                )
            }
        ) {
            Crossfade(
                targetState = on,
                animationSpec = tween(Motion.durationMicro, easing = Motion.argosyEase),
                label = "quick-switched-value"
            ) { isOn ->
                QuickValueText(if (isOn) valueText else offLabel, isFocused)
            }
        }
        SecondLine {
            SwitchedTrackSlider(
                value = fraction,
                phase = phase,
                onValueChange = { value ->
                    onFocus()
                    onFractionChange(value)
                },
                focused = isFocused
            )
        }
    }
}

@Composable
internal fun QuickSegmentedRow(
    icon: ImageVector,
    label: String,
    options: List<String>,
    selectedIndex: Int,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onSelect: (Int) -> Unit,
    inline: Boolean,
    enabled: Boolean = true
) {
    val select: (Int) -> Unit = { index ->
        onFocus()
        onSelect(index)
    }
    QuickRowFrame(isFocused = isFocused, onClick = onFocus, enabled = enabled, twoLine = !inline) {
        QuickLabelLine(
            label = label,
            leading = { QuickRowIcon(icon, isFocused) }
        ) {
            if (inline) {
                SegmentedControl(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = select,
                    focused = isFocused,
                    enabled = enabled,
                    modifier = Modifier.width(segmentedInlineWidth(options.size))
                )
            }
        }
        if (!inline) {
            SecondLine {
                SegmentedControl(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = select,
                    focused = isFocused,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
internal fun QuickNoticeRow(
    message: String,
    actionLabel: String,
    isFocused: Boolean,
    onAction: () -> Unit,
    enabled: Boolean = true
) {
    QuickRowFrame(isFocused = isFocused, onClick = onAction, enabled = enabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textPrimary,
                modifier = Modifier.weight(1f)
            )
            ActionButton(label = actionLabel, onClick = onAction, focused = isFocused && enabled, enabled = enabled)
        }
    }
}
