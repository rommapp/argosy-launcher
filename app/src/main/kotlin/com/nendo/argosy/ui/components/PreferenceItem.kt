package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale

@Composable
private fun preferenceModifier(
    isFocused: Boolean,
    isDangerous: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier {
    val preferenceShape = RoundedCornerShape(Dimens.radiusLg)
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
        .then(if (onClick != null) Modifier.clickableNoFocus(onClick = onClick) else Modifier)
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
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = preferenceModifier(isFocused, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    color = preferenceContentColor(isFocused).copy(alpha = 0.6f)
                )
            }
        }
        Text(
            text = "< $value >",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SliderPreference(
    title: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    isFocused: Boolean,
    step: Int = 1,
    suffix: String? = null,
    onClick: (() -> Unit)? = null
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (suffix != null) {
                Text(
                    text = "$value$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
            for (i in minValue..maxValue step step) {
                val isSelected = i <= value
                val dotColor = when {
                    isFocused && isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                }
                Box(
                    modifier = Modifier
                        .size(if (i == value) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
fun TrackSliderPreference(
    title: String,
    value: Float,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    steps: Int = 0,
    isFocused: Boolean,
    suffix: String = "%",
    onValueChange: (Float) -> Unit
) {
    val displayValue = (value * 100).toInt()
    val aspectRatioClass = LocalUiScale.current.aspectRatioClass
    val isWideDisplay = aspectRatioClass == AspectRatioClass.ULTRA_WIDE ||
                        aspectRatioClass == AspectRatioClass.WIDE

    val sliderColors = SliderDefaults.colors(
        thumbColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.primary,
        activeTrackColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.primary,
        inactiveTrackColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                             else MaterialTheme.colorScheme.surfaceVariant
    )

    if (isWideDisplay) {
        Row(
            modifier = preferenceModifier(isFocused),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused),
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = minValue..maxValue,
                    steps = steps,
                    colors = sliderColors,
                    modifier = Modifier.weight(1f).height(Dimens.iconMd)
                )
                Text(
                    text = "$displayValue$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        Column(
            modifier = preferenceModifier(isFocused)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = preferenceContentColor(isFocused)
                )
                Text(
                    text = "$displayValue$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = minValue..maxValue,
                steps = steps,
                colors = sliderColors,
                modifier = Modifier.height(Dimens.iconMd)
            )
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    isEnabled: Boolean,
    isFocused: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onLabelClick: (() -> Unit)? = null
) {
    val preferenceShape = RoundedCornerShape(Dimens.radiusLg)
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(preferenceShape)
            .background(backgroundColor, preferenceShape)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onLabelClick != null) Modifier.clickableNoFocus(onClick = onLabelClick)
                    else Modifier.clickableNoFocus { onToggle(!isEnabled) }
                ),
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
            Column {
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
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.focusProperties { canFocus = false },
            interactionSource = remember { MutableInteractionSource() }
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
    iconTint: Color? = null,
    isDangerous: Boolean = false,
    isEnabled: Boolean = true,
    trailingText: String? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = preferenceModifier(isFocused, isDangerous) { if (isEnabled) onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            val defaultTint = when {
                !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: defaultTint,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isEnabled) preferenceContentColor(isFocused, isDangerous)
                            else preferenceContentColor(isFocused, isDangerous).copy(alpha = 0.5f)
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = RoundedCornerShape(Dimens.radiusSm)
                            )
                            .padding(horizontal = Dimens.spacingXs, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) preferenceSecondaryColor(isFocused)
                        else preferenceSecondaryColor(isFocused).copy(alpha = 0.5f)
            )
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.clickableNoFocus { onColorSelect(color) },
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

@Composable
fun HueSliderPreference(
    title: String,
    currentHue: Float?,
    isFocused: Boolean,
    onHueChange: (Float?) -> Unit,
    saturation: Float = 0.7f,
    lightness: Float = 0.5f
) {
    val hueSteps = 36
    val hueColors = (0..hueSteps).map { step ->
        val hue = (step * 360f / hueSteps)
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
    }

    val currentColor = currentHue?.let {
        Color(ColorUtils.HSLToColor(floatArrayOf(it, saturation, lightness)))
    }

    Column(
        modifier = preferenceModifier(isFocused)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused)
            )
            if (currentColor != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            } else {
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.bodyMedium,
                    color = preferenceSecondaryColor(isFocused)
                )
            }
        }
        Spacer(modifier = Modifier.padding(top = Dimens.spacingSm))
        var sliderSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(
                    Brush.horizontalGradient(hueColors)
                )
                .onSizeChanged { sliderSize = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                val x = event.changes.first().position.x
                                val width = sliderSize.width.toFloat()
                                if (width > 0) {
                                    val hue = (x / width * 360f).coerceIn(0f, 360f)
                                    onHueChange(hue)
                                }
                            }
                        }
                    }
                }
        ) {
            if (currentHue != null) {
                val position = currentHue / 360f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(position)
                        .align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(16.dp, 32.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .border(2.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

fun hueToColorInt(hue: Float, saturation: Float = 0.7f, lightness: Float = 0.5f): Int {
    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
}

fun colorIntToHue(colorInt: Int): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colorInt, hsl)
    return hsl[0]
}

@Composable
fun ImageCachePreference(
    title: String,
    displayPath: String,
    hasCustomPath: Boolean,
    isFocused: Boolean,
    actionIndex: Int,
    isMigrating: Boolean = false,
    onChange: () -> Unit,
    onReset: () -> Unit
) {
    val preferenceShape = RoundedCornerShape(Dimens.radiusLg)
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }
    val disabledAlpha = 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(preferenceShape)
            .background(backgroundColor, preferenceShape)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = if (isMigrating) "Moving images..." else displayPath,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val changeSelected = isFocused && actionIndex == 0 && !isMigrating
            val changeBgColor = when {
                isMigrating -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = disabledAlpha)
                changeSelected -> MaterialTheme.colorScheme.primary
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val changeTextColor = when {
                isMigrating -> contentColor.copy(alpha = disabledAlpha)
                changeSelected -> MaterialTheme.colorScheme.onPrimary
                else -> contentColor
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(changeBgColor)
                    .clickableNoFocus(enabled = !isMigrating) { onChange() }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = changeTextColor
                )
            }

            if (hasCustomPath) {
                val resetSelected = isFocused && actionIndex == 1 && !isMigrating
                val resetBgColor = when {
                    isMigrating -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = disabledAlpha)
                    resetSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val resetTextColor = when {
                    isMigrating -> contentColor.copy(alpha = disabledAlpha)
                    resetSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> contentColor
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(resetBgColor)
                        .clickableNoFocus(enabled = !isMigrating) { onReset() }
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelMedium,
                        color = resetTextColor
                    )
                }
            }
        }
    }
}
