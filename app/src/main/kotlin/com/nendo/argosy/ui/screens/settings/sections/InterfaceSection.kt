package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.TrackSliderPreference
import com.nendo.argosy.ui.components.colorIntToHue
import com.nendo.argosy.ui.components.hueToColorInt
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class InterfaceLayoutState(
    val display: DisplayState,
    val bgmEnabled: Boolean,
    val uiSoundsEnabled: Boolean
)

internal sealed class InterfaceItem(
    val key: String,
    val section: String,
    val visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (InterfaceLayoutState) -> Boolean = { true }
    ) : InterfaceItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (InterfaceLayoutState) -> Boolean = { true })
        : InterfaceItem(key, section, visibleWhen)

    // Appearance
    data object Theme : InterfaceItem("theme", "appearance")
    data object AccentColor : InterfaceItem("accentColor", "appearance")
    data object SecondaryColor : InterfaceItem("secondaryColor", "appearance")
    data object GridDensity : InterfaceItem("gridDensity", "appearance")
    data object UiScale : InterfaceItem("uiScale", "appearance")
    data object BoxArt : InterfaceItem("boxArt", "appearance")
    data object HomeScreen : InterfaceItem("homeScreen", "appearance")

    // Default
    data object DefaultView : InterfaceItem("defaultView", "default")

    // Screen Safety
    data object ScreenDimmer : InterfaceItem("screenDimmer", "screenSafety")
    data object DimAfter : InterfaceItem("dimAfter", "screenSafety")
    data object DimLevel : InterfaceItem("dimLevel", "screenSafety")

    // Ambient LED
    data object AmbientLed : InterfaceItem(
        key = "ambientLed",
        section = "ambientLed",
        visibleWhen = { it.display.ambientLedAvailable }
    )
    data object AmbientLedBrightness : InterfaceItem(
        key = "ambientLedBrightness",
        section = "ambientLed",
        visibleWhen = { it.display.ambientLedAvailable && it.display.ambientLedEnabled }
    )
    data object AmbientLedAudioBrightness : InterfaceItem(
        key = "ambientLedAudioBrightness",
        section = "ambientLed",
        visibleWhen = { it.display.ambientLedAvailable && it.display.ambientLedEnabled }
    )
    data object AmbientLedAudioColors : InterfaceItem(
        key = "ambientLedAudioColors",
        section = "ambientLed",
        visibleWhen = { it.display.ambientLedAvailable && it.display.ambientLedEnabled }
    )
    data object AmbientLedColorMode : InterfaceItem(
        key = "ambientLedColorMode",
        section = "ambientLed",
        visibleWhen = { it.display.ambientLedAvailable && it.display.ambientLedEnabled }
    )

    // Background Music
    data object BgmToggle : InterfaceItem("bgmToggle", "bgm")
    data object BgmVolume : InterfaceItem("bgmVolume", "bgm", { it.bgmEnabled })
    data object BgmFile : InterfaceItem("bgmFile", "bgm", { it.bgmEnabled })

    // UI Sounds
    data object UiSoundsToggle : InterfaceItem("uiSoundsToggle", "uiSounds")
    data object UiSoundsVolume : InterfaceItem("uiVolume", "uiSounds", { it.uiSoundsEnabled })

    // Sound Customization
    class SoundTypeItem(val soundType: SoundType) : InterfaceItem(
        key = "soundType_${soundType.name}",
        section = "customize",
        visibleWhen = { it.uiSoundsEnabled }
    )

    companion object {
        private val AppearanceHeader = Header("appearanceHeader", "appearance", "Appearance")
        private val DefaultHeader = Header("defaultHeader", "default", "Default")
        private val ScreenSafetyHeader = Header("screenSafetyHeader", "screenSafety", "Screen Safety")
        private val AmbientLedHeader = Header(
            key = "ambientLedHeader",
            section = "ambientLed",
            title = "Ambient Lighting",
            visibleWhen = { it.display.ambientLedAvailable }
        )
        private val BgmSpacer = SectionSpacer("bgmSpacer", "bgm")
        private val BgmHeader = Header("bgmHeader", "bgm", "Background Music")
        private val UiSoundsSpacer = SectionSpacer("uiSoundsSpacer", "uiSounds")
        private val UiSoundsHeader = Header("uiSoundsHeader", "uiSounds", "UI Sounds")
        private val CustomizeSpacer = SectionSpacer(
            key = "customizeSpacer",
            section = "customize",
            visibleWhen = { it.uiSoundsEnabled }
        )
        private val CustomizeHeader = Header(
            key = "customizeHeader",
            section = "customize",
            title = "Customize Sounds",
            visibleWhen = { it.uiSoundsEnabled }
        )

        val ALL: List<InterfaceItem> = listOf(
            AppearanceHeader,
            Theme, AccentColor, SecondaryColor, GridDensity, UiScale, BoxArt, HomeScreen,
            DefaultHeader,
            DefaultView,
            ScreenSafetyHeader,
            ScreenDimmer, DimAfter, DimLevel,
            AmbientLedHeader,
            AmbientLed, AmbientLedBrightness, AmbientLedAudioBrightness, AmbientLedAudioColors, AmbientLedColorMode,
            BgmSpacer, BgmHeader,
            BgmToggle, BgmVolume, BgmFile,
            UiSoundsSpacer, UiSoundsHeader,
            UiSoundsToggle, UiSoundsVolume,
            CustomizeSpacer, CustomizeHeader
        ) + SoundType.entries.map { SoundTypeItem(it) }
    }
}

private val interfaceLayout = SettingsLayout<InterfaceItem, InterfaceLayoutState>(
    allItems = InterfaceItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun interfaceMaxFocusIndex(state: InterfaceLayoutState): Int = interfaceLayout.maxFocusIndex(state)

internal fun interfaceItemAtFocusIndex(index: Int, state: InterfaceLayoutState): InterfaceItem? =
    interfaceLayout.itemAtFocusIndex(index, state)

internal fun interfaceSections(state: InterfaceLayoutState) = interfaceLayout.buildSections(state)

internal fun interfaceFocusIndexOf(item: InterfaceItem, state: InterfaceLayoutState): Int =
    interfaceLayout.focusIndexOf(item, state)

@Composable
fun InterfaceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val display = uiState.display
    val storage = uiState.storage
    val bgmEnabled = uiState.ambientAudio.enabled
    val uiSoundsEnabled = uiState.sounds.enabled

    val layoutState = remember(
        display.ambientLedAvailable,
        display.ambientLedEnabled,
        bgmEnabled,
        uiSoundsEnabled
    ) {
        InterfaceLayoutState(display, bgmEnabled, uiSoundsEnabled)
    }

    val currentHue = display.primaryColor?.let { colorIntToHue(it) }
    val secondaryHue = display.secondaryColor?.let { colorIntToHue(it) }

    val visibleItems = remember(
        display.ambientLedAvailable,
        display.ambientLedEnabled,
        bgmEnabled,
        uiSoundsEnabled
    ) {
        interfaceLayout.visibleItems(layoutState)
    }
    val sections = remember(
        display.ambientLedAvailable,
        display.ambientLedEnabled,
        bgmEnabled,
        uiSoundsEnabled
    ) {
        interfaceLayout.buildSections(layoutState)
    }

    fun isFocused(item: InterfaceItem): Boolean =
        uiState.focusedIndex == interfaceLayout.focusIndexOf(item, layoutState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { interfaceLayout.focusToListIndex(it, layoutState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is InterfaceItem.Header -> InterfaceSectionHeader(item.title)
                is InterfaceItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                InterfaceItem.Theme -> CyclePreference(
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

                InterfaceItem.AccentColor -> HueSliderPreference(
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

                InterfaceItem.SecondaryColor -> HueSliderPreference(
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

                InterfaceItem.GridDensity -> CyclePreference(
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

                InterfaceItem.UiScale -> SliderPreference(
                    title = "UI Scale",
                    value = display.uiScale,
                    minValue = 75,
                    maxValue = 150,
                    isFocused = isFocused(item),
                    step = 5,
                    suffix = "%",
                    onClick = { viewModel.adjustUiScale(5) }
                )

                InterfaceItem.BoxArt -> NavigationPreference(
                    icon = Icons.Outlined.Image,
                    title = "Box Art",
                    subtitle = "Customize card appearance",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToBoxArt() }
                )

                InterfaceItem.HomeScreen -> NavigationPreference(
                    icon = Icons.Outlined.Home,
                    title = "Home Screen",
                    subtitle = "Background and footer settings",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToHomeScreen() }
                )

                InterfaceItem.DefaultView -> CyclePreference(
                    title = "Default View",
                    value = when (display.defaultView) {
                        DefaultView.HOME -> "Home"
                        DefaultView.LIBRARY -> "Library"
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleDefaultView() }
                )

                InterfaceItem.ScreenDimmer -> SwitchPreference(
                    title = "Screen Dimmer",
                    subtitle = "Dims screen after inactivity to prevent burn-in",
                    isEnabled = storage.screenDimmerEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleScreenDimmer() }
                )

                InterfaceItem.DimAfter -> CyclePreference(
                    title = "Dim After",
                    value = "${storage.screenDimmerTimeoutMinutes} min",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleScreenDimmerTimeout() }
                )

                InterfaceItem.DimLevel -> SliderPreference(
                    title = "Dim Level",
                    value = storage.screenDimmerLevel,
                    minValue = 40,
                    maxValue = 70,
                    isFocused = isFocused(item),
                    step = 10,
                    onClick = { viewModel.cycleScreenDimmerLevel() }
                )

                InterfaceItem.AmbientLed -> {
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

                InterfaceItem.AmbientLedBrightness -> TrackSliderPreference(
                    title = "Brightness",
                    value = display.ambientLedBrightness / 100f,
                    steps = 19,
                    isFocused = isFocused(item),
                    onValueChange = { viewModel.setAmbientLedBrightness((it * 100).toInt()) }
                )

                InterfaceItem.AmbientLedAudioBrightness -> SwitchPreference(
                    title = "Audio Brightness",
                    subtitle = "LEDs pulse with music",
                    isEnabled = display.ambientLedAudioBrightness,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioBrightness(!display.ambientLedAudioBrightness) }
                )

                InterfaceItem.AmbientLedAudioColors -> SwitchPreference(
                    title = "Audio Colors",
                    subtitle = "Intensity shifts between color bands",
                    isEnabled = display.ambientLedAudioColors,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioColors(!display.ambientLedAudioColors) }
                )

                InterfaceItem.AmbientLedColorMode -> CyclePreference(
                    title = "Color Selection",
                    value = display.ambientLedColorMode.displayName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleAmbientLedColorMode() }
                )

                InterfaceItem.BgmToggle -> SwitchPreference(
                    title = "Background Music",
                    subtitle = "Play music while using the launcher",
                    isEnabled = uiState.ambientAudio.enabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientAudioEnabled(it) }
                )

                InterfaceItem.BgmVolume -> {
                    val volumeLevels = listOf(2, 5, 10, 20, 35)
                    val currentIndex = volumeLevels.indexOfFirst { it >= uiState.ambientAudio.volume }.takeIf { it >= 0 } ?: 0
                    val sliderValue = currentIndex + 1
                    SliderPreference(
                        title = "Volume",
                        value = sliderValue,
                        minValue = 1,
                        maxValue = 5,
                        isFocused = isFocused(item),
                        onClick = {
                            val nextIndex = (currentIndex + 1).mod(volumeLevels.size)
                            viewModel.setAmbientAudioVolume(volumeLevels[nextIndex])
                        }
                    )
                }

                InterfaceItem.BgmFile -> BackgroundMusicFileItem(
                    fileName = uiState.ambientAudio.audioFileName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openAudioFileBrowser() }
                )

                InterfaceItem.UiSoundsToggle -> SwitchPreference(
                    title = "UI Sounds",
                    subtitle = "Play tones on navigation and selection",
                    isEnabled = uiState.sounds.enabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSoundEnabled(it) }
                )

                InterfaceItem.UiSoundsVolume -> {
                    val volumeLevels = listOf(50, 70, 85, 95, 100)
                    val currentIndex = volumeLevels.indexOfFirst { it >= uiState.sounds.volume }.takeIf { it >= 0 } ?: 0
                    val sliderValue = currentIndex + 1
                    SliderPreference(
                        title = "Volume",
                        value = sliderValue,
                        minValue = 1,
                        maxValue = 5,
                        isFocused = isFocused(item),
                        onClick = {
                            val nextIndex = (currentIndex + 1).mod(volumeLevels.size)
                            viewModel.setSoundVolume(volumeLevels[nextIndex])
                        }
                    )
                }

                is InterfaceItem.SoundTypeItem -> SoundCustomizationItem(
                    soundType = item.soundType,
                    displayValue = uiState.sounds.getDisplayNameForType(item.soundType),
                    isFocused = isFocused(item),
                    onClick = { viewModel.showSoundPicker(item.soundType) }
                )
            }
        }
    }
}

@Composable
private fun InterfaceSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

@Composable
private fun SoundCustomizationItem(
    soundType: SoundType,
    displayValue: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val displayName = soundType.name
        .replace("_", " ")
        .lowercase()
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackgroundMusicFileItem(
    fileName: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Music File",
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = fileName ?: "None selected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
