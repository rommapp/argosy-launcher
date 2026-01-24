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
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun BuiltinVideoSection(
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
        item(key = "shader_header") {
            Text(
                text = "Shaders",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = Dimens.spacingSm,
                    top = Dimens.spacingMd,
                    bottom = Dimens.spacingXs
                )
            )
        }

        item(key = "shader") {
            CyclePreference(
                title = "Shader",
                value = uiState.builtinVideo.shader,
                isFocused = uiState.focusedIndex == 0,
                onClick = {
                    val options = listOf("None", "CRT", "LCD", "Sharp")
                    val currentIndex = options.indexOf(uiState.builtinVideo.shader).coerceAtLeast(0)
                    val nextIndex = (currentIndex + 1) % options.size
                    viewModel.setBuiltinShader(options[nextIndex])
                }
            )
        }

        item(key = "scaling_header") {
            Text(
                text = "Scaling",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = Dimens.spacingSm,
                    top = Dimens.spacingLg,
                    bottom = Dimens.spacingXs
                )
            )
        }

        item(key = "aspect_ratio") {
            CyclePreference(
                title = "Aspect Ratio",
                value = uiState.builtinVideo.aspectRatio,
                isFocused = uiState.focusedIndex == 1,
                onClick = {
                    val options = listOf("Auto", "4:3", "16:9", "Native")
                    val currentIndex = options.indexOf(uiState.builtinVideo.aspectRatio).coerceAtLeast(0)
                    val nextIndex = (currentIndex + 1) % options.size
                    viewModel.setBuiltinAspectRatio(options[nextIndex])
                }
            )
        }

        item(key = "integer_scaling") {
            SwitchPreference(
                title = "Integer Scaling",
                subtitle = "Scale only by whole numbers for pixel-perfect display",
                isEnabled = uiState.builtinVideo.integerScaling,
                isFocused = uiState.focusedIndex == 2,
                onToggle = { viewModel.setBuiltinIntegerScaling(it) }
            )
        }
    }
}
