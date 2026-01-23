package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

private data class SoundsLayoutState(
    val bgmEnabled: Boolean,
    val uiSoundsEnabled: Boolean
)

private sealed class SoundsItem(
    val key: String,
    val section: String,
    val visibleWhen: (SoundsLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String, visibleWhen: (SoundsLayoutState) -> Boolean = { true })
        : SoundsItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (SoundsLayoutState) -> Boolean = { true })
        : SoundsItem(key, section, visibleWhen)

    data object BgmToggle : SoundsItem("bgmToggle", "bgm")
    data object BgmVolume : SoundsItem("bgmVolume", "bgm", { it.bgmEnabled })
    data object BgmFile : SoundsItem("bgmFile", "bgm", { it.bgmEnabled })

    data object UiSoundsToggle : SoundsItem("uiSoundsToggle", "uiSounds")
    data object UiSoundsVolume : SoundsItem("uiVolume", "uiSounds", { it.uiSoundsEnabled })

    class SoundTypeItem(val soundType: SoundType) : SoundsItem(
        key = "soundType_${soundType.name}",
        section = "customize",
        visibleWhen = { it.uiSoundsEnabled }
    )

    companion object {
        private val UiSoundsHeader = Header("uiSoundsHeader", "uiSounds", "UI SOUNDS")
        private val UiSoundsSpacer = SectionSpacer("uiSoundsSpacer", "uiSounds")
        private val CustomizeHeader = Header(
            key = "customizeHeader",
            section = "customize",
            title = "CUSTOMIZE SOUNDS",
            visibleWhen = { it.uiSoundsEnabled }
        )
        private val CustomizeSpacer = SectionSpacer(
            key = "customizeSpacer",
            section = "customize",
            visibleWhen = { it.uiSoundsEnabled }
        )

        val ALL: List<SoundsItem> = listOf(
            BgmToggle, BgmVolume, BgmFile,
            UiSoundsSpacer, UiSoundsHeader, UiSoundsToggle, UiSoundsVolume,
            CustomizeSpacer, CustomizeHeader
        ) + SoundType.entries.map { SoundTypeItem(it) }
    }
}

private val soundsLayout = SettingsLayout<SoundsItem, SoundsLayoutState>(
    allItems = SoundsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun soundsMaxFocusIndex(bgmEnabled: Boolean, uiSoundsEnabled: Boolean): Int =
    soundsLayout.maxFocusIndex(SoundsLayoutState(bgmEnabled, uiSoundsEnabled))

internal fun soundsSections(bgmEnabled: Boolean, uiSoundsEnabled: Boolean) =
    soundsLayout.buildSections(SoundsLayoutState(bgmEnabled, uiSoundsEnabled))

@Composable
fun SoundsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()

    val bgmEnabled = uiState.ambientAudio.enabled
    val uiSoundsEnabled = uiState.sounds.enabled
    val layoutState = remember(bgmEnabled, uiSoundsEnabled) {
        SoundsLayoutState(bgmEnabled, uiSoundsEnabled)
    }

    val visibleItems = remember(bgmEnabled, uiSoundsEnabled) {
        soundsLayout.visibleItems(layoutState)
    }
    val sections = remember(bgmEnabled, uiSoundsEnabled) {
        soundsLayout.buildSections(layoutState)
    }

    fun isFocused(item: SoundsItem): Boolean =
        uiState.focusedIndex == soundsLayout.focusIndexOf(item, layoutState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { soundsLayout.focusToListIndex(it, layoutState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is SoundsItem.Header -> {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                    )
                }

                is SoundsItem.SectionSpacer -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                }

                SoundsItem.BgmToggle -> SwitchPreference(
                    title = "Background Music",
                    subtitle = "Play music while using the launcher",
                    isEnabled = uiState.ambientAudio.enabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAmbientAudioEnabled(it) }
                )

                SoundsItem.BgmVolume -> {
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

                SoundsItem.BgmFile -> BackgroundMusicFileItem(
                    fileName = uiState.ambientAudio.audioFileName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openAudioFileBrowser() }
                )

                SoundsItem.UiSoundsToggle -> SwitchPreference(
                    title = "UI Sounds",
                    subtitle = "Play tones on navigation and selection",
                    isEnabled = uiState.sounds.enabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSoundEnabled(it) }
                )

                SoundsItem.UiSoundsVolume -> {
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

                is SoundsItem.SoundTypeItem -> SoundCustomizationItem(
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
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
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
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
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
