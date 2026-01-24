package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun BuiltinAudioSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item(key = "audio_header") {
            Text(
                text = "Audio Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = Dimens.spacingSm,
                    top = Dimens.spacingMd,
                    bottom = Dimens.spacingXs
                )
            )
        }

        item(key = "latency") {
            SliderPreference(
                title = "Audio Latency",
                value = uiState.builtinAudio.latency,
                minValue = 1,
                maxValue = 5,
                isFocused = uiState.focusedIndex == 0
            )
        }

        item(key = "sync_mode") {
            CyclePreference(
                title = "Sync Mode",
                value = uiState.builtinAudio.syncMode,
                isFocused = uiState.focusedIndex == 1,
                onClick = {
                    val options = listOf("Auto", "VSync", "Audio")
                    val currentIndex = options.indexOf(uiState.builtinAudio.syncMode).coerceAtLeast(0)
                    val nextIndex = (currentIndex + 1) % options.size
                    viewModel.setBuiltinAudioSyncMode(options[nextIndex])
                }
            )
        }
    }
}
