package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
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
        val titleRes: Int,
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
    data object AchievementFlash : AmbientLedItem("achievementFlash", "general",
        visibleWhen = { it.ambientLedEnabled })

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
        private val GeneralHeader =
            Header("generalHeader", "general", R.string.settings_led_section_general)
        private val CoverArtHeader = Header("coverArtHeader", "coverArt", R.string.settings_led_section_cover_art,
            visibleWhen = { it.ambientLedEnabled })
        private val ReactiveAudioHeader = Header("reactiveAudioHeader", "reactiveAudio", R.string.settings_led_section_reactive_audio,
            visibleWhen = { it.ambientLedEnabled })
        private val ReactiveScreenHeader = Header("reactiveScreenHeader", "reactiveScreen", R.string.settings_led_section_reactive_screen,
            visibleWhen = { it.ambientLedEnabled })

        val ALL: List<AmbientLedItem>
            get() = listOf(
                GeneralHeader,
                Enable, Brightness, CustomColor, CustomColorHue, AchievementFlash,
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
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "general" -> R.string.settings_led_section_general
            "coverArt" -> R.string.settings_led_section_cover_art
            "reactiveAudio" -> R.string.settings_led_section_reactive_audio
            "reactiveScreen" -> R.string.settings_led_section_reactive_screen
            else -> null
        }
    }
)

private fun ambientLedColorModeLabelRes(mode: AmbientLedColorMode): Int = when (mode) {
    AmbientLedColorMode.DOMINANT_3 -> R.string.settings_led_color_mode_dominant
    AmbientLedColorMode.VIBRANT_MUTED -> R.string.settings_led_color_mode_vibrant_muted
    AmbientLedColorMode.HUE_FAMILIES -> R.string.settings_led_color_mode_hue_families
}

internal fun ambientLedMaxFocusIndex(display: DisplayState): Int =
    ambientLedLayout.maxFocusIndex(display)

internal fun ambientLedItemAtFocusIndex(index: Int, display: DisplayState): AmbientLedItem? =
    ambientLedLayout.itemAtFocusIndex(index, display)

internal fun ambientLedSections(display: DisplayState) = ambientLedLayout.buildSections(display)

private val transitionLabels = mapOf(
    0 to R.string.settings_led_transition_instant,
    100 to R.string.settings_led_transition_100ms,
    250 to R.string.settings_led_transition_250ms,
    500 to R.string.settings_led_transition_500ms,
    1000 to R.string.settings_led_transition_1s
)

@Composable
fun AmbientLedSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val display = uiState.display
    val context = LocalContext.current

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
        display.ambientLedScreenEnabled,
        context
    ) {
        ambientLedLayout.buildSections(display, context)
    }

    fun isFocused(item: AmbientLedItem): Boolean =
        uiState.focusedIndex == ambientLedLayout.focusIndexOf(item, display)

    fun pickerToken(item: AmbientLedItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { ambientLedLayout.focusToListIndex(it, display) },
        itemKey = { it.key },
        isNavItem = { false },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is AmbientLedItem.Header -> AmbientLedSectionHeader(stringResource(item.titleRes))

                AmbientLedItem.Enable -> SwitchPreference(
                    title = stringResource(R.string.settings_led_enable_title),
                    subtitle = stringResource(R.string.settings_led_enable_subtitle),
                    isEnabled = display.ambientLedEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedEnabled(!display.ambientLedEnabled) }
                )

                AmbientLedItem.Brightness -> TrackSliderPreference(
                    title = stringResource(R.string.settings_led_brightness_title),
                    value = display.ambientLedBrightness / 100f,
                    steps = 19,
                    isFocused = isFocused(item),
                    onValueChange = { viewModel.setAmbientLedBrightness((it * 100).toInt()) }
                )

                AmbientLedItem.CustomColor -> SwitchPreference(
                    title = stringResource(R.string.settings_led_custom_color_title),
                    subtitle = if (display.ambientLedCustomColor) {
                        stringResource(R.string.settings_led_custom_color_subtitle_on)
                    } else {
                        stringResource(R.string.settings_led_custom_color_subtitle_off)
                    },
                    isEnabled = display.ambientLedCustomColor,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedCustomColor(!display.ambientLedCustomColor) }
                )

                AmbientLedItem.CustomColorHue -> HueSliderPreference(
                    title = stringResource(R.string.settings_led_custom_color_hue_title),
                    currentHue = display.ambientLedCustomColorHue.toFloat(),
                    isFocused = isFocused(item),
                    onHueChange = { hue ->
                        viewModel.setAmbientLedCustomColorHue(hue?.toInt() ?: 200)
                    }
                )

                AmbientLedItem.AchievementFlash -> SwitchPreference(
                    title = stringResource(R.string.settings_led_achievement_flash_title),
                    subtitle = stringResource(R.string.settings_led_achievement_flash_subtitle),
                    isEnabled = display.ambientLedAchievementFlash,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAchievementFlash(!display.ambientLedAchievementFlash) }
                )

                AmbientLedItem.CoverArtColors -> SwitchPreference(
                    title = stringResource(R.string.settings_led_cover_art_title),
                    subtitle = stringResource(R.string.settings_led_cover_art_subtitle),
                    isEnabled = display.ambientLedCoverArtEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedCoverArtEnabled(!display.ambientLedCoverArtEnabled) }
                )

                AmbientLedItem.TransitionSpeed -> {
                    val steps = remember { transitionLabels.keys.toList() }
                    val currentStep = steps.indexOf(display.ambientLedTransitionMs).coerceAtLeast(0)
                    CyclePreference(
                        title = stringResource(R.string.settings_led_transition_title),
                        value = transitionLabels[display.ambientLedTransitionMs]
                            ?.let { stringResource(it) }
                            ?: stringResource(
                                R.string.settings_led_transition_milliseconds,
                                display.ambientLedTransitionMs
                            ),
                        isFocused = isFocused(item),
                        onClick = { viewModel.setAmbientLedTransitionMs(steps[(currentStep + 1).mod(steps.size)]) },
                        onPrev = { viewModel.setAmbientLedTransitionMs(steps[(currentStep - 1).mod(steps.size)]) },
                        options = remember(context) {
                            transitionLabels.values.map { context.getString(it) }
                        },
                        onSelect = { viewModel.setAmbientLedTransitionMs(steps[it]) },
                        pickerRequestToken = pickerToken(item)
                    )
                }

                AmbientLedItem.AudioBrightness -> SwitchPreference(
                    title = stringResource(R.string.settings_led_audio_brightness_title),
                    subtitle = stringResource(R.string.settings_led_audio_brightness_subtitle),
                    isEnabled = display.ambientLedAudioBrightness,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioBrightness(!display.ambientLedAudioBrightness) }
                )

                AmbientLedItem.AudioColors -> SwitchPreference(
                    title = stringResource(R.string.settings_led_audio_colors_title),
                    subtitle = stringResource(R.string.settings_led_audio_colors_subtitle),
                    isEnabled = display.ambientLedAudioColors,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientLedAudioColors(!display.ambientLedAudioColors) }
                )

                AmbientLedItem.ScreenColors -> {
                    val subtitle = if (!display.hasScreenCapturePermission) {
                        stringResource(R.string.settings_led_screen_colors_subtitle_locked)
                    } else {
                        stringResource(R.string.settings_led_screen_colors_subtitle)
                    }
                    SwitchPreference(
                        title = stringResource(R.string.settings_led_screen_colors_title),
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
                    title = stringResource(R.string.settings_led_color_mode_title),
                    value = stringResource(ambientLedColorModeLabelRes(display.ambientLedColorMode)),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleAmbientLedColorMode() },
                    onPrev = { viewModel.cycleAmbientLedColorMode(-1) },
                    options = remember(context) {
                        AmbientLedColorMode.entries.map { context.getString(ambientLedColorModeLabelRes(it)) }
                    },
                    onSelect = { viewModel.cycleAmbientLedColorMode(it - display.ambientLedColorMode.ordinal) },
                    pickerRequestToken = pickerToken(item)
                )
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
