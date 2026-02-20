package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.theme.Dimens

enum class FanMode(val value: Int, val label: String) {
    QUIET(1, "Quiet"),
    SMART(4, "Smart"),
    SPORT(5, "Sport"),
    CUSTOM(6, "Turbo+");

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SMART
    }
}

enum class PerformanceMode(val value: Int, val label: String) {
    STANDARD(0, "Standard"),
    HIGH(1, "High Performance"),
    MAX(2, "Max Performance");

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: STANDARD
    }
}

data class QuickSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,
    val vibrationSupported: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val fanMode: FanMode = FanMode.SMART,
    val fanSpeed: Int = 25000,
    val performanceMode: PerformanceMode = PerformanceMode.STANDARD,
    val deviceSettingsSupported: Boolean = false,
    val deviceSettingsEnabled: Boolean = false,
    val systemVolume: Float = 1f,
    val secondaryVolume: Float? = null,
    val screenBrightness: Float = 0.5f,
    val secondaryBrightness: Float? = null,
    val isDualScreenActive: Boolean = false,
    val isRolesSwapped: Boolean = false
)

@Composable
fun QuickSettingsPanel(
    isVisible: Boolean,
    state: QuickSettingsState,
    focusedIndex: Int,
    onThemeCycle: () -> Unit,
    onSoundToggle: () -> Unit,
    onHapticToggle: () -> Unit,
    onVibrationStrengthChange: (Float) -> Unit,
    onAmbientToggle: () -> Unit,
    onFanModeCycle: () -> Unit,
    onFanSpeedChange: (Int) -> Unit,
    onPerformanceModeCycle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSecondaryBrightnessChange: (Float) -> Unit,
    onSwapDisplays: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVibrationSlider = state.vibrationSupported && state.hapticEnabled
    val hasSecondaryVolume = state.secondaryVolume != null
    val hasSecondaryBrightness = state.secondaryBrightness != null
    val hasSwapDisplays = state.isDualScreenActive
    val baseItemCount = 6 +
        (if (hasVibrationSlider) 1 else 0) +
        (if (hasSecondaryVolume) 1 else 0) +
        (if (hasSecondaryBrightness) 1 else 0) +
        (if (hasSwapDisplays) 1 else 0)
    val hasFanSlider = state.deviceSettingsSupported && state.fanMode == FanMode.CUSTOM && state.deviceSettingsEnabled
    val deviceItemCount = when {
        !state.deviceSettingsSupported -> 0
        hasFanSlider -> 3
        else -> 2
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickableNoFocus(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Column(
                modifier = Modifier
                    .width(Dimens.modalWidth - Dimens.footerHeight)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = Dimens.spacingLg)
            ) {
                Text(
                    text = "Quick Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg)
                )

                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val listState = rememberLazyListState()
                val sections = if (state.deviceSettingsSupported) {
                    val dividerListIndex = deviceItemCount
                    listOf(
                        ListSection(
                            listStartIndex = 0,
                            listEndIndex = deviceItemCount - 1,
                            focusStartIndex = 0,
                            focusEndIndex = deviceItemCount - 1
                        ),
                        ListSection(
                            listStartIndex = dividerListIndex + 1,
                            listEndIndex = dividerListIndex + baseItemCount,
                            focusStartIndex = deviceItemCount,
                            focusEndIndex = deviceItemCount + baseItemCount - 1
                        )
                    )
                } else {
                    listOf(
                        ListSection(
                            listStartIndex = 0,
                            listEndIndex = baseItemCount - 1,
                            focusStartIndex = 0,
                            focusEndIndex = baseItemCount - 1
                        )
                    )
                }

                val focusToListIndex: (Int) -> Int = { focus ->
                    if (state.deviceSettingsSupported && focus >= deviceItemCount) {
                        focus + 1
                    } else {
                        focus
                    }
                }

                SectionFocusedScroll(
                    listState = listState,
                    focusedIndex = focusedIndex,
                    focusToListIndex = focusToListIndex,
                    sections = sections
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.deviceSettingsSupported) {
                        val permissionMissing = !state.deviceSettingsEnabled

                        item {
                            QuickSettingItemTwoLine(
                                icon = Icons.Default.Speed,
                                label = "Performance",
                                value = state.performanceMode.label,
                                isFocused = focusedIndex == 0,
                                isDisabled = permissionMissing,
                                disabledReason = "Permission required",
                                onClick = onPerformanceModeCycle
                            )
                        }

                        item {
                            QuickSettingItem(
                                icon = Icons.Default.Toys,
                                label = "Fan",
                                value = state.fanMode.label,
                                isFocused = focusedIndex == 1,
                                isDisabled = permissionMissing,
                                disabledReason = "Permission required",
                                onClick = onFanModeCycle
                            )
                        }

                        if (hasFanSlider) {
                            item {
                                FanSpeedSlider(
                                    speed = state.fanSpeed,
                                    isFocused = focusedIndex == 2,
                                    onSpeedChange = onFanSpeedChange
                                )
                            }
                        }

                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    val secondaryVolumeOffset = if (hasSecondaryVolume) 1 else 0
                    val secondaryBrightnessOffset = if (hasSecondaryBrightness) 1 else 0
                    val avOffset = secondaryVolumeOffset + secondaryBrightnessOffset
                    val vibrationSliderOffset = if (hasVibrationSlider) 1 else 0

                    item {
                        QuickSettingItem(
                            icon = when (state.themeMode) {
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                            },
                            label = "Theme",
                            value = state.themeMode.displayName,
                            isFocused = focusedIndex == deviceItemCount,
                            onClick = onThemeCycle
                        )
                    }

                    item {
                        SystemVolumeSlider(
                            volume = state.systemVolume,
                            isFocused = focusedIndex == deviceItemCount + 1,
                            onVolumeChange = onVolumeChange,
                            label = if (hasSecondaryVolume) "Top Screen" else "Volume"
                        )
                    }

                    if (hasSecondaryVolume) {
                        item {
                            SystemVolumeSlider(
                                volume = state.secondaryVolume ?: 0.5f,
                                isFocused = focusedIndex == deviceItemCount + 2,
                                onVolumeChange = onSecondaryVolumeChange,
                                label = "Bottom Screen"
                            )
                        }
                    }

                    item {
                        ScreenBrightnessSlider(
                            brightness = state.screenBrightness,
                            isFocused = focusedIndex == deviceItemCount + 2 + secondaryVolumeOffset,
                            onBrightnessChange = onBrightnessChange,
                            label = if (hasSecondaryBrightness) "Top Screen" else "Brightness"
                        )
                    }

                    if (hasSecondaryBrightness) {
                        item {
                            ScreenBrightnessSlider(
                                brightness = state.secondaryBrightness ?: 0.5f,
                                isFocused = focusedIndex == deviceItemCount + 3 + secondaryVolumeOffset,
                                onBrightnessChange = onSecondaryBrightnessChange,
                                label = "Bottom Screen"
                            )
                        }
                    }

                    item {
                        QuickSettingToggle(
                            icon = Icons.Default.Vibration,
                            label = "Haptics",
                            isEnabled = state.hapticEnabled,
                            isFocused = focusedIndex == deviceItemCount + 3 + avOffset,
                            onClick = onHapticToggle
                        )
                    }

                    if (hasVibrationSlider) {
                        item {
                            VibrationStrengthSlider(
                                strength = state.vibrationStrength,
                                isFocused = focusedIndex == deviceItemCount + 4 + avOffset,
                                onStrengthChange = onVibrationStrengthChange
                            )
                        }
                    }

                    item {
                        QuickSettingToggle(
                            icon = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            label = "UI Sounds",
                            isEnabled = state.soundEnabled,
                            isFocused = focusedIndex == deviceItemCount + 4 + avOffset + vibrationSliderOffset,
                            onClick = onSoundToggle
                        )
                    }

                    item {
                        QuickSettingToggle(
                            icon = if (state.ambientAudioEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            label = "BGM",
                            isEnabled = state.ambientAudioEnabled,
                            isFocused = focusedIndex == deviceItemCount + 5 + avOffset + vibrationSliderOffset,
                            onClick = onAmbientToggle
                        )
                    }

                    if (hasSwapDisplays) {
                        item {
                            QuickSettingToggle(
                                icon = Icons.Default.SwapHoriz,
                                label = "Swap Displays",
                                isEnabled = state.isRolesSwapped,
                                isFocused = focusedIndex == deviceItemCount + 6 + avOffset + vibrationSliderOffset,
                                onClick = onSwapDisplays
                            )
                        }
                    }
                }

                Text(
                    text = "Press B or R3 to close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg)
                )
            }
        }
    }
}

@Composable
private fun QuickSettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    isFocused: Boolean,
    isDisabled: Boolean = false,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isDisabled -> Color.Transparent
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val contentColor = when {
        isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val valueColor = when {
        isDisabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .then(if (isDisabled) Modifier else Modifier.clickableNoFocus(onClick = onClick))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isDisabled && disabledReason != null) disabledReason else value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
private fun QuickSettingItemTwoLine(
    icon: ImageVector,
    label: String,
    value: String,
    isFocused: Boolean,
    isDisabled: Boolean = false,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isDisabled -> Color.Transparent
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val contentColor = when {
        isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val valueColor = when {
        isDisabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .then(if (isDisabled) Modifier else Modifier.clickableNoFocus(onClick = onClick))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Text(
            text = if (isDisabled && disabledReason != null) disabledReason else value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingXs),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun QuickSettingToggle(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = null,
            modifier = Modifier.focusProperties { canFocus = false },
            interactionSource = remember { MutableInteractionSource() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun FanSpeedSlider(
    speed: Int,
    isFocused: Boolean,
    onSpeedChange: (Int) -> Unit
) {
    val minSpeed = 25000f
    val maxSpeed = 35000f
    val percentage = ((speed - minSpeed) / (maxSpeed - minSpeed) * 100).toInt()

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Speed",
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = speed.toFloat(),
            onValueChange = { onSpeedChange(it.toInt()) },
            valueRange = minSpeed..maxSpeed,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(Dimens.iconMd)
        )
    }
}

@Composable
private fun VibrationStrengthSlider(
    strength: Float,
    isFocused: Boolean,
    onStrengthChange: (Float) -> Unit
) {
    val percentage = (strength * 100).toInt()

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Strength",
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = strength,
            onValueChange = onStrengthChange,
            valueRange = 0f..1f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(Dimens.iconMd)
        )
    }
}

@Composable
private fun SystemVolumeSlider(
    volume: Float,
    isFocused: Boolean,
    onVolumeChange: (Float) -> Unit,
    label: String = "Volume"
) {
    val percentage = (volume * 100).toInt()

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (volume > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(Dimens.iconMd)
        )
    }
}

@Composable
private fun ScreenBrightnessSlider(
    brightness: Float,
    isFocused: Boolean,
    onBrightnessChange: (Float) -> Unit,
    label: String = "Brightness"
) {
    val percentage = (brightness * 100).toInt()

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsBrightness,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0f..1f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(Dimens.iconMd)
        )
    }
}
