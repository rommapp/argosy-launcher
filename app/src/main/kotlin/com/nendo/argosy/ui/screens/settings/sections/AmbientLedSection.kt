package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.TrackSliderPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class AmbientLedItem(
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
    ) : AmbientLedItem(key, section, visibleWhen)

    // General
    data object Enable : AmbientLedItem("enable", "general")
    data object Brightness : AmbientLedItem("brightness", "general",
        visibleWhen = { it.ambientLedEnabled })
    data object CustomColor : AmbientLedItem("customColor", "general",
        visibleWhen = { it.ambientLedEnabled })
    data object CustomColorHue : AmbientLedItem("customColorHue", "general",
        visibleWhen = { it.ambientLedEnabled && it.ambientLedCustomColor })

    // Cover Art
    data object CoverArtColors : AmbientLedItem("coverArtColors", "coverArt",
        visibleWhen = { it.ambientLedEnabled })
    data object TransitionSpeed : AmbientLedItem("transitionSpeed", "coverArt",
        visibleWhen = { it.ambientLedEnabled && it.ambientLedCoverArtEnabled })

    // Reactive Audio
    data object AudioBrightness : AmbientLedItem("audioBrightness", "reactiveAudio",
        visibleWhen = { it.ambientLedEnabled })
    data object AudioColors : AmbientLedItem("audioColors", "reactiveAudio",
        visibleWhen = { it.ambientLedEnabled })

    // Reactive Screen
    data object ScreenColors : AmbientLedItem("screenColors", "reactiveScreen",
        visibleWhen = { it.ambientLedEnabled })
    data object ScreenColorMode : AmbientLedItem("screenColorMode", "reactiveScreen",
        visibleWhen = { it.ambientLedEnabled && it.ambientLedScreenEnabled })

    companion object {
        private val GeneralHeader = Header("generalHeader", "general", "General")
        private val CoverArtHeader = Header("coverArtHeader", "coverArt", "Cover Art",
            visibleWhen = { it.ambientLedEnabled })
        private val ReactiveAudioHeader = Header("reactiveAudioHeader", "reactiveAudio", "Reactive Audio",
            visibleWhen = { it.ambientLedEnabled })
        private val ReactiveScreenHeader = Header("reactiveScreenHeader", "reactiveScreen", "Reactive Screen",
            visibleWhen = { it.ambientLedEnabled })

        val ALL: List<AmbientLedItem> = listOf(
            GeneralHeader,
            Enable, Brightness, CustomColor, CustomColorHue,
            CoverArtHeader,
            CoverArtColors, TransitionSpeed,
            ReactiveAudioHeader,
            AudioBrightness, AudioColors,
            ReactiveScreenHeader,
            ScreenColors, ScreenColorMode
        )
    }
}

private val ambientLedLayout = SettingsLayout<AmbientLedItem, DisplayState>(
    allItems = AmbientLedItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun ambientLedMaxFocusIndex(display: DisplayState): Int =
    ambientLedLayout.maxFocusIndex(display)

internal fun ambientLedItemAtFocusIndex(index: Int, display: DisplayState): AmbientLedItem? =
    ambientLedLayout.itemAtFocusIndex(index, display)

internal fun ambientLedSections(display: DisplayState) = ambientLedLayout.buildSections(display)

private val transitionLabels = mapOf(
    0 to "Instant",
    100 to "100ms",
    250 to "250ms",
    500 to "500ms",
    1000 to "1s"
)

@Composable
fun AmbientLedSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val display = uiState.display

    val visibleItems = remember(
        display.ambientLedEnabled,
        display.ambientLedCustomColor,
        display.ambientLedCoverArtEnabled,
        display.ambientLedScreenEnabled
    ) {
        ambientLedLayout.visibleItems(display)
    }
    val sections = remember(
        display.ambientLedEnabled,
        display.ambientLedCustomColor,
        display.ambientLedCoverArtEnabled,
        display.ambientLedScreenEnabled
    ) {
        ambientLedLayout.buildSections(display)
    }

    fun isFocused(item: AmbientLedItem): Boolean =
        uiState.focusedIndex == ambientLedLayout.focusIndexOf(item, display)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { ambientLedLayout.focusToListIndex(it, display) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is AmbientLedItem.Header -> AmbientLedSectionHeader(item.title)

                AmbientLedItem.Enable -> SwitchPreference(
                    title = "Enable LEDs",
                    subtitle = "Master toggle for thumbstick lighting",
                    isEnabled = display.ambientLedEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedEnabled(!display.ambientLedEnabled) }
                )

                AmbientLedItem.Brightness -> TrackSliderPreference(
                    title = "Brightness",
                    value = display.ambientLedBrightness / 100f,
                    steps = 19,
                    isFocused = isFocused(item),
                    onValueChange = { viewModel.setAmbientLedBrightness((it * 100).toInt()) }
                )

                AmbientLedItem.CustomColor -> SwitchPreference(
                    title = "Custom Default Color",
                    subtitle = if (display.ambientLedCustomColor) "Using custom hue" else "Using accent color",
                    isEnabled = display.ambientLedCustomColor,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedCustomColor(!display.ambientLedCustomColor) }
                )

                AmbientLedItem.CustomColorHue -> HueSliderPreference(
                    title = "Default Color Hue",
                    currentHue = display.ambientLedCustomColorHue.toFloat(),
                    isFocused = isFocused(item),
                    onHueChange = { hue ->
                        viewModel.setAmbientLedCustomColorHue(hue?.toInt() ?: 200)
                    }
                )

                AmbientLedItem.CoverArtColors -> SwitchPreference(
                    title = "Cover Art Colors",
                    subtitle = "LEDs match game box art gradient",
                    isEnabled = display.ambientLedCoverArtEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedCoverArtEnabled(!display.ambientLedCoverArtEnabled) }
                )

                AmbientLedItem.TransitionSpeed -> CyclePreference(
                    title = "Transition Speed",
                    value = transitionLabels[display.ambientLedTransitionMs] ?: "${display.ambientLedTransitionMs}ms",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleAmbientLedTransitionMsWrap() }
                )

                AmbientLedItem.AudioBrightness -> SwitchPreference(
                    title = "Audio Brightness",
                    subtitle = "LEDs pulse with music",
                    isEnabled = display.ambientLedAudioBrightness,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioBrightness(!display.ambientLedAudioBrightness) }
                )

                AmbientLedItem.AudioColors -> SwitchPreference(
                    title = "Audio Colors",
                    subtitle = "Intensity shifts between color bands",
                    isEnabled = display.ambientLedAudioColors,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioColors(!display.ambientLedAudioColors) }
                )

                AmbientLedItem.ScreenColors -> {
                    val subtitle = if (!display.hasScreenCapturePermission)
                        "Requires screen capture permission"
                    else "LEDs match game screen content"
                    SwitchPreference(
                        title = "Screen Colors",
                        subtitle = subtitle,
                        isEnabled = display.ambientLedScreenEnabled,
                        isFocused = isFocused(item),
                        onToggle = {
                            if (!display.ambientLedScreenEnabled && !display.hasScreenCapturePermission) {
                                viewModel.requestScreenCapturePermission()
                            }
                            viewModel.setAmbientLedScreenEnabled(!display.ambientLedScreenEnabled)
                        }
                    )
                }

                AmbientLedItem.ScreenColorMode -> CyclePreference(
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
private fun AmbientLedSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
