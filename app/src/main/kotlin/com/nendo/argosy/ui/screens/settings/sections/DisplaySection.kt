package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.NavigationPreference
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
    val storage = uiState.storage
    val maxIndex = 8

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
                title = "Grid Density",
                value = uiState.display.gridDensity.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 2,
                onClick = {
                    val next = when (uiState.display.gridDensity) {
                        GridDensity.COMPACT -> GridDensity.NORMAL
                        GridDensity.NORMAL -> GridDensity.SPACIOUS
                        GridDensity.SPACIOUS -> GridDensity.COMPACT
                    }
                    viewModel.setGridDensity(next)
                }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Outlined.Image,
                title = "Box Art",
                subtitle = "Customize card appearance",
                isFocused = uiState.focusedIndex == 3,
                onClick = { viewModel.navigateToBoxArt() }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Outlined.Home,
                title = "Home Screen",
                subtitle = "Background and footer settings",
                isFocused = uiState.focusedIndex == 4,
                onClick = { viewModel.navigateToHomeScreen() }
            )
        }
        item {
            DisplaySectionHeader("Default")
        }
        item {
            CyclePreference(
                title = "Default View",
                value = when (uiState.display.defaultView) {
                    DefaultView.SHOWCASE -> "Showcase"
                    DefaultView.LIBRARY -> "Library"
                },
                isFocused = uiState.focusedIndex == 5,
                onClick = { viewModel.cycleDefaultView() }
            )
        }
        item {
            DisplaySectionHeader("Screen Safety")
        }
        item {
            SwitchPreference(
                title = "Screen Dimmer",
                subtitle = "Dims screen after inactivity to prevent burn-in",
                isEnabled = storage.screenDimmerEnabled,
                isFocused = uiState.focusedIndex == 6,
                onToggle = { viewModel.toggleScreenDimmer() }
            )
        }
        item {
            CyclePreference(
                title = "Dim After",
                value = "${storage.screenDimmerTimeoutMinutes} min",
                isFocused = uiState.focusedIndex == 7,
                onClick = { viewModel.cycleScreenDimmerTimeout() }
            )
        }
        item {
            SliderPreference(
                title = "Dim Level",
                value = storage.screenDimmerLevel,
                minValue = 40,
                maxValue = 70,
                isFocused = uiState.focusedIndex == 8,
                step = 10,
                onClick = { viewModel.cycleScreenDimmerLevel() }
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
