package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
private fun focusedLabelColor(focused: Boolean): Color {
    val theme = LocalArgosyTheme.current
    return if (focused) lerp(theme.focusAccent, Color.White, 0.45f) else theme.textPrimary
}

/**
 * A single-select row: selected draws the selected fill and a trailing check, focus draws the
 * focus fill. A row with [enabled] false is dimmed and takes no tap.
 */
@Composable
fun SetupChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.menuRowHeightLg)
                .clip(shape)
                .argosyFocusIndicators(
                    focused = focused,
                    indicators = FocusIndicators.ListRow,
                    selected = selected,
                    shape = shape,
                )
                .clickableNoFocus(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else ComponentDefaults.QuickPanel.disabledContentAlpha)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) theme.focusAccent else theme.textDim,
                modifier = Modifier.size(Dimens.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = focusedLabelColor(focused),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.firstrun_save_sync_option_selected),
                    tint = theme.focusAccent,
                    modifier = Modifier.size(Dimens.iconMd),
                )
            }
        }
        if (error != null) SetupErrorLine(error, Modifier.padding(horizontal = Dimens.spacingMd))
    }
}

/**
 * A text field drawn as a row. The value is typed on the controller keyboard; a tap reaches
 * [onOpen], the same path A takes.
 */
@Composable
fun SetupFieldRow(
    label: String,
    value: String,
    placeholder: String?,
    focused: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    ControllerKeyboardTapTarget(onTap = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.menuRowHeightLg)
                .clip(shape)
                .background(theme.surfaceRaised)
                .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, shape = shape)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = theme.textDim)
            Text(
                text = value.ifEmpty { placeholder.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) theme.textDim else focusedLabelColor(focused),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A numbered step with a live state chip. [number] null draws an unnumbered step.
 */
@Composable
fun SetupStepCard(
    number: Int?,
    title: String,
    subtitle: String,
    chip: String,
    chipPositive: Boolean,
    focused: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .clip(shape)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        when {
            leadingIcon != null -> Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (chipPositive) LocalLauncherTheme.current.semanticColors.success else theme.textDim,
                modifier = Modifier.size(Dimens.iconMd),
            )
            number != null -> Box(
                modifier = Modifier
                    .size(Dimens.iconLg)
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(theme.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = number.toString(), style = MaterialTheme.typography.labelLarge, color = theme.textPrimary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = focusedLabelColor(focused))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
        }
        SetupStateChip(label = chip, positive = chipPositive)
    }
}

@Composable
fun SetupStateChip(label: String, positive: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val success = LocalLauncherTheme.current.semanticColors.success
    val s = LocalUiScale.current.scale
    Row(
        modifier = modifier
            .height((ComponentDefaults.Setup.chipHeightDp * s).dp)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (positive) success.copy(alpha = ComponentDefaults.Setup.chipGrantedFillAlpha) else theme.surfaceElevated)
            .padding(horizontal = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        if (positive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = success,
                modifier = Modifier.size(Dimens.iconXs),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (positive) success else theme.textDim,
            maxLines = 1,
        )
    }
}

@Composable
fun SetupErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.destructive,
        modifier = modifier.padding(top = Dimens.spacingXs),
    )
}

@Composable
fun SetupSummaryRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = theme.focusAccent, modifier = Modifier.size(Dimens.iconSm))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = theme.textDim)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
