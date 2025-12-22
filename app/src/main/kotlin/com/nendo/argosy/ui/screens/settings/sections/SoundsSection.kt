package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun SoundsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val soundTypes = SoundType.entries.toList()

    // Background Music is first (indices 0, 1, 2 if enabled)
    val bgmItemCount = if (uiState.ambientAudio.enabled) 3 else 1
    val uiSoundsToggleIndex = bgmItemCount
    val uiSoundsItemCount = if (uiState.sounds.enabled) 2 + soundTypes.size else 1
    val maxIndex = bgmItemCount + uiSoundsItemCount - 1

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
        // Background Music section (first)
        item {
            SwitchPreference(
                title = "Background Music",
                subtitle = "Play music while using the launcher",
                isEnabled = uiState.ambientAudio.enabled,
                isFocused = uiState.focusedIndex == 0,
                onToggle = { viewModel.setAmbientAudioEnabled(it) }
            )
        }
        if (uiState.ambientAudio.enabled) {
            item {
                val volumeLevels = listOf(10, 25, 40, 60, 80)
                val currentIndex = volumeLevels.indexOfFirst { it >= uiState.ambientAudio.volume }.takeIf { it >= 0 } ?: 0
                val sliderValue = currentIndex + 1
                SliderPreference(
                    title = "Volume",
                    value = sliderValue,
                    minValue = 1,
                    maxValue = 5,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = {
                        val nextIndex = (currentIndex + 1) % volumeLevels.size
                        viewModel.setAmbientAudioVolume(volumeLevels[nextIndex])
                    }
                )
            }
            item {
                BackgroundMusicFileItem(
                    fileName = uiState.ambientAudio.audioFileName,
                    isFocused = uiState.focusedIndex == 2,
                    onClick = { viewModel.openAudioFilePicker() }
                )
            }
        }

        // UI Sounds section (after background music)
        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "UI SOUNDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
        }
        item {
            SwitchPreference(
                title = "UI Sounds",
                subtitle = "Play tones on navigation and selection",
                isEnabled = uiState.sounds.enabled,
                isFocused = uiState.focusedIndex == uiSoundsToggleIndex,
                onToggle = { viewModel.setSoundEnabled(it) }
            )
        }
        if (uiState.sounds.enabled) {
            item {
                val volumeLevels = listOf(10, 25, 40, 60, 80)
                val currentIndex = volumeLevels.indexOfFirst { it >= uiState.sounds.volume }.takeIf { it >= 0 } ?: 0
                val sliderValue = currentIndex + 1
                SliderPreference(
                    title = "Volume",
                    value = sliderValue,
                    minValue = 1,
                    maxValue = 5,
                    isFocused = uiState.focusedIndex == uiSoundsToggleIndex + 1,
                    onClick = {
                        val nextIndex = (currentIndex + 1) % volumeLevels.size
                        viewModel.setSoundVolume(volumeLevels[nextIndex])
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = "CUSTOMIZE SOUNDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                )
            }
            itemsIndexed(soundTypes) { index, soundType ->
                val focusIndex = uiSoundsToggleIndex + 2 + index
                SoundCustomizationItem(
                    soundType = soundType,
                    displayValue = uiState.sounds.getDisplayNameForType(soundType),
                    isFocused = uiState.focusedIndex == focusIndex,
                    onClick = { viewModel.showSoundPicker(soundType) }
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
