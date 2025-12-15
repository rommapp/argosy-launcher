package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.AnimationSpeed
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UiDensity
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.colorIntToHue
import com.nendo.argosy.ui.components.hueToColorInt
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun DisplaySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val currentHue = uiState.display.primaryColor?.let { colorIntToHue(it) }
    val maxIndex = if (uiState.display.useGameBackground) 7 else 8

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            DisplaySectionHeader("Appearance")
        }
        item {
            CyclePreference(
                title = "Theme",
                value = uiState.display.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 0,
                onClick = {
                    val next = when (uiState.display.themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    viewModel.setThemeMode(next)
                }
            )
        }
        item {
            HueSliderPreference(
                title = "Accent Color",
                currentHue = currentHue,
                isFocused = uiState.focusedIndex == 1,
                onHueChange = { hue ->
                    if (hue != null) {
                        viewModel.setPrimaryColor(hueToColorInt(hue))
                    } else {
                        viewModel.resetToDefaultColor()
                    }
                }
            )
        }
        item {
            CyclePreference(
                title = "Animation Speed",
                value = uiState.display.animationSpeed.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 2,
                onClick = {
                    val next = when (uiState.display.animationSpeed) {
                        AnimationSpeed.SLOW -> AnimationSpeed.NORMAL
                        AnimationSpeed.NORMAL -> AnimationSpeed.FAST
                        AnimationSpeed.FAST -> AnimationSpeed.OFF
                        AnimationSpeed.OFF -> AnimationSpeed.SLOW
                    }
                    viewModel.setAnimationSpeed(next)
                }
            )
        }
        item {
            CyclePreference(
                title = "UI Density",
                value = uiState.display.uiDensity.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 3,
                onClick = {
                    val next = when (uiState.display.uiDensity) {
                        UiDensity.COMPACT -> UiDensity.NORMAL
                        UiDensity.NORMAL -> UiDensity.SPACIOUS
                        UiDensity.SPACIOUS -> UiDensity.COMPACT
                    }
                    viewModel.setUiDensity(next)
                }
            )
        }
        item {
            DisplaySectionHeader("Background")
        }
        item {
            SwitchPreference(
                title = "Game Artwork",
                subtitle = "Use game cover as background",
                isEnabled = uiState.display.useGameBackground,
                isFocused = uiState.focusedIndex == 4,
                onToggle = { viewModel.setUseGameBackground(it) }
            )
        }
        if (!uiState.display.useGameBackground) {
            item {
                val subtitle = if (uiState.display.customBackgroundPath != null) {
                    "Custom image selected"
                } else {
                    "No image selected"
                }
                ActionPreference(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Custom Image",
                    subtitle = subtitle,
                    isFocused = uiState.focusedIndex == 5,
                    onClick = { viewModel.openBackgroundPicker() }
                )
            }
        }
        val sliderOffset = if (uiState.display.useGameBackground) 0 else 1
        item {
            SliderPreference(
                title = "Blur",
                value = uiState.display.backgroundBlur / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 5 + sliderOffset
            )
        }
        item {
            SliderPreference(
                title = "Saturation",
                value = uiState.display.backgroundSaturation / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 6 + sliderOffset
            )
        }
        item {
            SliderPreference(
                title = "Opacity",
                value = uiState.display.backgroundOpacity / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 7 + sliderOffset
            )
        }
    }
}

@Composable
private fun DisplaySectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
