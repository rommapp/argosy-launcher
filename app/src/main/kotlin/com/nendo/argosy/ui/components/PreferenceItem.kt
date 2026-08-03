package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.focusBackground
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.ArgosyTrackSlider
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.primitives.StepperControl
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens

@Composable
private fun preferenceAccent(isDangerous: Boolean = false): Color =
    if (isDangerous) LocalArgosyTheme.current.destructive else LocalArgosyTheme.current.focusAccent

/**
 * Row chrome shared by every preference item.
 *
 * A row takes either a whole-row [onClick] or a split [onDirectionalTap], never both.
 * The split form hands the tap -1 for the left half of the row and +1 for the right,
 * so a stepper reads by touch the way it reads on the d-pad.
 */
@Composable
internal fun preferenceModifier(
    isFocused: Boolean,
    isDangerous: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDirectionalTap: ((Int) -> Unit)? = null
): Modifier {
    val preferenceShape = RoundedCornerShape(Dimens.radiusControl)
    val currentDirectionalTap by rememberUpdatedState(onDirectionalTap)
    val accent = preferenceAccent(isDangerous)
    val surface = MaterialTheme.colorScheme.surface
    val background by animateColorAsState(
        targetValue = if (isFocused) accent.copy(alpha = 0.15f).compositeOver(surface) else surface.copy(alpha = 0.10f),
        animationSpec = Motion.focusColorSpec,
        label = "pref-bg"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.8f else 0f,
        animationSpec = Motion.focusSpring,
        label = "pref-border"
    )

    return Modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.settingsItemMinHeight)
        .clip(preferenceShape)
        .background(background)
        .border(Dimens.borderThin, accent.copy(alpha = borderAlpha), preferenceShape)
        .then(
            when {
                onDirectionalTap != null -> Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentDirectionalTap?.invoke(if (offset.x < size.width / 2f) -1 else 1)
                    }
                }
                onClick != null -> Modifier.clickableNoFocus(onClick = onClick)
                else -> Modifier
            }
        )
        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
}

@Composable
internal fun preferenceContentColor(isFocused: Boolean, isDangerous: Boolean = false): Color {
    return when {
        isDangerous -> LocalArgosyTheme.current.destructive
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun preferenceSecondaryColor(isFocused: Boolean): Color {
    return if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f).copy(alpha = 0.65f)
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
    subtitle: String? = null,
    isCustom: Boolean = false,
    showResetButton: Boolean = false,
    onReset: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    valueFooter: (@Composable () -> Unit)? = null,
    options: List<String>? = null,
    onSelect: ((Int) -> Unit)? = null,
    pickerRequestToken: Int = 0
) {
    val valueColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isCustom -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    val hasPicker = options != null && onSelect != null
    var pickerVisible by remember { mutableStateOf(false) }
    var consumedPickerToken by remember { mutableIntStateOf(pickerRequestToken) }
    LaunchedEffect(pickerRequestToken) {
        if (hasPicker && pickerRequestToken > consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
            pickerVisible = true
        } else if (pickerRequestToken < consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
        }
    }

    val modifier = if (showResetButton && onReset != null) {
        preferenceModifier(isFocused).then(
            Modifier.clickableNoFocus(onClick = onClick, onLongClick = onReset)
        )
    } else {
        preferenceModifier(isFocused, onClick = onClick)
    }

    Row(
        modifier = modifier,
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
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (showResetButton && onReset != null) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.iconMd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            .clickableNoFocus(onClick = onReset),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Reset to global",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(Dimens.iconSm)
                        )
                    }
                }
                EnumValueControl(
                    value = value,
                    focused = isFocused,
                    onPrev = onPrev ?: onClick,
                    onNext = onClick,
                    selectedIndex = options?.indexOf(value)?.takeIf { it >= 0 },
                    optionCount = options?.size,
                    onOpen = if (hasPicker) ({ pickerVisible = true }) else onClick,
                    valueColor = if (isFocused) null else valueColor
                )
            }
            if (valueFooter != null) {
                valueFooter()
            }
        }
    }
    if (options != null && onSelect != null) {
        EnumPickerModal(
            title = title,
            options = options,
            selectedIndex = options.indexOf(value),
            onSelect = { index ->
                pickerVisible = false
                onSelect(index)
            },
            onDismiss = { pickerVisible = false },
            visible = pickerVisible
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
    onClick: (() -> Unit)? = null,
    onAdjust: ((Int) -> Unit)? = null
) {
    Row(
        modifier = preferenceModifier(
            isFocused,
            onClick = if (onAdjust == null) onClick else null,
            onDirectionalTap = onAdjust?.let { adjust -> { direction: Int -> adjust(direction * step) } }
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        StepperControl(
            display = "$value${suffix.orEmpty()}",
            focused = isFocused,
            numericValue = value,
            onDecrement = { onAdjust?.invoke(-step) },
            onIncrement = { onAdjust?.invoke(step) ?: onClick?.invoke() }
        )
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
                ArgosyTrackSlider(
                    value = value,
                    onValueChange = onValueChange,
                    minValue = minValue,
                    maxValue = maxValue,
                    focused = isFocused,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$displayValue$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = preferenceContentColor(isFocused)
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
                    color = preferenceContentColor(isFocused)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            ArgosyTrackSlider(
                value = value,
                onValueChange = onValueChange,
                minValue = minValue,
                maxValue = maxValue,
                focused = isFocused
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
    onLabelClick: (() -> Unit)? = null,
    isCustom: Boolean = false,
    showResetButton: Boolean = false,
    onReset: (() -> Unit)? = null
) {
    Row(
        modifier = preferenceModifier(isFocused),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = preferenceContentColor(isFocused)
                    )
                    if (isCustom && !isFocused) {
                        Text(
                            text = "(Custom)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = preferenceSecondaryColor(isFocused)
                    )
                }
            }
        }
        if (showResetButton && onReset != null) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconMd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    .clickableNoFocus(onClick = onReset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reset to global",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        ArgosyToggle(
            checked = isEnabled,
            onToggle = onToggle,
            focused = isFocused
        )
    }
}

@Composable
fun InfoPreference(
    title: String,
    value: String,
    isFocused: Boolean,
    icon: ImageVector? = null,
    subtitle: String? = null
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
    trailingButtonLabel: String? = null,
    badge: String? = null,
    spinIcon: Boolean = false,
    showResetButton: Boolean = false,
    onReset: (() -> Unit)? = null,
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
            val iconModifier = if (spinIcon) {
                val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = 1200,
                            easing = androidx.compose.animation.core.LinearEasing
                        )
                    ),
                    label = "spin-rotation"
                )
                Modifier.size(Dimens.iconMd).rotate(rotation)
            } else {
                Modifier.size(Dimens.iconMd)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: defaultTint,
                modifier = iconModifier
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
        if (trailingButtonLabel != null) {
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            val btnContainer = if (isFocused) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            }
            val btnContent = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            Box(
                modifier = Modifier
                    .heightIn(min = Dimens.iconMd)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(btnContainer)
                    .clickableNoFocus(onClick = { if (isEnabled) onClick() })
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trailingButtonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = btnContent
                )
            }
        }
        if (showResetButton && onReset != null) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Box(
                modifier = Modifier
                    .size(Dimens.iconMd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    .clickableNoFocus(onClick = onReset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reset to default",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
        if (trailingText != null || badge != null) {
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(horizontalAlignment = Alignment.End) {
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) preferenceSecondaryColor(isFocused)
                                else preferenceSecondaryColor(isFocused).copy(alpha = 0.5f)
                    )
                }
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(Dimens.radiusSm)
                            )
                            .padding(horizontal = Dimens.spacingXs, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
        }
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
    lightness: Float = 0.5f,
    defaultLabel: String = "Default"
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
                    text = defaultLabel,
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
    val contentColor = preferenceContentColor(isFocused)
    val secondaryColor = preferenceSecondaryColor(isFocused)
    val disabledAlpha = 0.5f

    Row(
        modifier = preferenceModifier(isFocused),
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
