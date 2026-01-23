package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.colorIntToHue
import com.nendo.argosy.ui.components.hueToColorInt
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class DisplayItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (DisplayState) -> Boolean = { true }
    ) : DisplayItem(key, section, visibleWhen)

    data object Theme : DisplayItem("theme", "appearance")
    data object AccentColor : DisplayItem("accentColor", "appearance")
    data object SecondaryColor : DisplayItem("secondaryColor", "appearance")
    data object GridDensity : DisplayItem("gridDensity", "appearance")
    data object UiScale : DisplayItem("uiScale", "appearance")
    data object BoxArt : DisplayItem("boxArt", "appearance")
    data object HomeScreen : DisplayItem("homeScreen", "appearance")

    data object DefaultView : DisplayItem("defaultView", "default")

    data object ScreenDimmer : DisplayItem("screenDimmer", "screenSafety")
    data object DimAfter : DisplayItem("dimAfter", "screenSafety")
    data object DimLevel : DisplayItem("dimLevel", "screenSafety")

    data object AmbientLed : DisplayItem(
        key = "ambientLed",
        section = "ambientLed",
        visibleWhen = { it.ambientLedAvailable }
    )
    data object AmbientLedAudioBrightness : DisplayItem(
        key = "ambientLedAudioBrightness",
        section = "ambientLed",
        visibleWhen = { it.ambientLedAvailable && it.ambientLedEnabled }
    )
    data object AmbientLedAudioColors : DisplayItem(
        key = "ambientLedAudioColors",
        section = "ambientLed",
        visibleWhen = { it.ambientLedAvailable && it.ambientLedEnabled }
    )
    data object AmbientLedColorMode : DisplayItem(
        key = "ambientLedColorMode",
        section = "ambientLed",
        visibleWhen = { it.ambientLedAvailable && it.ambientLedEnabled }
    )

    companion object {
        private val AppearanceHeader = Header("appearanceHeader", "appearance", "Appearance")
        private val DefaultHeader = Header("defaultHeader", "default", "Default")
        private val ScreenSafetyHeader = Header("screenSafetyHeader", "screenSafety", "Screen Safety")
        private val AmbientLedHeader = Header(
            key = "ambientLedHeader",
            section = "ambientLed",
            title = "Ambient Lighting",
            visibleWhen = { it.ambientLedAvailable }
        )

        val ALL: List<DisplayItem> = listOf(
            AppearanceHeader,
            Theme, AccentColor, SecondaryColor, GridDensity, UiScale, BoxArt, HomeScreen,
            DefaultHeader,
            DefaultView,
            ScreenSafetyHeader,
            ScreenDimmer, DimAfter, DimLevel,
            AmbientLedHeader,
            AmbientLed, AmbientLedAudioBrightness, AmbientLedAudioColors, AmbientLedColorMode
        )
    }
}

private val displayLayout = SettingsLayout<DisplayItem, DisplayState>(
    allItems = DisplayItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun displayMaxFocusIndex(display: DisplayState): Int = displayLayout.maxFocusIndex(display)

internal fun displayItemAtFocusIndex(index: Int, display: DisplayState): DisplayItem? =
    displayLayout.itemAtFocusIndex(index, display)

internal fun displaySections(display: DisplayState) = displayLayout.buildSections(display)

@Composable
fun DisplaySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val display = uiState.display
    val storage = uiState.storage
    val currentHue = display.primaryColor?.let { colorIntToHue(it) }
    val secondaryHue = display.secondaryColor?.let { colorIntToHue(it) }

    val visibleItems = remember(display.ambientLedAvailable, display.ambientLedEnabled) {
        displayLayout.visibleItems(display)
    }
    val sections = remember(display.ambientLedAvailable, display.ambientLedEnabled) {
        displayLayout.buildSections(display)
    }

    fun isFocused(item: DisplayItem): Boolean =
        uiState.focusedIndex == displayLayout.focusIndexOf(item, display)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { displayLayout.focusToListIndex(it, display) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is DisplayItem.Header -> DisplaySectionHeader(item.title)

                DisplayItem.Theme -> CyclePreference(
                    title = "Theme",
                    value = display.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    isFocused = isFocused(item),
                    onClick = {
                        val next = when (display.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        viewModel.setThemeMode(next)
                    }
                )

                DisplayItem.AccentColor -> HueSliderPreference(
                    title = "Accent Color",
                    currentHue = currentHue,
                    isFocused = isFocused(item),
                    onHueChange = { hue ->
                        if (hue != null) {
                            viewModel.setPrimaryColor(hueToColorInt(hue))
                        } else {
                            viewModel.resetToDefaultColor()
                        }
                    }
                )

                DisplayItem.SecondaryColor -> HueSliderPreference(
                    title = "Secondary Color",
                    currentHue = secondaryHue,
                    isFocused = isFocused(item),
                    onHueChange = { hue ->
                        if (hue != null) {
                            viewModel.setSecondaryColor(hueToColorInt(hue))
                        } else {
                            viewModel.resetToDefaultSecondaryColor()
                        }
                    }
                )

                DisplayItem.GridDensity -> CyclePreference(
                    title = "Grid Density",
                    value = display.gridDensity.name.lowercase().replaceFirstChar { it.uppercase() },
                    isFocused = isFocused(item),
                    onClick = {
                        val next = when (display.gridDensity) {
                            GridDensity.COMPACT -> GridDensity.NORMAL
                            GridDensity.NORMAL -> GridDensity.SPACIOUS
                            GridDensity.SPACIOUS -> GridDensity.COMPACT
                        }
                        viewModel.setGridDensity(next)
                    }
                )

                DisplayItem.UiScale -> SliderPreference(
                    title = "UI Scale",
                    value = display.uiScale,
                    minValue = 75,
                    maxValue = 150,
                    isFocused = isFocused(item),
                    step = 5,
                    suffix = "%",
                    onClick = { viewModel.adjustUiScale(5) }
                )

                DisplayItem.BoxArt -> NavigationPreference(
                    icon = Icons.Outlined.Image,
                    title = "Box Art",
                    subtitle = "Customize card appearance",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToBoxArt() }
                )

                DisplayItem.HomeScreen -> NavigationPreference(
                    icon = Icons.Outlined.Home,
                    title = "Home Screen",
                    subtitle = "Background and footer settings",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToHomeScreen() }
                )

                DisplayItem.DefaultView -> CyclePreference(
                    title = "Default View",
                    value = when (display.defaultView) {
                        DefaultView.HOME -> "Home"
                        DefaultView.LIBRARY -> "Library"
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleDefaultView() }
                )

                DisplayItem.ScreenDimmer -> SwitchPreference(
                    title = "Screen Dimmer",
                    subtitle = "Dims screen after inactivity to prevent burn-in",
                    isEnabled = storage.screenDimmerEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleScreenDimmer() }
                )

                DisplayItem.DimAfter -> CyclePreference(
                    title = "Dim After",
                    value = "${storage.screenDimmerTimeoutMinutes} min",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleScreenDimmerTimeout() }
                )

                DisplayItem.DimLevel -> SliderPreference(
                    title = "Dim Level",
                    value = storage.screenDimmerLevel,
                    minValue = 40,
                    maxValue = 70,
                    isFocused = isFocused(item),
                    step = 10,
                    onClick = { viewModel.cycleScreenDimmerLevel() }
                )

                DisplayItem.AmbientLed -> {
                    val subtitle = if (!display.hasScreenCapturePermission && !display.ambientLedEnabled) {
                        "Requires screen capture permission for in-game colors"
                    } else {
                        "Context-aware thumbstick LED colors"
                    }
                    SwitchPreference(
                        title = "Ambient Lighting",
                        subtitle = subtitle,
                        isEnabled = display.ambientLedEnabled,
                        isFocused = isFocused(item),
                        onToggle = {
                            if (!display.ambientLedEnabled && !display.hasScreenCapturePermission) {
                                viewModel.requestScreenCapturePermission()
                            }
                            viewModel.setAmbientLedEnabled(!display.ambientLedEnabled)
                        }
                    )
                }

                DisplayItem.AmbientLedAudioBrightness -> SwitchPreference(
                    title = "Audio Brightness",
                    subtitle = "LEDs pulse with music",
                    isEnabled = display.ambientLedAudioBrightness,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioBrightness(!display.ambientLedAudioBrightness) }
                )

                DisplayItem.AmbientLedAudioColors -> SwitchPreference(
                    title = "Audio Colors",
                    subtitle = "Intensity shifts between color bands",
                    isEnabled = display.ambientLedAudioColors,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioColors(!display.ambientLedAudioColors) }
                )

                DisplayItem.AmbientLedColorMode -> CyclePreference(
                    title = "Color Selection",
                    value = display.ambientLedColorMode.displayName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleAmbientLedColorMode() }
                )
            }
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
