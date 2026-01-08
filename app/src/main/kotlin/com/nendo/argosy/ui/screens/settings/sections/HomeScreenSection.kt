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
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun HomeScreenSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val display = uiState.display
    val hasCustomImage = !display.useGameBackground

    val sections = if (hasCustomImage) {
        listOf(
            ListSection(listStartIndex = 0, listEndIndex = 5, focusStartIndex = 0, focusEndIndex = 4),
            ListSection(listStartIndex = 6, listEndIndex = 7, focusStartIndex = 5, focusEndIndex = 5)
        )
    } else {
        listOf(
            ListSection(listStartIndex = 0, listEndIndex = 4, focusStartIndex = 0, focusEndIndex = 3),
            ListSection(listStartIndex = 5, listEndIndex = 6, focusStartIndex = 4, focusEndIndex = 4)
        )
    }

    val focusToListIndex: (Int) -> Int = { focus ->
        if (hasCustomImage) focus + 1 else focus + 1
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = focusToListIndex,
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            HomeScreenSectionHeader("Background")
        }
        item {
            SwitchPreference(
                title = "Game Artwork",
                subtitle = "Use game cover as background",
                isEnabled = display.useGameBackground,
                isFocused = uiState.focusedIndex == 0,
                onToggle = { viewModel.setUseGameBackground(it) }
            )
        }
        if (!display.useGameBackground) {
            item {
                val subtitle = if (display.customBackgroundPath != null) {
                    "Custom image selected"
                } else {
                    "No image selected"
                }
                ActionPreference(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Custom Image",
                    subtitle = subtitle,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.openBackgroundPicker() }
                )
            }
        }
        val sliderOffset = if (display.useGameBackground) 0 else 1
        item {
            SliderPreference(
                title = "Blur",
                value = display.backgroundBlur / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 1 + sliderOffset,
                onClick = { viewModel.cycleBackgroundBlur() }
            )
        }
        item {
            SliderPreference(
                title = "Saturation",
                value = display.backgroundSaturation / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 2 + sliderOffset,
                onClick = { viewModel.cycleBackgroundSaturation() }
            )
        }
        item {
            SliderPreference(
                title = "Opacity",
                value = display.backgroundOpacity / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 3 + sliderOffset,
                onClick = { viewModel.cycleBackgroundOpacity() }
            )
        }
        item {
            HomeScreenSectionHeader("Footer")
        }
        item {
            SwitchPreference(
                title = "Accent Color Footer",
                subtitle = "Use accent color for footer background",
                isEnabled = display.useAccentColorFooter,
                isFocused = uiState.focusedIndex == 4 + sliderOffset,
                onToggle = { viewModel.setUseAccentColorFooter(it) }
            )
        }
    }
}

@Composable
private fun HomeScreenSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
