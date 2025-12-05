package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.ui.theme.Dimens

private val preferenceShape = RoundedCornerShape(Dimens.radiusLg)

@Composable
private fun preferenceModifier(
    isFocused: Boolean,
    isDangerous: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier {
    val backgroundColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    return Modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.settingsItemMinHeight)
        .clip(preferenceShape)
        .background(backgroundColor, preferenceShape)
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier
        )
        .padding(Dimens.spacingMd)
}

@Composable
private fun preferenceContentColor(isFocused: Boolean, isDangerous: Boolean = false): Color {
    return when {
        isDangerous -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun preferenceSecondaryColor(isFocused: Boolean): Color {
    return if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }
}

@Composable
fun NavigationPreference(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = preferenceModifier(isFocused, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = preferenceSecondaryColor(isFocused)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CyclePreference(
    title: String,
    value: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = preferenceModifier(isFocused, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        Text(
            text = "< $value >",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    isEnabled: Boolean,
    isFocused: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    Row(
        modifier = preferenceModifier(isFocused, onClick = { onToggle(!isEnabled) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = preferenceSecondaryColor(isFocused)
                )
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun InfoPreference(
    title: String,
    value: String,
    isFocused: Boolean,
    icon: ImageVector? = null
) {
    Row(
        modifier = preferenceModifier(isFocused),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = preferenceSecondaryColor(isFocused)
        )
    }
}

@Composable
fun ActionPreference(
    title: String,
    subtitle: String,
    isFocused: Boolean,
    icon: ImageVector? = null,
    isDangerous: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = preferenceModifier(isFocused, isDangerous) { if (isEnabled) onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) preferenceContentColor(isFocused, isDangerous)
                        else preferenceContentColor(isFocused, isDangerous).copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) preferenceSecondaryColor(isFocused)
                        else preferenceSecondaryColor(isFocused).copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PlatformPreference(
    platformName: String,
    emulatorCount: Int,
    selectedEmulator: String?,
    isFocused: Boolean,
    isEnabled: Boolean = true,
    onCycle: () -> Unit
) {
    val disabledAlpha = 0.45f
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        else -> preferenceContentColor(isFocused)
    }
    val secondaryColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        else -> preferenceSecondaryColor(isFocused)
    }

    Row(
        modifier = preferenceModifier(isFocused, onClick = onCycle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = platformName,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = if (isEnabled) "$emulatorCount emulators available" else "No emulators installed",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
        Text(
            text = if (isEnabled) "< ${selectedEmulator ?: "Auto"} >" else "Download",
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                !isEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
fun PlatformStatsPreference(
    platformName: String,
    gamesCount: String,
    downloadedText: String,
    isFocused: Boolean
) {
    Row(
        modifier = preferenceModifier(isFocused),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = platformName,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = gamesCount,
                style = MaterialTheme.typography.bodyMedium,
                color = preferenceContentColor(isFocused)
            )
            Text(
                text = downloadedText,
                style = MaterialTheme.typography.bodySmall,
                color = preferenceSecondaryColor(isFocused)
            )
        }
    }
}

@Composable
fun ColorPickerPreference(
    title: String,
    presetColors: List<Pair<Int?, String>>,
    currentColor: Int?,
    isFocused: Boolean,
    focusedColorIndex: Int,
    onColorSelect: (Int?) -> Unit,
    colorCircleContent: @Composable (color: Int?, isSelected: Boolean, isColorFocused: Boolean) -> Unit
) {
    Column(
        modifier = preferenceModifier(isFocused)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        Spacer(modifier = Modifier.padding(top = Dimens.spacingSm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier.fillMaxWidth()
        ) {
            presetColors.forEachIndexed { index, (color, _) ->
                Box(
                    modifier = Modifier.clickable { onColorSelect(color) },
                    contentAlignment = Alignment.Center
                ) {
                    colorCircleContent(
                        color,
                        currentColor == color,
                        isFocused && index == focusedColorIndex
                    )
                }
            }
        }
    }
}
